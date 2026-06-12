# Art of War 2: Online

A modern recreation of the classic mobile RTS game "Art of War 2: Online" using Java 21, JavaFX/FXGL, and Spring Boot.

## Features
- Faithful recreation of all original game mechanics
- Two factions: Global Confederation and Resistance
- 14 unit types, 16 building types, 48 research effects
- Online multiplayer with matchmaking
- Integrated map builder
- Data-driven modding support (JSON/YAML + Lua)
- Full replay system

## Tech Stack
| Component | Technology |
|-----------|-----------|
| Language | Java 21 (LTS) |
| Game Engine | FXGL 21 |
| UI | JavaFX 21 |
| Backend | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Build | Gradle 8.7 (Kotlin DSL) |

## Building

```bash
./gradlew build
```

## Running the Client

```bash
./gradlew :aow2-client:run
```

## Running the Server

```bash
./gradlew :aow2-server:bootRun
```

## Testing

```bash
./gradlew test
```

## Project Structure

- `aow2-common` - Shared data models, events, utilities
- `aow2-core` - Game engine, combat, economy, AI, pathfinding
- `aow2-client` - FXGL game client with JavaFX UI
- `aow2-server` - Spring Boot multiplayer backend
- `aow2-modding` - Mod loader and Lua scripting engine
