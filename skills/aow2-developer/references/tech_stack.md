# Technology Stack — Detailed Decisions

## Java 21 (LTS)

### Why Java 21
- Latest LTS release with long-term support
- Virtual threads for server-side concurrency
- Record patterns and pattern matching for switch
- Sealed classes for exhaustive type hierarchies (Command pattern)
- String templates (preview) for cleaner SQL/JSON
- Sequenced collections for ordered data

### Key Features Used
- **Records**: All stat/data classes (`UnitStats`, `BuildingStats`, `Command`)
- **Sealed interfaces**: Command hierarchy, entity types
- **Pattern matching**: Combat resolution, state machine transitions
- **Virtual threads**: Spring Boot request handling, network I/O
- **Switch expressions**: Facing direction, unit type dispatch

## FXGL 21

### Why FXGL
- Purpose-built 2D game framework on JavaFX
- Built-in game loop, entity system, physics, input handling
- Active community and documentation
- Integrates with JavaFX for rich UI
- Supports tile-based rendering (isometric with custom renderer)

### Key Components Used
- `GameApplication` base class
- `EntityComponent` system for unit/building logic
- `Input` system for mouse/keyboard
- `GameState` for game variables
- `SpawnSymbol` / `EntityFactory` for entity creation
- Custom `SubScene` for map editor

### Limitations to Work Around
- No built-in isometric support → custom `IsometricRenderer`
- No built-in lockstep networking → custom `LockstepEngine`
- No built-in replay system → custom `ReplayRecorder`

## Spring Boot 3.x

### Why Spring Boot
- Industry standard for Java backend
- WebSocket support for real-time communication
- Spring Security for authentication
- Spring Data JPA for PostgreSQL access
- Flyway for database migrations
- Actuator for monitoring

### Server Architecture
```
┌─────────────────────────────────────────────┐
│              Spring Boot Server              │
├─────────────────────────────────────────────┤
│  REST API                                    │
│  ├── /api/auth        (login/register)      │
│  ├── /api/matchmaking (find opponent)        │
│  ├── /api/maps        (upload/download)      │
│  ├── /api/replays     (upload/download)      │
│  └── /api/leaderboard (rankings)             │
│                                              │
│  WebSocket                                   │
│  ├── /ws/lobby        (matchmaking events)   │
│  └── /ws/game         (P2P signaling)        │
│                                              │
│  Database (PostgreSQL)                       │
│  ├── players, sessions, match_results        │
│  ├── uploaded_maps, replays                   │
│  └── leaderboards                             │
└─────────────────────────────────────────────┘
```

### Note on Lockstep P2P
The Spring Boot server handles lobby, matchmaking, and signaling.
Once two players are matched, they establish a P2P connection
(using the server as a signaling/relay if direct P2P fails).
The server does NOT run the game simulation — clients do via lockstep.

## PostgreSQL 16

### Schema Overview
```sql
-- Core tables
CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    elo_rating INT DEFAULT 1000,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE match_results (
    id BIGSERIAL PRIMARY KEY,
    player1_id BIGINT REFERENCES players(id),
    player2_id BIGINT REFERENCES players(id),
    winner_id BIGINT REFERENCES players(id),
    map_name VARCHAR(64),
    duration_seconds INT,
    replay_file_path VARCHAR(255),
    played_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE uploaded_maps (
    id BIGSERIAL PRIMARY KEY,
    uploader_id BIGINT REFERENCES players(id),
    name VARCHAR(64) NOT NULL,
    description TEXT,
    map_data JSONB NOT NULL,
    download_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
```

## Netty 4.x

### Why Netty
- High-performance async I/O framework
- Used for game client P2P networking
- Custom protocol codec for lockstep commands
- Efficient byte buffer management

### Network Protocol (Modern Redesign)
The original game used TCP with XOR encryption. Our recreation uses:
- **Transport**: TCP (Netty) for reliable command delivery
- **Framing**: Length-prefixed messages (4-byte header)
- **Serialization**: Protocol Buffers for command serialization
- **Encryption**: TLS 1.3 for transport security
- **Signaling**: WebSocket through Spring Boot for NAT traversal

## LuaJ 3.x

### Why LuaJ
- Pure Java Lua interpreter (no native dependencies)
- Easy embedding in Java application
- Good performance for game scripting
- Used for campaign mission scripts and mod scripting

### Exposed API
```lua
-- Game API available to Lua scripts
aow2.spawnUnit(faction, unitType, x, y)
aow2.destroyUnit(unitId)
aow2.getObjective(name)
aow2.setObjective(name, status)
aow2.showMessage(text)
aow2.setTimer(seconds, callback)
aow2.onUnitKilled(callback)
aow2.onAreaEntered(x, y, radius, callback)
```

## Docker Compose

### Services
```yaml
services:
  server:
    build: { context: ., dockerfile: docker/server.Dockerfile }
    ports: ["8080:8080", "8443:8443"]
    depends_on: [db, redis]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/aow2
      SPRING_REDIS_HOST: redis

  db:
    image: postgres:16
    volumes: [pgdata:/var/lib/postgresql/data]
    environment:
      POSTGRES_DB: aow2
      POSTGRES_PASSWORD: ${DB_PASSWORD}

  redis:
    image: redis:7-alpine
    # Used for session cache, matchmaking queue

volumes:
  pgdata:
```

## Dependency Versions

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Java | 21 LTS | Language |
| FXGL | 21 | Game framework |
| JavaFX | 21 | UI framework |
| Spring Boot | 3.3.x | Backend framework |
| PostgreSQL | 16 | Database |
| Netty | 4.1.x | Network I/O |
| LuaJ | 3.0.3 | Scripting engine |
| JUnit | 5.10.x | Testing |
| Mockito | 5.x | Mocking |
| Jackson | 2.17.x | JSON serialization |
| Protocol Buffers | 4.x | Network serialization |
| Flyway | 10.x | DB migrations |
| SLF4J + Logback | 2.x / 1.5 | Logging |
| Gradle | 8.x | Build system |
| Docker | 24+ | Containerization |
| Redis | 7.x | Caching/sessions |
