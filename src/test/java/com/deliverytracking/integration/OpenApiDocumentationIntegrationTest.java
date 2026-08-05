package com.deliverytracking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the springdoc-generated OpenAPI document and that Swagger UI is
 * reachable without authentication.
 */
class OpenApiDocumentationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApiJson_containsMetadataAndJwtSecurityScheme() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(root.path("info").path("title").asText()).isEqualTo("Delivery Tracking System API");
        assertThat(root.path("info").path("version").asText()).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(root.path("info").path("contact").path("email").asText()).isEqualTo("dev@deliverytracking.local");

        JsonNode bearer = root.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    void openApiJson_groupsEndpointsUnderLogicalTags() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);

        List<String> tagNames = new ArrayList<>();
        root.path("tags").forEach(tag -> tagNames.add(tag.path("name").asText()));
        assertThat(tagNames).contains("Authentication", "Restaurants", "Menu", "Orders",
                "Location Tracking", "Admin", "Health");

        assertThat(root.path("paths").path("/api/auth/login").path("post").path("tags").get(0).asText())
                .isEqualTo("Authentication");
        assertThat(root.path("paths").path("/api/orders").path("get").path("tags").get(0).asText())
                .isEqualTo("Orders");
        assertThat(root.path("paths").path("/api/orders").path("get").path("security").get(0).has("bearerAuth"))
                .isTrue();
        assertThat(root.path("paths").path("/api/restaurants").path("post").path("tags").toString())
                .contains("Restaurants", "Admin");
    }

    @Test
    void openApiJson_documentsPaginationAndFilterQueryParameters() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);

        List<String> orderParams = new ArrayList<>();
        root.path("paths").path("/api/orders").path("get").path("parameters").forEach(p ->
                orderParams.add(p.path("name").asText()));
        assertThat(orderParams).contains("page", "size", "sortBy", "direction",
                "status", "customer", "deliveryPartner", "restaurant", "from", "to");

        List<String> restaurantParams = new ArrayList<>();
        root.path("paths").path("/api/restaurants").path("get").path("parameters").forEach(p ->
                restaurantParams.add(p.path("name").asText()));
        assertThat(restaurantParams).contains("page", "size", "sortBy", "direction", "name");
    }

    @Test
    void swaggerUi_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
