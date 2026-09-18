package com.deliverytracking.integration;

import com.deliverytracking.dto.LoginRequest;
import com.deliverytracking.dto.RegisterRequest;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void register_validCustomer_returns201WithBearerToken() throws Exception {
        String email = uniqueEmail("customer");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .name("Alice")
                                .email(email)
                                .password(PASSWORD)
                                .role(Role.CUSTOMER)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.expiresIn").isNumber());

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail("customer");
        register(email, Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .name("Alice Clone")
                                .email(email)
                                .password(PASSWORD)
                                .role(Role.CUSTOMER)
                                .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email already registered: " + email));
    }

    @Test
    void register_concurrentSameEmail_neverReturns500() throws Exception {
        String email = uniqueEmail("race");
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            CountDownLatch ready = new CountDownLatch(attempts);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return -1;
                    }
                    return mockMvc.perform(post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                            .name("Racer")
                                            .email(email)
                                            .password(PASSWORD)
                                            .role(Role.CUSTOMER)
                                            .build())))
                            .andReturn().getResponse().getStatus();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409);
            assertThat(statuses).doesNotContain(500);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateEmail_insertAtRepositoryLevel_hitsEmailUniqueConstraint() throws Exception {
        String email = uniqueEmail("constraint");
        register(email, Role.CUSTOMER);

        User duplicate = User.builder()
                .name("Clone")
                .email(email)
                .password("{bcrypt}x")
                .role(Role.CUSTOMER)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .satisfies(throwable -> {
                    Throwable cause = throwable;
                    boolean emailConstraint = false;
                    while (cause != null) {
                        if (cause instanceof ConstraintViolationException cve
                                && cve.getConstraintName() != null
                                && cve.getConstraintName().contains("idx_users_email")) {
                            emailConstraint = true;
                            break;
                        }
                        cause = cause.getCause();
                    }
                    assertThat(emailConstraint).as("violation should be the email unique constraint").isTrue();
                });
    }

    @Test
    void register_adminRole_isBlocked() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .name("Rogue Admin")
                                .email(uniqueEmail("admin"))
                                .password(PASSWORD)
                                .role(Role.ADMIN)
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Self-registration as ADMIN is not allowed"));
    }

    @Test
    void register_invalidEmail_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .name("Alice")
                                .email("not-an-email")
                                .password(PASSWORD)
                                .role(Role.CUSTOMER)
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_shortPassword_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .name("Alice")
                                .email(uniqueEmail("customer"))
                                .password("123")
                                .role(Role.CUSTOMER)
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_missingRole_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .name("Alice")
                                .email(uniqueEmail("customer"))
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        String email = uniqueEmail("customer");
        register(email, Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(email)
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail("customer");
        register(email, Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(email)
                                .password("wrong-password")
                                .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("nobody@test.com")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_missingPassword_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(uniqueEmail("customer"))
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
}
