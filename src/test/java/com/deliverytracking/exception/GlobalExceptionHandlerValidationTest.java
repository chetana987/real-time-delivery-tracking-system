package com.deliverytracking.exception;

import com.deliverytracking.dto.ApiError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerValidationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void constraintViolation_mapsTo400WithFieldErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn("deliveryAddress");
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn("must not be blank");

        ConstraintViolationException ex =
                new ConstraintViolationException("Validation failed", Set.of(violation));

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(ex, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getMessage()).isEqualTo("Request contains invalid fields");
        assertThat(response.getBody().getFieldErrors())
                .containsExactly(entry("deliveryAddress", "must not be blank"));
    }

    @Test
    void genericException_returns500WithoutLeakingDetails() {
        ResponseEntity<ApiError> response = handler.handleGeneric(
                new RuntimeException("internal secret details"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
        assertThat(response.getBody().getFieldErrors()).isNull();
    }

    @Test
    void emailAlreadyExists_mapsTo409WithStructuredError() {
        ResponseEntity<ApiError> response = handler.handleEmailAlreadyExists(
                new EmailAlreadyExistsException("Email already registered: dup@test.com"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo("Email already registered: dup@test.com");
        assertThat(response.getBody().getPath()).isEqualTo("/api/orders");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/orders");
    }
}
