package com.deliverytracking.integration;

import com.deliverytracking.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlingIntegrationTest extends AbstractIntegrationTest {

    private String customerToken;
    private String partnerToken;

    @BeforeEach
    void setUp() throws Exception {
        customerToken = register(uniqueEmail("exception-customer"), Role.CUSTOMER);
        partnerToken = register(uniqueEmail("exception-partner"), Role.DELIVERY_PARTNER);
    }

    @Test
    void unknownResource_withValidToken_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/999/unknown")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void missingOrder_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found: 999999"));
    }

    @Test
    void missingRestaurant_returns404() throws Exception {
        mockMvc.perform(get("/api/restaurants/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Restaurant not found: 999999"));
    }

    @Test
    void malformedJsonBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void invalidStatusEnum_returns400() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void invalidPathVariableType_returns400() throws Exception {
        mockMvc.perform(patch("/api/orders/abc/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + partnerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid path variable or parameter"));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerOnlyEndpoint_withCustomerRole_returns403() throws Exception {
        mockMvc.perform(patch("/api/orders/1/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationFailure_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"bad\",\"password\":\"123\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Request contains invalid fields"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void invalidPathVariableValue_returnsValidationErrors() throws Exception {
        mockMvc.perform(get("/api/restaurants/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Request contains invalid fields"))
                .andExpect(jsonPath("$.fieldErrors.id").exists());
    }
}
