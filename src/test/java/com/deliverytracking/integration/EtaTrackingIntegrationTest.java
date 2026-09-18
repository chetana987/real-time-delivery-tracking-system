package com.deliverytracking.integration;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.LocationUpdate;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.repository.LocationUpdateRepository;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the ETA exposed by {@code GET /api/orders/{id}/location}: the ETA is
 * derived from the real haversine distance (partner's latest GPS position to the
 * order's stored destination) using the configured average speed (30 km/h in the
 * test default), and changes when a newer GPS position arrives. Orders without a
 * stored destination expose neither distance nor ETA.
 */
class EtaTrackingIntegrationTest extends AbstractIntegrationTest {

    private static final double DEST_LAT = 18.5204;
    private static final double DEST_LNG = 73.8567;
    private static final int DEFAULT_SPEED_KMH = 30;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LocationUpdateRepository locationUpdateRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    private String customerToken;
    private String partnerToken;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;
    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("eta-customer"), Role.CUSTOMER);
        partnerToken = register(uniqueEmail("eta-partner"), Role.DELIVERY_PARTNER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "Eta Kitchen");
        menuItemId = addMenuItem(adminToken, restaurantId, "Pizza", "12.50");
    }

    private Long placeOrder() throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceOrderRequest.builder()
                                .restaurantId(restaurantId)
                                .deliveryAddress("9 Map St")
                                .deliveryLatitude(DEST_LAT)
                                .deliveryLongitude(DEST_LNG)
                                .items(List.of(OrderItemRequest.builder()
                                        .menuItemId(menuItemId)
                                        .quantity(1)
                                        .build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(body).get("id").asLong();
        createdOrderIds.add(orderId);
        return orderId;
    }

    private void accept(Long orderId) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isOk());
    }

    private void recordLocation(Long orderId, double lat, double lng) throws Exception {
        Order order = orderRepository.findById(orderId).orElseThrow();
        User partner = order.getDeliveryPartner();
        locationUpdateRepository.save(LocationUpdate.builder()
                .order(order)
                .deliveryPartner(partner)
                .lat(lat)
                .lng(lng)
                .build());
    }

    @Test
    void latestLocation_includesDistanceAndEtaAgainstRealDestination() throws Exception {
        Long orderId = placeOrder();
        accept(orderId);
        recordLocation(orderId, 18.6000, 73.8500);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(18.6000))
                .andExpect(jsonPath("$.lng").value(73.8500))
                .andExpect(jsonPath("$.distanceKm").value(closeTo(8.8793, 0.05)))
                .andExpect(jsonPath("$.etaMinutes").value(expectedEta(8.8793)));
    }

    @Test
    void newGpsUpdate_changesDistanceAndEta() throws Exception {
        Long orderId = placeOrder();
        accept(orderId);
        recordLocation(orderId, 18.6000, 73.8500);

        int firstEta = readEta(orderId);
        recordLocation(orderId, 18.5300, 73.8567);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(18.5300))
                .andExpect(jsonPath("$.lng").value(73.8567))
                .andExpect(jsonPath("$.distanceKm").value(closeTo(1.0675, 0.01)))
                .andExpect(jsonPath("$.etaMinutes").value(expectedEta(1.0675)))
                .andExpect(jsonPath("$.etaMinutes").value(not(firstEta)));
    }

    @Test
    void orderWithoutStoredDestination_returnsNullDistanceAndEta() throws Exception {
        Long orderId = placeOrder();
        accept(orderId);

        // Emulate a legacy record created before destination coordinates existed.
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setDeliveryLatitude(null);
        order.setDeliveryLongitude(null);
        orderRepository.save(order);
        recordLocation(orderId, 18.6000, 73.8500);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(nullValue()))
                .andExpect(jsonPath("$.etaMinutes").value(nullValue()));
    }

    private int readEta(Long orderId) throws Exception {
        String body = mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("etaMinutes").asInt();
    }

    private int expectedEta(double distanceKm) {
        return (int) Math.round(distanceKm / DEFAULT_SPEED_KMH * 60.0);
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdOrderIds) {
            locationUpdateRepository.findByOrderIdOrderByTimestampDesc(id)
                    .forEach(locationUpdateRepository::delete);
            orderRepository.findById(id).ifPresent(orderRepository::delete);
        }
        createdOrderIds.clear();
        if (restaurantId != null) {
            restaurantRepository.findById(restaurantId).ifPresent(restaurantRepository::delete);
        }
    }
}