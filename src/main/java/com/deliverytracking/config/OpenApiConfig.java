package com.deliverytracking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger documentation configuration.
 *
 * <p>Defines the API metadata (title, version, description, contact), the JWT
 * security scheme used by Swagger UI's "Authorize" button, and the logical
 * endpoint groups shown in the UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI deliveryTrackingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Delivery Tracking System API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                REST API for the real-time food/ride delivery tracking system.

                                **Roles**
                                - `CUSTOMER` — place orders, view own orders, cancel own PLACED orders.
                                - `DELIVERY_PARTNER` — accept orders, update delivery status, publish live location.
                                - `ADMIN` — manage restaurants and menu items.

                                **Authentication**
                                Obtain a JWT from `POST /api/auth/register` or `POST /api/auth/login`, then click
                                *Authorize* and paste the token to test protected endpoints. Live location updates
                                are streamed over STOMP WebSocket at `/ws` (not part of this REST document).
                                """)
                        .contact(new Contact()
                                .name("Delivery Tracking Team")
                                .email("dev@deliverytracking.local")
                                .url("https://github.com/deliverytracking")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token returned by /api/auth/login or /api/auth/register. "
                                        + "Paste the raw token (no 'Bearer ' prefix required).")))
                .tags(List.of(
                        new Tag().name("Authentication").description("Public endpoints to create an account and obtain a JWT"),
                        new Tag().name("Restaurants").description("Browse restaurants (public). Create, update and delete are ADMIN only"),
                        new Tag().name("Menu").description("Menu items of a restaurant. Management is ADMIN only"),
                        new Tag().name("Orders").description("Customers place and view orders (and may cancel their own PLACED orders); delivery partners accept and update them"),
                        new Tag().name("Location Tracking").description("Order location via REST. Live updates stream over STOMP WebSocket at /ws"),
                        new Tag().name("Admin").description("Admin-only management operations (same endpoints also listed under their domain group)"),
                        new Tag().name("Health").description("Liveness endpoint")));
    }
}