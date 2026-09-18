package com.deliverytracking.service;

import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Restaurant;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.OptimisticLockConflictException;
import com.deliverytracking.exception.OrderNotFoundException;
import com.deliverytracking.exception.ResourceNotFoundException;
import com.deliverytracking.repository.MenuItemRepository;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.RestaurantRepository;
import com.deliverytracking.repository.UserRepository;
import com.deliverytracking.dto.NearbyPartner;
import com.deliverytracking.dto.OrderItemRequest;
import com.deliverytracking.dto.OrderResponse;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.MenuItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceAcceptanceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private PartnerGeoService partnerGeoService;
    @Mock
    private PartnerAvailabilityService partnerAvailabilityService;

    @InjectMocks
    private OrderService orderService;

    private User partner() {
        return User.builder().id(99L).email("partner@test.com").role(Role.DELIVERY_PARTNER).build();
    }

    private Order placedOrder() {
        User customer = User.builder().id(10L).email("customer@test.com").role(Role.CUSTOMER).build();
        Restaurant restaurant = Restaurant.builder().id(20L).name("Test Kitchen").lat(1.0).lng(2.0).build();
        return Order.builder()
                .id(1L)
                .customer(customer)
                .restaurant(restaurant)
                .status(OrderStatus.PLACED)
                .deliveryAddress("42 Home St")
                .build();
    }

    @Test
    void acceptOrder_whenPlaced_assignsPartnerAndTransitionsToAccepted() {
        Order order = placedOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(99L)).thenReturn(Optional.of(partner()));

        var response = orderService.acceptOrder(1L, 99L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getDeliveryPartner().getId()).isEqualTo(99L);
        verify(entityManager).flush();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(response.getDeliveryPartnerId()).isEqualTo(99L);
    }

    @Test
    void acceptOrder_whenOrderMissing_throwsOrderNotFoundException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 99L))
                .isInstanceOf(OrderNotFoundException.class);

        verify(entityManager, never()).flush();
    }

    @Test
    void acceptOrder_whenPartnerMissing_throwsResourceNotFoundException() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(placedOrder()));
        // userRepository.findById left unstubbed -> Optional.empty

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acceptOrder_whenAlreadyAccepted_throwsOptimisticLockConflict() {
        Order order = placedOrder();
        order.transitionTo(OrderStatus.ACCEPTED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 99L))
                .isInstanceOf(OptimisticLockConflictException.class)
                .hasMessageContaining("already been accepted");

        verify(userRepository, never()).findById(anyLong());
        verify(entityManager, never()).flush();
    }

    @Test
    void acceptOrder_whenFlushFailsWithVersionConflict_wrapsInOptimisticLockConflict() {
        Order order = placedOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(99L)).thenReturn(Optional.of(partner()));
        doThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L)).when(entityManager).flush();

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 99L))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void placeOrder_filtersNearbyPartnersByAvailability() {
        ReflectionTestUtils.setField(orderService, "partnerSearchRadiusKm", 10.0);

        User customer = User.builder().id(10L).email("customer@test.com").role(Role.CUSTOMER).build();
        Restaurant restaurant = Restaurant.builder().id(20L).name("Test Kitchen").lat(1.0).lng(2.0).build();
        MenuItem item = MenuItem.builder().id(30L).name("Pizza").price(new BigDecimal("10.00")).build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(restaurantRepository.findById(20L)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByRestaurantId(20L)).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(partnerGeoService.findNearestPartners(1.0, 2.0, 10.0, 5)).thenReturn(List.of(
                NearbyPartner.builder().deliveryPartnerId(99L).distanceKm(1.0).build(),
                NearbyPartner.builder().deliveryPartnerId(98L).distanceKm(2.0).build()));
        when(partnerAvailabilityService.isAvailable(99L)).thenReturn(true);
        when(partnerAvailabilityService.isAvailable(98L)).thenReturn(false);

        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .restaurantId(20L)
                .deliveryAddress("42 Home St")
                .deliveryLatitude(1.5)
                .deliveryLongitude(2.5)
                .items(List.of(OrderItemRequest.builder()
                        .menuItemId(30L)
                        .quantity(1)
                        .build()))
                .build();

        OrderResponse response = orderService.placeOrder(request, 10L);

        assertThat(response.getNearbyPartners())
                .extracting(NearbyPartner::getDeliveryPartnerId)
                .containsExactly(99L);
    }
}
