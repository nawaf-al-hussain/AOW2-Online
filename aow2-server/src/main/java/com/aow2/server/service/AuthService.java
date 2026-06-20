package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import com.aow2.server.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Business logic for player authentication: registration, login, JWT generation.
 * Handles account creation with username uniqueness validation and bcrypt password hashing.
 * REF: protocol_specification.md - Authentication flow, player ID validation (S() - 12321)
 * REF: multiplayer_architecture.md - Session key for XOR stream cipher
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Constructs the AuthService with required dependencies.
     *
     * @param playerRepository repository for player persistence
     * @param passwordEncoder  bcrypt encoder for password hashing
     * @param jwtUtil          utility for JWT token operations
     */
    public AuthService(PlayerRepository playerRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers a new player account.
     * Validates username uniqueness and hashes the password with bcrypt.
     *
     * @param username the desired username (3-32 characters)
     * @param password the plain-text password (6+ characters)
     * @return a map containing the player ID, username, and JWT token
     * @throws IllegalArgumentException if the username is taken or parameters are invalid
     */
    @Transactional
    public Map<String, Object> register(String username, String password) {
        if (username == null || !username.matches("[a-zA-Z0-9_-]{3,32}")) {
            throw new IllegalArgumentException("Username must be 3-32 alphanumeric characters, underscores, or hyphens");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        // FIX (H-NEW-6): Reject excessively long passwords to prevent bcrypt DoS.
        // bcrypt processes the first 72 bytes, so anything beyond that is ignored.
        // But the CPU cost is proportional to length for the initial processing.
        if (password.length() > 128) {
            throw new IllegalArgumentException("Password must not exceed 128 characters");
        }
        if (playerRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }

        String passwordHash = passwordEncoder.encode(password);
        Player player = new Player(username, passwordHash);
        player = playerRepository.save(player);

        String token = jwtUtil.generateToken(player.getId(), player.getUsername());
        log.info("Player registered: {} (ID: {})", username, player.getId());

        return Map.of(
                "id", player.getId(),
                "username", player.getUsername(),
                "token", token,
                "eloRating", player.getEloRating()
        );
    }

    /**
     * Authenticates a player with username and password.
     * Validates the credentials and returns a JWT token on success.
     *
     * @param username the player's username
     * @param password the plain-text password
     * @return a map containing the player ID, username, and JWT token
     * @throws IllegalArgumentException if credentials are invalid
     */
    @Transactional(readOnly = true)
    public Map<String, Object> login(String username, String password) {
        Player player = playerRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, player.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(player.getId(), player.getUsername());
        log.info("Player logged in: {} (ID: {})", username, player.getId());

        return Map.of(
                "id", player.getId(),
                "username", player.getUsername(),
                "token", token,
                "eloRating", player.getEloRating(),
                "gamesPlayed", player.getGamesPlayed(),
                "gamesWon", player.getGamesWon()
        );
    }

    /**
     * Retrieves the current player's information from a JWT token.
     *
     * @param playerId the player ID extracted from the JWT
     * @return a map containing the player's profile data
     * @throws IllegalArgumentException if the player is not found
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentPlayer(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        return Map.of(
                "id", player.getId(),
                "username", player.getUsername(),
                "eloRating", player.getEloRating(),
                "gamesPlayed", player.getGamesPlayed(),
                "gamesWon", player.getGamesWon(),
                "createdAt", player.getCreatedAt().toString()
        );
    }
}
