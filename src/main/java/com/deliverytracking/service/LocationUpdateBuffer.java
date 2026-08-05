package com.deliverytracking.service;

import com.deliverytracking.dto.LocationUpdateMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocationUpdateBuffer {

    private static final int MAX_BUFFERED = 100;

    private final Map<Long, ArrayDeque<LocationUpdateMessage>> buffer = new ConcurrentHashMap<>();

    public synchronized void buffer(Long partnerId, LocationUpdateMessage message) {
        ArrayDeque<LocationUpdateMessage> queue = buffer.computeIfAbsent(partnerId, k -> new ArrayDeque<>());
        queue.addLast(message);
        while (queue.size() > MAX_BUFFERED) {
            queue.pollFirst();
        }
    }

    public synchronized List<LocationUpdateMessage> drain(Long partnerId) {
        ArrayDeque<LocationUpdateMessage> queue = buffer.remove(partnerId);
        return queue == null ? List.of() : new ArrayList<>(queue);
    }
}
