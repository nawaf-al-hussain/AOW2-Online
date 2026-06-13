package com.aow2.server.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Database configuration for the AOW2 multiplayer server.
 * Uses PostgreSQL in production and H2 in-memory for testing.
 * REF: multiplayer_architecture.md - Persistent storage for player data and match results
 */
@Configuration
@EntityScan(basePackages = "com.aow2.server.model")
@EnableJpaRepositories(basePackages = "com.aow2.server.repository")
public class DatabaseConfig {

    /**
     * Provides an embedded H2 datasource for testing.
     * Activated only when the "test" profile is active.
     *
     * @return the in-memory H2 datasource
     */
    @Bean
    @Profile("test")
    public DataSource testDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-h2.sql")
                .build();
    }
}
