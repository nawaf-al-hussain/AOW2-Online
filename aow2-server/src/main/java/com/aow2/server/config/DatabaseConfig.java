package com.aow2.server.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Database configuration for the AOW2 multiplayer server.
 * Uses PostgreSQL in production and H2 in-memory for testing.
 * H2 test datasource is auto-configured by Spring Boot via application.yml.
 * REF: multiplayer_architecture.md - Persistent storage for player data and match results
 */
@Configuration
@EntityScan(basePackages = "com.aow2.server.model")
@EnableJpaRepositories(basePackages = "com.aow2.server.repository")
public class DatabaseConfig {
}
