package com.deliverytracking.integration;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.OrderStatusRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Role;
import com.deliverytracking.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    private String customerToken;
    private String partnerToken;
    private String partner2Token;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("order-customer"), Role.CUSTOMER);
        partnerToken = register(uniqueEmail("order-partner"), Role.DELIVERY_PARTNER);
        partner2Token = register(uniqueEmail("order-partner-2"), Role.DELIVERY_PARTNER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "OrderFlow Kitchen");
        menuItemId = addMenuItem(adminToken, restaurantId, "Burger", "9.99");
    }

    private String placeOrderBody(int quantity) throws Exception {
        return objectMapper.writeValueAsString(PlaceOrderRequest.builder()
                .restaurantId(restaurantId)
                .deliveryAddress("42 Home St")
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(menuItemId)
                        .quantity(quantity)
                        .build()))
                .build());
    }

    private Long placeOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void accept(Long orderId, String token) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder updateStatus(Long orderId, String token, String status) throws Exception {
        return patch("/api/orders/{id}/status", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(OrderStatusRequest.builder()
                        .status(OrderStatus.valueOf(status))
                        .build()));
    }

    @Test
    void placeOrder_customer_createsPlacedOrder() throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.restaurantName").value("OrderFlow Kitchen"))
                .andExpect(jsonPath("$.totalAmount").value(19.98))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("id").asLong();
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("19.98");
        assertThat(order.getDeliveryAddress()).isEqualTo("42 Home St");
    }

    @Test
    void acceptOrder_partner_assignsPartnerAndTransitionsToAccepted() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.deliveryPartnerId").isNumber());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getDeliveryPartner()).isNotNull();
    }

    @Test
    void acceptOrder_secondPartner_returns409Conflict() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId, partnerToken);

        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner2Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already been accepted")));
    }

    @Test
    void updateStatus_assignedPartner_walksFullPipeline() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId, partnerToken);

        mockMvc.perform(updateStatus(orderId, partnerToken, "PICKED_UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
        mockMvc.perform(updateStatus(orderId, partnerToken, "OUT_FOR_DELIVERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
        mockMvc.perform(updateStatus(orderId, partnerToken, "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void updateStatus_skippedTransition_returns400() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId, partnerToken);

        mockMvc.perform(updateStatus(orderId, partnerToken, "OUT_FOR_DELIVERY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status transition: ACCEPTED -> OUT_FOR_DELIVERY"));
    }

    @Test
    void updateStatus_toAccepted_returns400() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(updateStatus(orderId, partnerToken, "ACCEPTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Orders are accepted via the accept endpoint"));
    }

    @Test
    void updateStatus_unassignedPartner_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId, partnerToken);

        mockMvc.perform(updateStatus(orderId, partner2Token, "PICKED_UP"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_customer_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(updateStatus(orderId, customerToken, "PICKED_UP"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAvailableOrders_partner_seesPlacedOrders() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(get("/api/orders/available")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(orderId.intValue())));
    }

    @Test
    void getMyOrders_customer_containsOwnOrders() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(orderId.intValue())));
    }

    @Test
    void getOrder_customer_canReadOwnOrder() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()));
    }

    @Test
    void getOrder_otherCustomer_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);
        String otherCustomer = register(uniqueEmail("other-customer"), Role.CUSTOMER);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherCustomer))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAvailableOrders_customer_returns403() throws Exception {
        mockMvc.perform(get("/api/orders/available")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyOrders_admin_seesAllOrders() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(orderId.intValue())));
    }
}
