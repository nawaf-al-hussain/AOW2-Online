package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import com.aow2.server.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for the AuthService authentication business logic.
 * Verifies registration, login, and current user retrieval.
 * REF: protocol_specification.md - Authentication flow (Section 6)
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtUtil jwtUtil;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
                3600000L
        );
        authService = new AuthService(playerRepository, passwordEncoder, jwtUtil);
    }

    @Test
    @DisplayName("Register new player successfully")
    void registerSuccess() {
        when(playerRepository.existsByUsername("newplayer")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> {
            Player p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        Map<String, Object> result = authService.register("newplayer", "password123");
        assertNotNull(result.get("token"));
        assertEquals("newplayer", result.get("username"));
        assertEquals(1000, result.get("eloRating"));
    }

    @Test
    @DisplayName("Register with duplicate username fails")
    void registerDuplicateUsername() {
        when(playerRepository.existsByUsername("existing")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("existing", "password123"));
    }

    @Test
    @DisplayName("Register with short username fails")
    void registerShortUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("ab", "password123"));
    }

    @Test
    @DisplayName("Register with short password fails")
    void registerShortPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("validuser", "12345"));
    }

    @Test
    @DisplayName("Login with valid credentials succeeds")
    void loginSuccess() {
        Player player = new Player("testuser", "$2a$10$hashed");
        player.setId(1L);
        when(playerRepository.findByUsername("testuser")).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("password123", "$2a$10$hashed")).thenReturn(true);

        Map<String, Object> result = authService.login("testuser", "password123");
        assertNotNull(result.get("token"));
        assertEquals("testuser", result.get("username"));
    }

    @Test
    @DisplayName("Login with wrong password fails")
    void loginWrongPassword() {
        Player player = new Player("testuser", "$2a$10$hashed");
        when(playerRepository.findByUsername("testuser")).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("wrongpass", "$2a$10$hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.login("testuser", "wrongpass"));
    }

    @Test
    @DisplayName("Login with non-existent user fails")
    void loginNonExistentUser() {
        when(playerRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> authService.login("nobody", "password123"));
    }

    @Test
    @DisplayName("Get current player returns profile data")
    void getCurrentPlayer() {
        Player player = new Player("testuser", "$2a$10$hashed");
        player.setId(1L);
        player.setEloRating(1200);
        player.setGamesPlayed(10);
        player.setGamesWon(7);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        Map<String, Object> result = authService.getCurrentPlayer(1L);
        assertEquals(1L, result.get("id"));
        assertEquals("testuser", result.get("username"));
        assertEquals(1200, result.get("eloRating"));
        assertEquals(10, result.get("gamesPlayed"));
        assertEquals(7, result.get("gamesWon"));
    }

    @Test
    @DisplayName("Get non-existent player throws")
    void getNonExistentPlayer() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> authService.getCurrentPlayer(999L));
    }
}
