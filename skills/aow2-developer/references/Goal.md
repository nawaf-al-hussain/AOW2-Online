# Art of War 2: Online — End Goal

## Vision

Recreate "Art of War 2: Online" as a modern, feature-complete RTS game using Java 21, JavaFX/FXGL,
and Spring Boot. The recreation must be faithful to the original game mechanics (combat, economy,
AI, pathfinding, unit stats, building stats, tech tree, campaigns) while adding modern features:
online multiplayer with matchmaking, a map builder, modding support, and a replay system.

## Must-Have Features (Non-Negotiable)

### Faithful Recreation
- [ ] Both factions: Global Confederation and Resistance
- [ ] All 14 unit types (7 per faction) with exact stats from RE data
- [ ] All 16 building types (8 per faction) with exact stats from RE data
- [ ] All 48 research effects (16 base techs + 32 asymmetric effects)
- [ ] Complete combat system with original formulas (damage, armor, projectiles)
- [ ] Economy system with auto-resource generation (no manual mining)
- [ ] Command Centre placement radius system
- [ ] Generator power system (buildings stop without power)
- [ ] Fog of war system
- [ ] Garrison system (bunkers, infantry in buildings)
- [ ] All 14 campaign missions (7 Episode 1 + 7 Episode 2)
- [ ] 15 custom missions (Global Confederation)
- [ ] AI opponent with documented behavior patterns
- [ ] A* pathfinding on grid maps
- [ ] Projectile system with collision and splash damage
- [ ] Original game's isometric top-down perspective

### New Features
- [ ] Online multiplayer via Spring Boot lobby/matchmaking server
- [ ] Lockstep P2P multiplayer for gameplay
- [ ] Integrated map builder/editor
- [ ] Data-driven modding (JSON/YAML + Lua scripting)
- [ ] Full replay system (record, seek, share)
- [ ] Desktop client (FXGL) + Web client
- [ ] Docker Compose deployment for server
- [ ] Player accounts, leaderboards, match history
- [ ] Hotkey configuration
- [ ] Modern UI with scalable resolution (no s0/s1/s2 variants)

## Quality Standards

- Test coverage: 80%+ on core systems
- No TODO placeholders in committed code
- All game data traceable to RE documentation
- Every assumption marked with `// ASSUMPTION:` comments
- Clean Gradle build with zero warnings
- All multiplayer sessions synced within 1 frame tolerance
- Replay files render identically across playbacks
- Modded content cannot crash the base game

## Platform Targets

| Platform | Priority | Framework |
|----------|----------|-----------|
| Desktop (Win/Mac/Linux) | P0 | FXGL + JavaFX |
| Web Browser | P1 | GraalVM WASM or GWT-compiled client |
| Mobile | P2 | Future consideration |

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 (LTS) |
| Game Engine | FXGL | 21 (latest) |
| UI | JavaFX | 21 |
| Backend | Spring Boot | 3.x |
| Database | PostgreSQL | 16 |
| Build | Gradle | 8.x (Kotlin DSL) |
| Scripting | LuaJ | 3.x |
| Testing | JUnit 5 + Mockito | 5.x / 5.x |
| Networking | Netty | 4.x |
| Serialization | Jackson | 2.x |
| Logging | SLF4J + Logback | 2.x / 1.5 |
| Containerization | Docker + Compose | Latest |

## Definition of Done

The project is "done" when:
1. All campaign missions are playable from start to finish
2. Multiplayer matches complete without desync
3. Map editor can create, save, and play custom maps
4. At least one mod can be loaded and played
5. Replays record and play back correctly
6. All tests pass with 80%+ coverage
7. The game builds and runs from `./gradlew run`
8. Everything is pushed to GitHub
9. A comprehensive development report is generated
