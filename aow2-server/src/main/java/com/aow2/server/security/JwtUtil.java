package com.aow2.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Utility class for JWT token generation, validation, and parsing.
 * Provides stateless authentication for the multiplayer server.
 * REF: protocol_specification.md - Authentication flow via session key
 * REF: multiplayer_architecture.md - Session key rotation per request
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Constructs the JwtUtil with configuration from application properties.
     *
     * @param secret         the HMAC-SHA256 secret key string (must be at least 256 bits)
     * @param expirationMs   token expiration time in milliseconds
     */
    public JwtUtil(
            @Value("${aow2.jwt.secret}") String secret,
            @Value("${aow2.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        // FIX (C-NEW-7): Fail fast if using the default dev secret in a non-dev environment.
        // The default secret is committed to source control and must NEVER be used in production.
        String devSecret = "aow2-dev-only-secret-key-that-is-at-least-32-bytes-long-for-hmac";
        if (secret.equals(devSecret)) {
            String env = System.getenv("AOW2_JWT_SECRET");
            if (env == null || env.isBlank()) {
                throw new IllegalStateException(
                    "JWT secret is the default dev value. Set AOW2_JWT_SECRET environment variable " +
                    "to a cryptographically random secret (min 32 bytes) before deploying.");
            }
            log.warn("Using default dev JWT secret — this is acceptable ONLY for local development");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a JWT token for the given player.
     * The token contains the player ID as the subject and the username as a claim.
     *
     * @param playerId the player's database ID
     * @param username the player's username
     * @return the signed JWT token string
     */
    public String generateToken(Long playerId, String username) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(playerId.toString())
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the given JWT token and extracts its claims.
     *
     * @param token the JWT token string
     * @return the parsed Claims if valid, or null if the token is invalid or expired
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the player ID from a valid JWT token.
     *
     * @param token the JWT token string
     * @return the player ID, or null if the token is invalid
     */
    public Long getPlayerId(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extracts the username from a valid JWT token.
     *
     * @param token the JWT token string
     * @return the username, or null if the token is invalid
     */
    public String getUsername(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    /**
     * Checks whether the given token is expired.
     *
     * @param token the JWT token string
     * @return true if the token is expired or invalid
     */
    public boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        return claims == null || claims.getExpiration().before(Date.from(Instant.now()));
    }
}
