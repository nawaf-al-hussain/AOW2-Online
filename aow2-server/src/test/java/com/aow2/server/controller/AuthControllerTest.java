package com.aow2.server.controller;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import com.aow2.server.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the AuthController REST endpoints.
 * Verifies registration, login, and current user info retrieval.
 * REF: protocol_specification.md - Authentication flow (Section 6)
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private com.aow2.server.service.AuthService authService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService);
    }

    @Test
    @DisplayName("Register returns 201 with player data")
    void registerSuccess() {
        Map<String, Object> serviceResult = Map.of(
                "id", 1L, "username", "testuser", "token", "jwt-token", "eloRating", 1000
        );
        when(authService.register("testuser", "password123")).thenReturn(serviceResult);

        ResponseEntity<Map<String, Object>> response = authController.register(
                Map.of("username", "testuser", "password", "password123"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("testuser", response.getBody().get("username"));
    }

    @Test
    @DisplayName("Register with invalid input returns 400")
    void registerInvalidInput() {
        when(authService.register("ab", "short"))
                .thenThrow(new IllegalArgumentException("Username must be 3-32 characters"));

        ResponseEntity<Map<String, Object>> response = authController.register(
                Map.of("username", "ab", "password", "short"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Login returns 200 with JWT token")
    void loginSuccess() {
        Map<String, Object> serviceResult = Map.of(
                "id", 1L, "username", "testuser", "token", "jwt-token", "eloRating", 1000
        );
        when(authService.login("testuser", "password123")).thenReturn(serviceResult);

        ResponseEntity<Map<String, Object>> response = authController.login(
                Map.of("username", "testuser", "password", "password123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("jwt-token", response.getBody().get("token"));
    }

    @Test
    @DisplayName("Login with invalid credentials returns 401")
    void loginInvalidCredentials() {
        when(authService.login("testuser", "wrongpass"))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));

        ResponseEntity<Map<String, Object>> response = authController.login(
                Map.of("username", "testuser", "password", "wrongpass"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Me endpoint returns player info")
    void meSuccess() {
        var authentication = org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        when(authentication.getPrincipal()).thenReturn(1L);

        Map<String, Object> serviceResult = Map.of(
                "id", 1L, "username", "testuser", "eloRating", 1000
        );
        when(authService.getCurrentPlayer(1L)).thenReturn(serviceResult);

        ResponseEntity<Map<String, Object>> response = authController.me(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().get("id"));
    }
}
