package com.deliverytracking.repository;

import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByDeliveryPartnerId(Long deliveryPartnerId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    boolean existsByRestaurantId(Long restaurantId);

    boolean existsByIdAndCustomerId(Long id, Long customerId);

    boolean existsByIdAndDeliveryPartnerId(Long id, Long deliveryPartnerId);

    List<Order> findDistinctByDeliveryPartnerIdNotNullAndStatusIn(List<OrderStatus> statuses);
}
