package com.deliverytracking;

import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.MenuItem;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Restaurant;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.OptimisticLockConflictException;
import com.deliverytracking.integration.AbstractIntegrationTest;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.RestaurantRepository;
import com.deliverytracking.repository.UserRepository;
import com.deliverytracking.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RestaurantRepository restaurantRepository;

    private Long orderId;
    private Long customerId;
    private Long partner1Id;
    private Long partner2Id;
    private Long restaurantId;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Test Kitchen")
                .address("1 Test St")
                .lat(1.0)
                .lng(2.0)
                .build());
        restaurantId = restaurant.getId();

        MenuItem burger = restaurant.getMenu().stream()
                .filter(item -> item.getName().equals("Burger"))
                .findFirst()
                .orElseGet(() -> {
                    MenuItem item = MenuItem.builder()
                            .restaurant(restaurant)
                            .name("Burger")
                            .price(new BigDecimal("9.99"))
                            .build();
                    restaurant.addMenuItem(item);
                    restaurantRepository.save(restaurant);
                    return item;
                });

        User customer = userRepository.save(User.builder()
                .name("Customer")
                .email("customer-" + System.nanoTime() + "@test.com")
                .password("x")
                .role(Role.CUSTOMER)
                .build());
        customerId = customer.getId();

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .restaurantId(restaurantId)
                .deliveryAddress("42 Home St")
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(burger.getId())
                        .quantity(2)
                        .build()))
                .build();

        orderId = orderService.placeOrder(request, customerId).getId();

        partner1Id = savePartner("partner-1");
        partner2Id = savePartner("partner-2");
    }

    private Long savePartner(String prefix) {
        return userRepository.save(User.builder()
                .name("Partner")
                .email(prefix + "-" + System.nanoTime() + "@test.com")
                .password("x")
                .role(Role.DELIVERY_PARTNER)
                .build())
                .getId();
    }

    @Test
    void onlyOnePartnerCanAcceptTheSameOrder() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> first = pool.submit(acceptAs(partner1Id, ready, go, successes, conflicts));
        Future<?> second = pool.submit(acceptAs(partner2Id, ready, go, successes, conflicts));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getDeliveryPartner()).isNotNull();
    }

    private Runnable acceptAs(Long partnerId, CountDownLatch ready, CountDownLatch go,
                              AtomicInteger successes, AtomicInteger conflicts) {
        return () -> {
            ready.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                orderService.acceptOrder(orderId, partnerId);
                successes.incrementAndGet();
            } catch (OptimisticLockConflictException e) {
                conflicts.incrementAndGet();
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(orderRepository::delete);
        }
        if (restaurantId != null) {
            restaurantRepository.deleteById(restaurantId);
        }
        if (partner1Id != null) {
            userRepository.deleteById(partner1Id);
        }
        if (partner2Id != null) {
            userRepository.deleteById(partner2Id);
        }
        if (customerId != null) {
            userRepository.deleteById(customerId);
        }
    }
}
