package com.aow2.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the AOW2 multiplayer server.
 * Handles player authentication, matchmaking, map sharing, and replay storage.
 */
@SpringBootApplication
public class AOW2ServerApp {

    public static void main(String[] args) {
        SpringApplication.run(AOW2ServerApp.class, args);
    }
}
