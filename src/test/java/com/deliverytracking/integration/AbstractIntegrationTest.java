package com.deliverytracking.integration;

import com.deliverytracking.dto.LoginRequest;
import com.deliverytracking.dto.MenuItemRequest;
import com.deliverytracking.dto.RegisterRequest;
import com.deliverytracking.dto.RestaurantRequest;
import com.deliverytracking.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerClientFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for every @SpringBootTest integration test.
 *
 * <p>Database selection order:
 * <ol>
 *   <li>If Docker is available a real MySQL 8 container is started (Testcontainers).</li>
 *   <li>Otherwise the tests fall back to a local MySQL 8 instance. Connection
 *       settings can be overridden with the TEST_DB_URL / TEST_DB_USER /
 *       TEST_DB_PASSWORD environment variables.</li>
 *   <li>If neither Docker nor a reachable local MySQL exists the whole class is
 *       skipped (aborted), so {@code mvn test} still succeeds on machines
 *       without a database.</li>
 * </ol>
 *
 * <p>The schema is recreated for the tests via {@code ddl-auto: create-drop},
 * so every run starts from a clean database. The seeded {@code admin@delivery.com}
 * account is available for ADMIN-role flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    protected static final String PASSWORD = "password123";
    protected static final String ADMIN_EMAIL = "admin@delivery.com";
    protected static final String ADMIN_PASSWORD = "admin123";

    private static final String LOCAL_TEST_DB_URL =
            "jdbc:mysql://localhost:3306/delivery_tracking_test?createDatabaseIfNotExist=true"
            + "&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";

    static final MySQLContainer<?> MYSQL = startMySqlIfDockerAvailable();

    private static MySQLContainer<?> startMySqlIfDockerAvailable() {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0");
                container.start();
                log.info("Integration tests running against Testcontainers MySQL at {}", container.getJdbcUrl());
                return container;
            }
        } catch (Throwable t) {
            log.warn("Testcontainers/Docker unavailable, will fall back to local MySQL: {}", t.getMessage());
        }
        return null;
    }

    @BeforeAll
    static void requireDatabase() {
        if (MYSQL == null && !localMySqlReachable()) {
            throw new TestAbortedException("Integration tests skipped: no Docker (Testcontainers) and no reachable "
                    + "local MySQL at " + LOCAL_TEST_DB_URL
                    + ". Override TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD if your local MySQL differs.");
        }
    }

    private static boolean localMySqlReachable() {
        String url = System.getenv().getOrDefault("TEST_DB_URL", LOCAL_TEST_DB_URL);
        String user = System.getenv().getOrDefault("TEST_DB_USER", "root");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "root");
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            log.info("Integration tests running against local MySQL at {}", url);
            return true;
        } catch (Exception e) {
            log.warn("Local MySQL unreachable: {}", e.getMessage());
            return false;
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (MYSQL != null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        } else {
            registry.add("spring.datasource.url",
                    () -> System.getenv().getOrDefault("TEST_DB_URL", LOCAL_TEST_DB_URL));
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("TEST_DB_USER", "root"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "root"));
        }
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String register(String email, Role role) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("User " + email)
                .email(email)
                .password(PASSWORD)
                .role(role)
                .build();
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    protected String login(String email, String password) throws Exception {
        LoginRequest request = LoginRequest.builder().email(email).password(password).build();
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    protected String login(String email) throws Exception {
        return login(email, PASSWORD);
    }

    protected Long createRestaurant(String adminToken, String name) throws Exception {
        RestaurantRequest request = RestaurantRequest.builder()
                .name(name)
                .address("1 Test Street")
                .lat(12.34)
                .lng(56.78)
                .build();
        String body = mockMvc.perform(post("/api/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    protected Long addMenuItem(String adminToken, Long restaurantId, String name, String price) throws Exception {
        MenuItemRequest request = MenuItemRequest.builder()
                .name(name)
                .price(new BigDecimal(price))
                .build();
        String body = mockMvc.perform(post("/api/restaurants/" + restaurantId + "/menu")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.com";
    }
}
