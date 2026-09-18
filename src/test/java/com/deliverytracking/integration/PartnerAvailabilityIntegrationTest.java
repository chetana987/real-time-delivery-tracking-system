package com.deliverytracking.integration;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.Role;
import com.deliverytracking.repository.UserRepository;
import com.deliverytracking.service.PartnerGeoService;
import com.deliverytracking.service.PartnerPresenceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of delivery-partner availability over real WebSocket
 * connections. The web environment is RANDOM_PORT so real STOMP/SockJS sessions
 * are exercised against the running server instead of MockMvc's STOMP mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartnerAvailabilityIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PartnerPresenceRegistry presenceRegistry;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PartnerGeoService partnerGeoService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<Long> manualGeoPartners = new ArrayList<>();

    private String customerEmail;
    private String partnerEmail;
    private String partner2Email;
    private String partner3Email;
    private String customerToken;
    private String partnerToken;
    private String partner2Token;
    private String partner3Token;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;

    @BeforeEach
    void setUp() throws Exception {
        customerEmail = uniqueEmail("avail-customer");
        partnerEmail = uniqueEmail("avail-partner");
        partner2Email = uniqueEmail("avail-partner-2");
        partner3Email = uniqueEmail("avail-partner-3");
        customerToken = register(customerEmail, Role.CUSTOMER);
        partnerToken = register(partnerEmail, Role.DELIVERY_PARTNER);
        partner2Token = register(partner2Email, Role.DELIVERY_PARTNER);
        partner3Token = register(partner3Email, Role.DELIVERY_PARTNER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "Availability Kitchen");
        menuItemId = addMenuItem(adminToken, restaurantId, "Wrap", "8.50");
    }

    @AfterEach
    void tearDown() {
        for (StompSession session : sessions) {
            try {
                session.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
        sessions.clear();
        for (WebSocketStompClient client : clients) {
            try {
                client.stop();
            } catch (RuntimeException ignored) {
            }
        }
        clients.clear();
        for (Long partnerId : manualGeoPartners) {
            presenceRegistry.markOffline("sess-" + partnerId);
            partnerGeoService.removePartnerLocation(partnerId);
        }
        manualGeoPartners.clear();
    }

    private StompSession connect(String token) throws Exception {
        SockJsClient sockJs = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
        WebSocketStompClient client = new WebSocketStompClient(sockJs);
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + token);
        StompSession session = client.connectAsync("http://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
        clients.add(client);
        sessions.add(session);
        return session;
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private boolean await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private void expectAvailability(String token, String expected) throws Exception {
        mockMvc.perform(get("/api/partners/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value(expected));
    }

    private String placeOrderBody() throws Exception {
        return objectMapper.writeValueAsString(PlaceOrderRequest.builder()
                .restaurantId(restaurantId)
                .deliveryAddress("42 Home St")
                .deliveryLatitude(18.5204)
                .deliveryLongitude(73.8567)
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(menuItemId)
                        .quantity(1)
                        .build()))
                .build());
    }

    private Long placeOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void accept(Long orderId, String token) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void advanceStatus(Long orderId, String token, String status) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deliveryPartnerConnects_goesOnlineAndReportsAvailable() throws Exception {
        Long partnerId = userId(partnerEmail);
        expectAvailability(partnerToken, "OFFLINE");

        connect(partnerToken);
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "AVAILABLE");
    }

    @Test
    void deliveryPartnerDisconnects_goesOffline() throws Exception {
        Long partnerId = userId(partnerEmail);
        StompSession session = connect(partnerToken);
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "AVAILABLE");

        session.disconnect();
        assertTrue(await(() -> !presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "OFFLINE");
    }

    @Test
    void multipleSessions_surviveIndividualDisconnects() throws Exception {
        Long partnerId = userId(partnerEmail);
        StompSession sessionA = connect(partnerToken);
        StompSession sessionB = connect(partnerToken);
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "AVAILABLE");

        sessionB.disconnect();
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "AVAILABLE");

        sessionA.disconnect();
        assertTrue(await(() -> !presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "OFFLINE");
    }

    @Test
    void customerAndAdminConnections_doNotRegisterAsPartners() throws Exception {
        Long customerId = userId(customerEmail);
        Long adminId = userId(ADMIN_EMAIL);

        connect(customerToken);
        connect(adminToken);
        Thread.sleep(500);

        assertThat(presenceRegistry.hasAnySession(customerId)).isFalse();
        assertThat(presenceRegistry.hasAnySession(adminId)).isFalse();

        mockMvc.perform(get("/api/partners/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/partners/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptingAnOrder_transitionsAvailableToBusy() throws Exception {
        Long partnerId = userId(partnerEmail);
        Long orderId = placeOrder(customerToken);

        connect(partnerToken);
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "AVAILABLE");

        accept(orderId, partnerToken);
        expectAvailability(partnerToken, "BUSY");
    }

    @Test
    void deliveringAnOrder_transitionsBusyBackToAvailable() throws Exception {
        Long partnerId = userId(partnerEmail);
        Long orderId = placeOrder(customerToken);

        connect(partnerToken);
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        accept(orderId, partnerToken);
        expectAvailability(partnerToken, "BUSY");

        advanceStatus(orderId, partnerToken, "PICKED_UP");
        advanceStatus(orderId, partnerToken, "OUT_FOR_DELIVERY");
        advanceStatus(orderId, partnerToken, "DELIVERED");
        expectAvailability(partnerToken, "AVAILABLE");
    }

    @Test
    void reconnectingWhileDeliveryIsInFlight_reportsBusy() throws Exception {
        Long partnerId = userId(partnerEmail);
        Long orderId = placeOrder(customerToken);
        accept(orderId, partnerToken);

        connect(partnerToken);
        assertTrue(await(() -> presenceRegistry.isOnline(partnerId)));
        expectAvailability(partnerToken, "BUSY");
    }

    @Test
    void nearbySelection_returnsOnlyOnlineAvailablePartners() throws Exception {
        Assumptions.assumeTrue(redisAvailable(), "Redis unavailable; skipping geographic selection test");

        Long customerId = userId(customerEmail);
        Long availableId = userId(partnerEmail);
        Long busyId = userId(partner2Email);
        Long farId = userId(partner3Email);

        partnerGeoService.addPartnerLocation(availableId, 12.35, 56.79);
        partnerGeoService.addPartnerLocation(busyId, 12.36, 56.78);
        partnerGeoService.addPartnerLocation(farId, 13.5, 58.0);
        partnerGeoService.addPartnerLocation(customerId, 12.34, 56.79);
        manualGeoPartners.addAll(List.of(availableId, busyId, farId, customerId));

        presenceRegistry.markOnline(availableId, "sess-" + availableId);
        presenceRegistry.markOnline(busyId, "sess-" + busyId);
        presenceRegistry.markOnline(farId, "sess-" + farId);

        Long busyOrder = placeOrder(customerToken);
        accept(busyOrder, partner2Token);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nearbyPartners[*].deliveryPartnerId",
                        hasItem(availableId.intValue())))
                .andExpect(jsonPath("$.nearbyPartners[*].deliveryPartnerId",
                        not(hasItem(busyId.intValue()))))
                .andExpect(jsonPath("$.nearbyPartners[*].deliveryPartnerId",
                        not(hasItem(farId.intValue()))))
                .andExpect(jsonPath("$.nearbyPartners[*].deliveryPartnerId",
                        not(hasItem(customerId.intValue()))));
    }

    private boolean redisAvailable() {
        try {
            redisTemplate.opsForValue().get("delivery-tracking-test-probe");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}