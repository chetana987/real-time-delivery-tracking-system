package com.deliverytracking.config;

import com.deliverytracking.dto.LocationUpdateMessage;
import com.deliverytracking.service.LocationUpdateBuffer;
import com.deliverytracking.service.LocationUpdateService;
import com.deliverytracking.service.PartnerGeoService;
import com.deliverytracking.service.PartnerPresenceRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final PartnerPresenceRegistry presenceRegistry;
    private final LocationUpdateBuffer updateBuffer;
    private final LocationUpdateService locationUpdateService;
    private final PartnerGeoService partnerGeoService;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() instanceof StompPrincipal principal) {
            presenceRegistry.markOnline(principal.getUserId(), accessor.getSessionId());

            List<LocationUpdateMessage> buffered = updateBuffer.drain(principal.getUserId());
            for (LocationUpdateMessage update : buffered) {
                try {
                    locationUpdateService.recordAndBroadcast(update, principal.getUserId());
                } catch (RuntimeException e) {
                    log.warn("Failed to flush buffered update for partner {}: {}",
                            principal.getUserId(), e.getMessage());
                }
            }

            LocationUpdateMessage latest = buffered.isEmpty()
                    ? locationUpdateService.getLatestLocationForPartner(principal.getUserId()).orElse(null)
                    : buffered.get(buffered.size() - 1);
            if (latest != null) {
                partnerGeoService.addPartnerLocation(
                        principal.getUserId(), latest.getLat(), latest.getLng());
            }

            log.info("Delivery partner {} connected (session {})",
                    principal.getUserId(), accessor.getSessionId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long partnerId = presenceRegistry.markOffline(event.getSessionId());
        if (partnerId != null && !presenceRegistry.hasAnySession(partnerId)) {
            partnerGeoService.removePartnerLocation(partnerId);
        }
        log.info("Session disconnected: {}", event.getSessionId());
    }
}
