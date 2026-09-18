package com.deliverytracking.integration;

import com.deliverytracking.dto.LocationUpdateMessage;
import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.OrderStatusRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.BadRequestException;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.RestaurantRepository;
import com.deliverytracking.repository.UserRepository;
import com.deliverytracking.service.LocationUpdateService;
import com.deliverytracking.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the cancellation contract of {@code PATCH /api/orders/{id}/cancel}:
 * <ul>
 *   <li>customers may cancel their own order while it is still in PLACED state (200, status CANCELLED)</li>
 *   <li>a customer cannot cancel another customer's order (403)</li>
 *   <li>ACCEPTED / DELIVERED / already CANCELLED orders cannot be cancelled (400)</li>
 *   <li>delivery partners and admins cannot cancel orders (403)</li>
 *   <li>cancelled orders leave the partner's available pool</li>
 *   <li>optimistic locking still guards concurrent cancellation (one 200, one conflict)</li>
 *   <li>a cancelled order stops accepting location updates from its assigned partner</li>
 * </ul>
 */
class OrderCancellationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private LocationUpdateService locationUpdateService;

    private String customerToken;
    private String otherCustomerToken;
    private String partnerToken;
    private String partnerEmail;
    private String adminToken;
    private Long restaurantId;
    private Long menuItemId;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("cancel-customer"), Role.CUSTOMER);
        otherCustomerToken = register(uniqueEmail("cancel-other"), Role.CUSTOMER);
        partnerEmail = uniqueEmail("cancel-partner");
        partnerToken = register(partnerEmail, Role.DELIVERY_PARTNER);
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        restaurantId = createRestaurant(adminToken, "Cancellation Kitchen");
        menuItemId = addMenuItem(adminToken, restaurantId, "Burger", "9.99");
    }

    private Long placeOrder(String token) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceOrderRequest.builder()
                                .restaurantId(restaurantId)
                                .deliveryAddress("77 Cancel St")
                                .deliveryLatitude(18.5204)
                                .deliveryLongitude(73.8567)
                                .items(List.of(OrderItemRequest.builder()
                                        .menuItemId(menuItemId)
                                        .quantity(2)
                                        .build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();
        createdOrderIds.add(id);
        return id;
    }

    private void accept(Long orderId) throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/accept", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder cancel(Long orderId, String token) {
        return patch("/api/orders/{id}/cancel", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void advanceToDelivered(Long orderId) throws Exception {
        for (String next : List.of("PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED")) {
            mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(OrderStatusRequest.builder()
                                    .status(OrderStatus.valueOf(next))
                                    .build())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void cancel_ownPlacedOrder_returns200WithCancelledStatus() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(cancel(orderId, customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.intValue()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.totalAmount").value(19.98));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_anotherCustomersOrder_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(cancel(orderId, otherCustomerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only the customer who placed this order can cancel it"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void cancel_acceptedOrder_returns400() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId);

        mockMvc.perform(cancel(orderId, customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status transition: ACCEPTED -> CANCELLED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void cancel_deliveredOrder_returns400() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId);
        advanceToDelivered(orderId);

        mockMvc.perform(cancel(orderId, customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status transition: DELIVERED -> CANCELLED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cancel_alreadyCancelledOrder_returns400() throws Exception {
        Long orderId = placeOrder(customerToken);
        mockMvc.perform(cancel(orderId, customerToken)).andExpect(status().isOk());

        mockMvc.perform(cancel(orderId, customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status transition: CANCELLED -> CANCELLED"));
    }

    @Test
    void cancel_deliveryPartner_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(cancel(orderId, partnerToken))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void cancel_admin_returns403() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(cancel(orderId, adminToken))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void cancel_nonexistentOrder_returns404() throws Exception {
        mockMvc.perform(cancel(999_999L, customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found: 999999"));
    }

    @Test
    void cancel_invalidId_returns400() throws Exception {
        mockMvc.perform(cancel(-1L, customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void cancel_unauthenticated_returns401() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(patch("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStatus_toCancelled_returns400() throws Exception {
        Long orderId = placeOrder(customerToken);

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OrderStatusRequest.builder()
                                .status(OrderStatus.CANCELLED)
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Orders are cancelled via the cancel endpoint"));
    }

    @Test
    void cancelPlacedOrder_orderLeavesAvailablePool() throws Exception {
        Long orderId = placeOrder(customerToken);
        mockMvc.perform(cancel(orderId, customerToken)).andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/available")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(orderId.intValue()))));
    }

    @Test
    void concurrentCancel_onlyOneRequestSucceeds() throws Exception {
        Long orderId = placeOrder(customerToken);
        Long customerUserId = orderRepository.findById(orderId).orElseThrow().getCustomer().getId();

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> first = pool.submit(cancelAs(orderId, customerUserId, ready, go, successes, rejections));
        Future<?> second = pool.submit(cancelAs(orderId, customerUserId, ready, go, successes, rejections));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(1);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private Runnable cancelAs(Long orderId, Long customerId, CountDownLatch ready, CountDownLatch go,
                              AtomicInteger successes, AtomicInteger rejections) {
        return () -> {
            ready.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                orderService.cancelOrder(orderId, customerId);
                successes.incrementAndGet();
            } catch (RuntimeException e) {
                rejections.incrementAndGet();
            }
        };
    }

    @Test
    void locationUpdate_cancelledOrder_isRejected() throws Exception {
        Long orderId = placeOrder(customerToken);

        // PLACED → CANCELLED never retains a delivery partner, so construct the
        // "cancelled order with an assigned partner" state directly at data level
        // to prove the terminal-status guard rejects updates regardless.
        Order order = orderRepository.findById(orderId).orElseThrow();
        User partner = userRepository.findByEmail(partnerEmail).orElseThrow();
        order.setDeliveryPartner(partner);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        LocationUpdateMessage update = LocationUpdateMessage.builder()
                .orderId(orderId)
                .lat(12.9750)
                .lng(77.6050)
                .build();

        assertThatThrownBy(() -> locationUpdateService.recordAndBroadcast(update, partner.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void locationUpdate_deliveredOrder_isRejected() throws Exception {
        Long orderId = placeOrder(customerToken);
        accept(orderId);
        advanceToDelivered(orderId);

        Order order = orderRepository.findById(orderId).orElseThrow();
        Long partnerId = order.getDeliveryPartner().getId();

        LocationUpdateMessage update = LocationUpdateMessage.builder()
                .orderId(orderId)
                .lat(12.9750)
                .lng(77.6050)
                .build();

        assertThatThrownBy(() -> locationUpdateService.recordAndBroadcast(update, partnerId))
                .isInstanceOf(BadRequestException.class);
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdOrderIds) {
            orderRepository.findById(id).ifPresent(orderRepository::delete);
        }
        createdOrderIds.clear();
        if (restaurantId != null) {
            restaurantRepository.findById(restaurantId).ifPresent(restaurantRepository::delete);
        }
    }
}