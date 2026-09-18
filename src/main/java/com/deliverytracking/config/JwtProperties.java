package com.deliverytracking.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;

    private Long expirationMs;

    @PostConstruct
    void requireSecret() {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET environment variable) is required but not set. "
                    + "Set it to a random string of at least 32 characters (e.g. openssl rand -hex 32).");
        }
    }
}
