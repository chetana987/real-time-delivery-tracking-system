package com.deliverytracking.service;

import com.deliverytracking.dto.RegisterRequest;
import com.deliverytracking.entity.Role;
import com.deliverytracking.entity.User;
import com.deliverytracking.exception.EmailAlreadyExistsException;
import com.deliverytracking.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest requestFor(String email) {
        return RegisterRequest.builder()
                .name("Alice")
                .email(email)
                .password("password123")
                .role(Role.CUSTOMER)
                .build();
    }

    private DataIntegrityViolationException emailUniqueViolation(String email) {
        SQLException sql = new SQLException(
                "Duplicate entry '" + email + "' for key 'users.idx_users_email'", "23000", 1062);
        ConstraintViolationException hibernate =
                new ConstraintViolationException("could not execute statement", sql, "idx_users_email");
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }

    private DataIntegrityViolationException notNullViolation() {
        SQLException sql = new SQLException("Column 'name' cannot be null", "23000", 1048);
        ConstraintViolationException hibernate =
                new ConstraintViolationException("could not execute statement", sql, "users.PRIMARY");
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }

    @Test
    void register_validRequest_hashesPasswordAndReturnsToken() {
        String email = "alice@test.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        var response = authService.register(requestFor(email));

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void register_emailUniqueConstraintViolation_mapsToEmailAlreadyExists() {
        String email = "dup@test.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(emailUniqueViolation(email));

        assertThatThrownBy(() -> authService.register(requestFor(email)))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already registered: " + email);
    }

    @Test
    void register_unrelatedDataIntegrityViolation_isNotSwallowedAsConflict() {
        String email = "other@test.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(notNullViolation());

        assertThatThrownBy(() -> authService.register(requestFor(email)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void register_plainDuplicateEntryOnUnknownConstraint_isNotSwallowedAsConflict() {
        String email = "unknown@test.com";
        SQLException sql = new SQLException("Duplicate entry 'x' for key 'orders.PRIMARY'", "23000", 1062);
        ConstraintViolationException hibernate =
                new ConstraintViolationException("could not execute statement", sql, "orders.PRIMARY");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement", hibernate);

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(ex);

        assertThatThrownBy(() -> authService.register(requestFor(email)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void register_fastPath_keepsRejectingAlreadyRegisteredEmail() {
        String email = "taken@test.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(requestFor(email)))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already registered: " + email);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}