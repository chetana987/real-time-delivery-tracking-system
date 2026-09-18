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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the API contract of {@code GET /api/orders/{id}/location}:
 * <ul>
 *   <li>200 with the latest location when an update exists</li>
 *   <li>404 with a structured {@code ApiError} when the order has no location update yet</li>
 *   <li>404 with a distinct message when the order itself does not exist</li>
 *   <li>403 when the caller is not a participant of the order</li>
 * </ul>
 */
class LocationTrackingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LocationUpdateRepository locationUpdateRepository;

    private String customerToken;
    private String otherCustomerToken;
    private String partnerToken;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("loc-customer"), Role.CUSTOMER);
        otherCustomerToken = register(uniqueEmail("loc-other"), Role.CUSTOMER);
        partnerToken = register(uniqueEmail("loc-partner"), Role.DELIVERY_PARTNER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "Location Kitchen");
        menuItemId = addMenuItem(adminToken, restaurantId, "Pizza", "12.50");
    }

    private Long placeOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceOrderRequest.builder()
                                .restaurantId(restaurantId)
                                .deliveryAddress("9 Map St")
                                .deliveryLatitude(18.5204)
                                .deliveryLongitude(73.8567)
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
    void getLatestLocation_noLocationYet_returns404WithStructuredApiError() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("No location update recorded for order")))
                .andExpect(jsonPath("$.path").value("/api/orders/" + orderId + "/location"));
    }

    @Test
    void getLatestLocation_withLocation_returns200WithLatestLocation() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId);
        recordLocation(orderId, 12.9750, 77.6050);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.intValue()))
                .andExpect(jsonPath("$.lat").value(12.9750))
                .andExpect(jsonPath("$.lng").value(77.6050));
    }

    @Test
    void getLatestLocation_nonexistentOrder_returns404DistinctFromNoLocation() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/location", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Order not found")));
    }

    @Test
    void getLatestLocation_invalidId_returns400() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/location", -1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void getLatestLocation_nonParticipant_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId);

        mockMvc.perform(get("/api/orders/{id}/location", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("is not a participant of order")));
    }

    @Test
    void getLatestLocation_unauthenticated_returns401() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(get("/api/orders/{id}/location", orderId))
                .andExpect(status().isUnauthorized());
    }
}