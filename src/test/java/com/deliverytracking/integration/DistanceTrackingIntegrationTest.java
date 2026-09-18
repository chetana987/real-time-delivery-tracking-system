package com.deliverytracking.integration;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.LocationUpdate;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.repository.LocationUpdateRepository;
import com.deliverytracking.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
 * Verifies the haversine distance returned by {@code GET /api/orders/{id}/location}:
 * the distance is computed from the partner's latest recorded GPS position to the
 * order's real destination coordinates, updates when a newer GPS position arrives,
 * and stays null when the order has no stored destination.
 */
class DistanceTrackingIntegrationTest extends AbstractIntegrationTest {

    private static final double DEST_LAT = 18.5204;
    private static final double DEST_LNG = 73.8567;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LocationUpdateRepository locationUpdateRepository;

    private String customerToken;
    private String partnerToken;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("dist-customer"), Role.CUSTOMER);
        partnerToken = register(uniqueEmail("dist-partner"), Role.DELIVERY_PARTNER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "Distance Kitchen");
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
        return objectMapper.readTree(body).get("id").asLong();
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
    void latestLocation_includesHaversineDistanceToRealDestination() throws Exception {
        Long orderId = placeOrder();
        accept(orderId);
        recordLocation(orderId, 18.6000, 73.8500);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(18.6000))
                .andExpect(jsonPath("$.lng").value(73.8500))
                .andExpect(jsonPath("$.distanceKm").value(closeTo(8.8793, 0.05)));
    }

    @Test
    void newGpsUpdate_changesCalculatedDistance() throws Exception {
        Long orderId = placeOrder();
        accept(orderId);
        recordLocation(orderId, 18.6000, 73.8500);

        double first = readDistance(orderId);
        recordLocation(orderId, 18.5300, 73.8567);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(18.5300))
                .andExpect(jsonPath("$.lng").value(73.8567))
                .andExpect(jsonPath("$.distanceKm").value(closeTo(1.0675, 0.01)))
                .andExpect(jsonPath("$.distanceKm").value(not(closeTo(first, 0.01))));
    }

    @Test
    void orderWithoutStoredDestination_returnsNullDistance() throws Exception {
        Long orderId = placeOrder();
        accept(orderId);

        // Emulate a legacy record created before destination coordinates existed:
        // coordinates are nullable in the schema but PlaceOrderRequest requires them.
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setDeliveryLatitude(null);
        order.setDeliveryLongitude(null);
        orderRepository.save(order);
        recordLocation(orderId, 18.6000, 73.8500);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(nullValue()));
    }

    private double readDistance(Long orderId) throws Exception {
        String body = mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("distanceKm").asDouble();
    }
}