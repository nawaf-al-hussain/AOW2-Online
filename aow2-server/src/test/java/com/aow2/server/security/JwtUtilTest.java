package com.aow2.server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the JwtUtil JWT token management utility.
 * Verifies token generation, validation, and claim extraction.
 * REF: protocol_specification.md - Authentication flow
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
                3600000L // 1 hour
        );
    }

    @Test
    @DisplayName("Generate and validate token successfully")
    void generateAndValidate() {
        String token = jwtUtil.generateToken(1L, "testplayer");
        assertNotNull(token);

        var claims = jwtUtil.validateToken(token);
        assertNotNull(claims);
        assertEquals("1", claims.getSubject());
        assertEquals("testplayer", claims.get("username", String.class));
    }

    @Test
    @DisplayName("Extract player ID from token")
    void extractPlayerId() {
        String token = jwtUtil.generateToken(42L, "player42");
        Long playerId = jwtUtil.getPlayerId(token);
        assertEquals(42L, playerId);
    }

    @Test
    @DisplayName("Extract username from token")
    void extractUsername() {
        String token = jwtUtil.generateToken(1L, "myuser");
        String username = jwtUtil.getUsername(token);
        assertEquals("myuser", username);
    }

    @Test
    @DisplayName("Invalid token returns null claims")
    void invalidTokenReturnsNull() {
        assertNull(jwtUtil.validateToken("invalid.token.here"));
        assertNull(jwtUtil.getPlayerId("invalid.token.here"));
        assertNull(jwtUtil.getUsername("invalid.token.here"));
    }

    @Test
    @DisplayName("Expired token is detected")
    void expiredTokenDetected() {
        JwtUtil shortLived = new JwtUtil(
                "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm",
                1L // 1ms expiration
        );
        String token = shortLived.generateToken(1L, "test");

        // Wait for token to expire
        try { Thread.sleep(50); } catch (InterruptedException e) { /* ignore */ }

        assertTrue(shortLived.isTokenExpired(token));
    }

    @Test
    @DisplayName("Valid token is not expired")
    void validTokenNotExpired() {
        String token = jwtUtil.generateToken(1L, "test");
        assertFalse(jwtUtil.isTokenExpired(token));
    }
}
