package com.deliverytracking.service;

import com.deliverytracking.dto.NearbyPartner;
import com.deliverytracking.dto.OrderFilter;
import com.deliverytracking.dto.OrderResponse;
import com.deliverytracking.dto.PageParams;
import com.deliverytracking.dto.PageResponse;
import com.deliverytracking.dto.PlaceOrderRequest;
import com.deliverytracking.entity.MenuItem;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderItem;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.Restaurant;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.BadRequestException;
import com.deliverytracking.exception.OptimisticLockConflictException;
import com.deliverytracking.exception.OrderNotFoundException;
import com.deliverytracking.exception.ResourceNotFoundException;
import com.deliverytracking.exception.UnauthorizedActionException;
import com.deliverytracking.repository.MenuItemRepository;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.OrderSpecs;
import com.deliverytracking.repository.RestaurantRepository;
import com.deliverytracking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final EntityManager entityManager;
    private final PartnerGeoService partnerGeoService;

    private static final Set<String> ORDER_SORT_FIELDS = Set.of("id", "status", "totalAmount", "createdAt", "updatedAt");

    @Value("${app.partner-search-radius-km:10}")
    private double partnerSearchRadiusKm;

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request, Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + request.getRestaurantId()));

        Map<Long, MenuItem> menu = menuItemRepository.findByRestaurantId(request.getRestaurantId()).stream()
                .collect(Collectors.toMap(MenuItem::getId, item -> item));

        if (menu.isEmpty()) {
            throw new BadRequestException("Restaurant has no menu items");
        }

        Order order = Order.builder()
                .customer(customer)
                .restaurant(restaurant)
                .status(OrderStatus.PLACED)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .build();

        List<OrderItem> items = request.getItems().stream()
                .map(itemRequest -> {
                    MenuItem menuItem = menu.get(itemRequest.getMenuItemId());
                    if (menuItem == null) {
                        throw new BadRequestException("Menu item " + itemRequest.getMenuItemId()
                                + " is not on this restaurant's menu");
                    }
                    return OrderItem.builder()
                            .itemName(menuItem.getName())
                            .quantity(itemRequest.getQuantity())
                            .price(menuItem.getPrice())
                            .build();
                })
                .toList();

        items.forEach(order::addItem);

        BigDecimal total = items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);

        OrderResponse response = OrderResponse.from(orderRepository.save(order));
        response.setNearbyPartners(findNearbyAvailablePartners(restaurant));
        return response;
    }

    private List<NearbyPartner> findNearbyAvailablePartners(Restaurant restaurant) {
        Set<Long> busyPartnerIds = orderRepository
                .findDistinctByDeliveryPartnerIdNotNullAndStatusIn(List.of(
                        OrderStatus.ACCEPTED,
                        OrderStatus.PICKED_UP,
                        OrderStatus.OUT_FOR_DELIVERY))
                .stream()
                .map(order -> order.getDeliveryPartner().getId())
                .collect(Collectors.toSet());

        return partnerGeoService.findNearestPartners(
                        restaurant.getLat(), restaurant.getLng(), partnerSearchRadiusKm, 5)
                .stream()
                .filter(nearby -> !busyPartnerIds.contains(nearby.getDeliveryPartnerId()))
                .toList();
    }

    @Transactional
    public OrderResponse acceptOrder(Long orderId, Long partnerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new OptimisticLockConflictException("Order " + orderId
                    + " has already been accepted by another partner");
        }

        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found"));

        order.setDeliveryPartner(partner);

        try {
            order.transitionTo(OrderStatus.ACCEPTED);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("Order " + orderId
                    + " was already accepted by another partner");
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus, Long partnerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (newStatus == OrderStatus.ACCEPTED) {
            throw new BadRequestException("Orders are accepted via the accept endpoint");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            throw new BadRequestException("Orders are cancelled via the cancel endpoint");
        }

        requireAssignedPartner(order, partnerId);
        order.transitionTo(newStatus);

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isOwner = order.getCustomer() != null && order.getCustomer().getId().equals(customerId);
        if (!isOwner) {
            throw new UnauthorizedActionException("Only the customer who placed this order can cancel it");
        }

        try {
            order.transitionTo(OrderStatus.CANCELLED);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("Order " + orderId
                    + " was modified concurrently; refresh and retry");
        }

        return OrderResponse.from(order);
    }

    private void requireAssignedPartner(Order order, Long partnerId) {
        if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getId().equals(partnerId)) {
            throw new UnauthorizedActionException("Only the assigned delivery partner can update this order");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(UserPrincipal principal, OrderFilter filter, PageParams params) {
        Specification<Order> spec;
        if (principal.getRole() == Role.CUSTOMER) {
            spec = OrderSpecs.customerId(principal.getId());
        } else if (principal.getRole() == Role.DELIVERY_PARTNER) {
            spec = OrderSpecs.deliveryPartnerId(principal.getId());
        } else {
            spec = OrderSpecs.any();
        }

        if (filter.status() != null) {
            spec = spec.and(OrderSpecs.status(filter.status()));
        }
        if (filter.customerId() != null) {
            spec = spec.and(OrderSpecs.customerId(filter.customerId()));
        }
        if (filter.deliveryPartnerId() != null) {
            spec = spec.and(OrderSpecs.deliveryPartnerId(filter.deliveryPartnerId()));
        }
        if (filter.restaurantId() != null) {
            spec = spec.and(OrderSpecs.restaurantId(filter.restaurantId()));
        }
        if (filter.from() != null || filter.to() != null) {
            spec = spec.and(OrderSpecs.createdAtBetween(filter.from(), filter.to()));
        }

        Pageable pageable = PagingSupport.pageRequest(params, ORDER_SORT_FIELDS, "createdAt", "desc");
        return PageResponse.from(orderRepository.findAll(spec, pageable).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAvailableOrders(Long restaurantId, PageParams params) {
        Specification<Order> spec = Specification.where(OrderSpecs.status(OrderStatus.PLACED));
        if (restaurantId != null) {
            spec = spec.and(OrderSpecs.restaurantId(restaurantId));
        }
        Pageable pageable = PagingSupport.pageRequest(params, ORDER_SORT_FIELDS, "createdAt", "desc");
        return PageResponse.from(orderRepository.findAll(spec, pageable).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, UserPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isCustomer = order.getCustomer() != null && order.getCustomer().getId().equals(principal.getId());
        boolean isPartner = order.getDeliveryPartner() != null
                && order.getDeliveryPartner().getId().equals(principal.getId());
        if (principal.getRole() != Role.ADMIN && !isCustomer && !isPartner) {
            throw new UnauthorizedActionException("User " + principal.getId()
                    + " is not a participant of order " + orderId);
        }

        return OrderResponse.from(order);
    }
}
