package com.deliverytracking.integration;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.Role;
import com.deliverytracking.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the delivery destination contract of {@code POST /api/orders}:
 * <ul>
 *   <li>the destination (address, latitude, longitude) is required, validated and echoed back</li>
 *   <li>latitude must be in [-90, 90], longitude in [-180, 180], otherwise a structured 400 is returned</li>
 *   <li>the destination is persisted on the order and survives re-fetching</li>
 * </ul>
 */
class OrderDestinationIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = 18.5204;
    private static final double LNG = 73.8567;

    @Autowired
    private OrderRepository orderRepository;

    private String customerToken;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("dest-customer"), Role.CUSTOMER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "Destination Kitchen");
        menuItemId = addMenuItem(adminToken, restaurantId, "Burger", "9.99");
    }

    private PlaceOrderRequest request(String address, Double lat, Double lng) {
        return PlaceOrderRequest.builder()
                .restaurantId(restaurantId)
                .deliveryAddress(address)
                .deliveryLatitude(lat)
                .deliveryLongitude(lng)
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(menuItemId)
                        .quantity(1)
                        .build()))
                .build();
    }

    private String placeOrderBody(PlaceOrderRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void placeOrder_validDestination_returns201WithDestination() throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", LAT, LNG))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryAddress").value("123 Main Street, Pune"))
                .andExpect(jsonPath("$.deliveryLatitude").value(LAT))
                .andExpect(jsonPath("$.deliveryLongitude").value(LNG))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("id").asLong();
        createdOrderIds.add(orderId);
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getDeliveryAddress()).isEqualTo("123 Main Street, Pune");
        assertThat(order.getDeliveryLatitude()).isEqualTo(LAT);
        assertThat(order.getDeliveryLongitude()).isEqualTo(LNG);
    }

    @Test
    void placeOrder_destination_persistsAndIsReturnedOnFetch() throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("42 Evergreen Terrace, Pune", LAT, LNG))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("id").asLong();
        createdOrderIds.add(orderId);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress").value("42 Evergreen Terrace, Pune"))
                .andExpect(jsonPath("$.deliveryLatitude").value(LAT))
                .andExpect(jsonPath("$.deliveryLongitude").value(LNG));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getDeliveryLatitude()).isEqualTo(LAT);
        assertThat(order.getDeliveryLongitude()).isEqualTo(LNG);
        assertThat(order.getDeliveryAddress()).isEqualTo("42 Evergreen Terrace, Pune");
    }

    @Test
    void placeOrder_missingAddress_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request(null, LAT, LNG))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.deliveryAddress").exists());
    }

    @Test
    void placeOrder_missingLatitude_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", null, LNG))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.deliveryLatitude").exists());
    }

    @Test
    void placeOrder_missingLongitude_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", LAT, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.deliveryLongitude").exists());
    }

    @Test
    void placeOrder_latitudeTooHigh_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", 91.0, LNG))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryLatitude").exists());
    }

    @Test
    void placeOrder_latitudeTooLow_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", -91.0, LNG))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryLatitude").exists());
    }

    @Test
    void placeOrder_longitudeTooHigh_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", LAT, 181.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryLongitude").exists());
    }

    @Test
    void placeOrder_longitudeTooLow_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(request("123 Main Street, Pune", LAT, -181.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryLongitude").exists());
    }

    @Test
    void placeOrder_boundaryCoordinates_areAccepted() throws Exception {
        String[] bodies = {
                placeOrderBody(request("S90", 90.0, 180.0)),
                placeOrderBody(request("N90", -90.0, -180.0)),
                placeOrderBody(request("Origin", 0.0, 0.0))
        };
        for (String body : bodies) {
            String resp = mockMvc.perform(post("/api/orders")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andReturn().getResponse().getContentAsString();
            createdOrderIds.add(objectMapper.readTree(resp).get("id").asLong());
        }
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdOrderIds) {
            orderRepository.findById(id).ifPresent(orderRepository::delete);
        }
        createdOrderIds.clear();
    }
}