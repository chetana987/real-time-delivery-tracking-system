package com.deliverytracking.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PartnerPresenceRegistry {

    private final Map<String, Long> sessionToPartner = new ConcurrentHashMap<>();
    private final Map<Long, String> partnerToSession = new ConcurrentHashMap<>();

    public void markOnline(Long partnerId, String sessionId) {
        sessionToPartner.put(sessionId, partnerId);
        partnerToSession.put(partnerId, sessionId);
    }

    public Long markOffline(String sessionId) {
        Long partnerId = sessionToPartner.remove(sessionId);
        if (partnerId != null && !sessionToPartner.containsValue(partnerId)) {
            partnerToSession.remove(partnerId);
        }
        return partnerId;
    }

    public boolean hasAnySession(Long partnerId) {
        return partnerToSession.containsKey(partnerId);
    }

    public boolean isOnline(Long partnerId) {
        return partnerToSession.containsKey(partnerId);
    }
}
