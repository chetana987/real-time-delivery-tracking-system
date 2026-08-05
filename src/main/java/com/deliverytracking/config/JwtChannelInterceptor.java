package com.deliverytracking.config;

import com.deliverytracking.entity.Role;
import com.deliverytracking.repository.OrderRepository;
import com.deliverytracking.service.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TRACKING_TOPIC = Pattern.compile("^/topic/order/(\\d+)/location$");

    private final JwtService jwtService;
    private final OrderRepository orderRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            return;
        }
        try {
            Claims claims = jwtService.extractClaims(token);
            Long userId = ((Number) claims.get("userId")).longValue();
            String email = claims.getSubject();
            Role role = Role.valueOf(claims.get("role", String.class));
            accessor.setUser(new StompPrincipal(userId, email, role));
        } catch (RuntimeException e) {
            // invalid or expired token: leave the STOMP session unauthenticated
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/order/")) {
            return;
        }

        Long orderId = parseOrderId(destination);
        if (orderId == null) {
            throw new AccessDeniedException("Invalid tracking destination");
        }
        if (!(accessor.getUser() instanceof StompPrincipal principal)) {
            throw new AccessDeniedException("Authentication required to track an order");
        }

        boolean isCustomer = orderRepository.existsByIdAndCustomerId(orderId, principal.getUserId());
        boolean isPartner = orderRepository.existsByIdAndDeliveryPartnerId(orderId, principal.getUserId());
        if (!isCustomer && !isPartner) {
            throw new AccessDeniedException("Not a participant of order " + orderId);
        }
    }

    private Long parseOrderId(String destination) {
        Matcher matcher = TRACKING_TOPIC.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String token = accessor.getFirstNativeHeader("token");
        return (token != null && !token.isBlank()) ? token : null;
    }
}
