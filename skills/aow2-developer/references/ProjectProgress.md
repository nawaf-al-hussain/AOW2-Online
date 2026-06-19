# Project Progress — Art of War 2: Online

> This file tracks everything that has been implemented and what remains.
> Updated after each development session.

## Status: NOT STARTED

## Phase 0: Project Scaffolding
- [ ] Gradle multi-module project initialized
- [ ] Java 21 + FXGL + Spring Boot dependencies configured
- [ ] .gitignore, README.md, LICENSE created
- [ ] Basic FXGL application opens a window
- [ ] JUnit 5 + Mockito configured
- [ ] GitHub Actions CI set up
- [ ] Initial commit pushed to GitHub

## Phase 1: Core Engine & Data Model
- [ ] Fixed-timestep game loop (60 TPS)
- [ ] Entity model (Unit, Building, Projectile, Mine)
- [ ] Faction enum
- [ ] Terrain/Tile system
- [ ] Map loading from JSON
- [ ] GameState class
- [ ] EventBus
- [ ] Data model tests

## Phase 2: Rendering & UI Framework
- [ ] Isometric tile renderer
- [ ] Sprite loading and rendering
- [ ] Camera system
- [ ] Unit rendering (8-directional)
- [ ] Building rendering
- [ ] Minimap
- [ ] HUD
- [ ] Mouse selection
- [ ] Right-click commands
- [ ] Production UI
- [ ] Fog of war rendering
- [ ] Health bars
- [ ] Main menu

## Phase 3: Movement & Pathfinding
- [ ] A* pathfinding
- [ ] Terrain passability
- [ ] Collision avoidance
- [ ] Formation movement
- [ ] Stuck detection
- [ ] Garrison movement
- [ ] Large unit collision

## Phase 4: Combat System
- [ ] Damage formula
- [ ] Armor calculation
- [ ] Projectile system
- [ ] Splash damage
- [ ] Attack cooldowns
- [ ] Death animations
- [ ] Bunker garrison attacks
- [ ] Defensive building attacks

## Phase 5: Economy & Buildings
- [ ] Auto-resource generation
- [ ] Building placement system
- [ ] Generator power system
- [ ] Building construction
- [ ] Production queues
- [ ] Technology Centre research
- [ ] Building destruction

## Phase 6: AI System
- [ ] AI decision system
- [ ] Difficulty levels
- [ ] Base building logic
- [ ] Unit composition
- [ ] Retreat behavior
- [ ] Fog-of-war awareness

## Phase 7: Campaign System
- [ ] Mission scripting (Lua)
- [ ] Episode 1 missions (7)
- [ ] Episode 2 missions (7)
- [ ] Custom missions (15)
- [ ] Save/load system
- [ ] Mission briefing screen
- [ ] Victory/defeat conditions

## Phase 8: Multiplayer
- [ ] Spring Boot server
- [ ] Player authentication
- [ ] Matchmaking system
- [ ] Lockstep P2P networking
- [ ] Desync detection
- [ ] Chat system
- [ ] ELO ranking

## Phase 9: Map Builder
- [ ] Map editor UI
- [ ] Map save/load
- [ ] Map validation
- [ ] Map testing
- [ ] Map sharing

## Phase 10: Modding System
- [ ] Mod loader
- [ ] Data-driven overrides
- [ ] Lua scripting
- [ ] Mod UI

## Phase 11: Replay System
- [ ] Command recording
- [ ] Replay file format
- [ ] Replay playback
- [ ] Seeking
- [ ] Replay sharing

## Phase 12: Web Client
- [ ] Web client implementation
- [ ] Touch controls
- [ ] Feature parity check

## Phase 13: Polish & Optimization
- [ ] Performance optimization
- [ ] Memory optimization
- [ ] Sound and music
- [ ] Tutorial system
- [ ] Accessibility
- [ ] Localization
- [ ] Docker Compose setup

## Phase 14: Final Testing & Release
- [ ] Full regression pass
- [ ] Multiplayer stress test
- [ ] Campaign completion test
- [ ] Mod compatibility test
- [ ] Replay integrity test
- [ ] Development report
- [ ] GitHub push
- [ ] Release tag

---

## Assumptions Log

> Track all assumptions made during development.

| Date | Phase | Assumption | Reason | Status |
|------|-------|-----------|--------|--------|
| - | - | - | - | - |

## Known Issues

> Track known bugs and issues.

| Date | Phase | Issue | Severity | Status |
|------|-------|-------|----------|--------|
| - | - | - | - | - |
