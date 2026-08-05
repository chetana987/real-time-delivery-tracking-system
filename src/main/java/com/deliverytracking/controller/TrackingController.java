package com.deliverytracking.controller;

import com.deliverytracking.config.StompPrincipal;
import com.deliverytracking.dto.LocationUpdateMessage;
import com.deliverytracking.service.LocationUpdateBuffer;
import com.deliverytracking.service.LocationUpdateService;
import com.deliverytracking.service.PartnerPresenceRegistry;
import com.deliverytracking.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);
    private static final int TOKEN_CAPACITY = 1;

    private final LocationUpdateService locationUpdateService;
    private final LocationUpdateBuffer updateBuffer;
    private final PartnerPresenceRegistry presenceRegistry;
    private final RateLimiterService rateLimiterService;

    @Value("${app.location-update-min-interval-seconds:2}")
    private double minIntervalSeconds;

    @MessageMapping("/location-update")
    public void handleLocationUpdate(LocationUpdateMessage message, SimpMessageHeaderAccessor headerAccessor) {
        if (!(headerAccessor.getUser() instanceof StompPrincipal principal)) {
            log.warn("Rejected location update for order {}: unauthenticated STOMP session", message.getOrderId());
            return;
        }

        if (message.getOrderId() == null || message.getLat() == null || message.getLng() == null) {
            log.warn("Malformed location update rejected from partner {}", principal.getUserId());
            return;
        }

        if (!rateLimiterService.tryAcquire(
                "ratelimit:location:" + principal.getUserId(),
                TOKEN_CAPACITY,
                1.0 / Math.max(minIntervalSeconds, 0.1))) {
            log.warn("Rate limited: partner {} tried to send a location update too fast (order {})",
                    principal.getUserId(), message.getOrderId());
            return;
        }

        if (!presenceRegistry.isOnline(principal.getUserId())) {
            log.warn("Partner {} appears offline; buffering update for order {}",
                    principal.getUserId(), message.getOrderId());
            updateBuffer.buffer(principal.getUserId(), message);
            return;
        }

        try {
            locationUpdateService.recordAndBroadcast(message, principal.getUserId());
        } catch (RuntimeException e) {
            log.warn("Could not process location update for order {}: {}",
                    message.getOrderId(), e.getMessage());
        }
    }
}
