package com.deliverytracking.service;

import com.deliverytracking.dto.LocationUpdateMessage;
import com.deliverytracking.dto.PageParams;
import com.deliverytracking.dto.PageResponse;
import com.deliverytracking.entity.LocationUpdate;
import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.BadRequestException;
import com.deliverytracking.exception.OrderNotFoundException;
import com.deliverytracking.exception.ResourceNotFoundException;
import com.deliverytracking.exception.UnauthorizedActionException;
import com.deliverytracking.repository.LocationUpdateRepository;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocationUpdateService {

    private final LocationUpdateRepository locationUpdateRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PartnerGeoService partnerGeoService;

    private static final Set<String> LOCATION_SORT_FIELDS = Set.of("id", "timestamp");

    @Transactional
    public void recordAndBroadcast(LocationUpdateMessage message, Long partnerId) {
        Order order = orderRepository.findById(message.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(message.getOrderId()));

        requireAssignedPartner(order, partnerId);

        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED || status == OrderStatus.DELIVERED) {
            throw new BadRequestException("Order " + order.getId() + " is " + status
                    + " and no longer accepts location updates");
        }

        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found: " + partnerId));

        LocationUpdate update = LocationUpdate.builder()
                .order(order)
                .deliveryPartner(partner)
                .lat(message.getLat())
                .lng(message.getLng())
                .build();
        locationUpdateRepository.save(update);

        partnerGeoService.updatePartnerLocation(partnerId, message.getLat(), message.getLng());

        LocationUpdateMessage outbound = LocationUpdateMessage.builder()
                .orderId(order.getId())
                .deliveryPartnerId(partnerId)
                .lat(message.getLat())
                .lng(message.getLng())
                .timestamp(update.getTimestamp())
                .build();

        messagingTemplate.convertAndSend("/topic/order/" + order.getId() + "/location", outbound);
    }

    @Transactional(readOnly = true)
    public LocationUpdateMessage getLatestLocation(Long orderId, Long userId) {
        Order order = requireParticipant(orderId, userId);

        return locationUpdateRepository.findTopByOrderIdOrderByTimestampDesc(orderId)
                .map(this::toMessage)
                .orElseThrow(() -> new ResourceNotFoundException("No location update recorded for order " + orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationUpdateMessage> getLocationHistory(Long orderId, Long userId, PageParams params) {
        requireParticipant(orderId, userId);

        Pageable pageable = PagingSupport.pageRequest(params, LOCATION_SORT_FIELDS, "timestamp", "desc");
        return PageResponse.from(locationUpdateRepository.findByOrderId(orderId, pageable).map(this::toMessage));
    }

    public Optional<LocationUpdateMessage> getLatestLocationForPartner(Long partnerId) {
        return locationUpdateRepository.findFirstByDeliveryPartnerIdOrderByTimestampDesc(partnerId)
                .map(this::toMessage);
    }

    private Order requireParticipant(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isCustomer = order.getCustomer() != null && order.getCustomer().getId().equals(userId);
        boolean isPartner = order.getDeliveryPartner() != null && order.getDeliveryPartner().getId().equals(userId);
        if (!isCustomer && !isPartner) {
            throw new UnauthorizedActionException("User " + userId + " is not a participant of order " + orderId);
        }
        return order;
    }

    private void requireAssignedPartner(Order order, Long partnerId) {
        if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getId().equals(partnerId)) {
            throw new UnauthorizedActionException("Partner " + partnerId + " is not assigned to order " + order.getId());
        }
    }

    private LocationUpdateMessage toMessage(LocationUpdate update) {
        return LocationUpdateMessage.builder()
                .orderId(update.getOrder().getId())
                .deliveryPartnerId(update.getDeliveryPartner().getId())
                .lat(update.getLat())
                .lng(update.getLng())
                .timestamp(update.getTimestamp())
                .build();
    }
}
