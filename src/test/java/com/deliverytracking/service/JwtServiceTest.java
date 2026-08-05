package com.deliverytracking.service;

import com.deliverytracking.config.JwtProperties;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs256-0001";
    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;
    private UserPrincipal principal;
    private UserPrincipal otherPrincipal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(properties(SECRET, EXPIRATION_MS));
        principal = new UserPrincipal(User.builder()
                .id(7L)
                .name("Alice")
                .email("alice@test.com")
                .role(Role.CUSTOMER)
                .build());
        otherPrincipal = new UserPrincipal(User.builder()
                .id(8L)
                .name("Bob")
                .email("bob@test.com")
                .role(Role.DELIVERY_PARTNER)
                .build());
    }

    private JwtProperties properties(String secret, long expirationMs) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(expirationMs);
        return props;
    }

    @Test
    void generateToken_subjectIsTheEmail() {
        String token = jwtService.generateToken(principal);
        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@test.com");
    }

    @Test
    void generateToken_embedsUserIdAndRole() {
        Claims claims = jwtService.extractClaims(jwtService.generateToken(principal));
        assertThat(claims.getSubject()).isEqualTo("alice@test.com");
        assertThat((Number) claims.get("userId")).isEqualTo(7);
        assertThat(claims.get("role")).isEqualTo("CUSTOMER");
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void isTokenValid_returnsTrueForMatchingUser() {
        String token = jwtService.generateToken(principal);
        assertThat(jwtService.isTokenValid(token, principal)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForDifferentUser() {
        String token = jwtService.generateToken(principal);
        assertThat(jwtService.isTokenValid(token, otherPrincipal)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() {
        JwtService shortLived = new JwtService(properties(SECRET, -1_000L));
        String token = shortLived.generateToken(principal);
        assertThat(shortLived.isTokenValid(token, principal)).isFalse();
    }

    @Test
    void expiredToken_canStillBeParsed() {
        JwtService shortLived = new JwtService(properties(SECRET, -1_000L));
        String token = shortLived.generateToken(principal);
        assertThat(shortLived.extractEmail(token)).isEqualTo("alice@test.com");
    }

    @Test
    void tokenSignedWithDifferentSecret_isRejected() {
        JwtService other = new JwtService(properties(
                "a-different-but-still-long-enough-secret-key-0000000000", EXPIRATION_MS));
        String token = other.generateToken(principal);

        assertThatThrownBy(() -> jwtService.extractEmail(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void malformedToken_isRejected() {
        assertThatThrownBy(() -> jwtService.extractEmail("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void getExpirationMs_returnsConfiguredValue() {
        assertThat(jwtService.getExpirationMs()).isEqualTo(EXPIRATION_MS);
    }
}
