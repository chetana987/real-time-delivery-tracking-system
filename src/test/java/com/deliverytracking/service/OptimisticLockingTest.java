package com.deliverytracking.service;

import com.deliverytracking.dto.ApiError;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Restaurant;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.GlobalExceptionHandler;
import com.deliverytracking.exception.OptimisticLockConflictException;
import com.deliverytracking.repository.MenuItemRepository;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.RestaurantRepository;
import com.deliverytracking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptimisticLockingTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

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

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void flushVersionConflict_isWrappedAsOptimisticLockConflict() {
        Order order = placedOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(99L)).thenReturn(Optional.of(partner()));
        doThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L)).when(entityManager).flush();

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 99L))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void secondAcceptAfterSuccessfulFirst_isRejected() {
        Order order = placedOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(99L)).thenReturn(Optional.of(partner()));

        orderService.acceptOrder(1L, 99L);

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 99L))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void optimisticLockConflictException_isMappedToHttp409() {
        ResponseEntity<ApiError> response = exceptionHandler.handleOptimisticLockConflict(
                new OptimisticLockConflictException("Order 1 was already accepted"),
                request("/api/orders/1/accept"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo("Order 1 was already accepted");
        assertThat(response.getBody().getPath()).isEqualTo("/api/orders/1/accept");
    }

    @Test
    void rawJpaVersionConflict_isMappedToHttp409() {
        ResponseEntity<ApiError> response = exceptionHandler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Order.class, 1L),
                request("/api/orders/1/accept"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Concurrent modification detected, please refresh and retry");
    }
}
