package com.deliverytracking.service;

import com.deliverytracking.entity.AvailabilityStatus;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerAvailabilityService {

    private static final List<OrderStatus> ACTIVE_DELIVERY_STATUSES = List.of(
            OrderStatus.ACCEPTED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY);

    private final PartnerPresenceRegistry presenceRegistry;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public AvailabilityStatus getStatus(Long partnerId) {
        if (!presenceRegistry.isOnline(partnerId)) {
            return AvailabilityStatus.OFFLINE;
        }
        return hasOngoingDelivery(partnerId)
                ? AvailabilityStatus.BUSY
                : AvailabilityStatus.AVAILABLE;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Long partnerId) {
        return presenceRegistry.isOnline(partnerId) && !hasOngoingDelivery(partnerId);
    }

    private boolean hasOngoingDelivery(Long partnerId) {
        return busyPartnerIds().contains(partnerId);
    }

    private Set<Long> busyPartnerIds() {
        return orderRepository
                .findDistinctByDeliveryPartnerIdNotNullAndStatusIn(ACTIVE_DELIVERY_STATUSES)
                .stream()
                .map(order -> order.getDeliveryPartner().getId())
                .collect(Collectors.toSet());
    }
}