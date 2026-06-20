# Development Phases

## Phase 0: Project Scaffolding (Foundation)
**Goal**: Runnable project skeleton with build, CI, and basic FXGL window.

### Tasks
- [ ] Initialize Gradle multi-module project (Kotlin DSL)
  - `aow2-client` — FXGL game client
  - `aow2-server` — Spring Boot backend
  - `aow2-core` — Shared game logic (combat, economy, pathfinding)
  - `aow2-modding` — Mod loader and scripting engine
  - `aow2-common` — Shared data models, events, utilities
- [ ] Configure Java 21, FXGL, Spring Boot dependencies
- [ ] Set up `.gitignore`, `README.md`, `LICENSE`
- [ ] Create basic FXGL application class that opens a window
- [ ] Configure JUnit 5 + Mockito test framework
- [ ] Set up GitHub Actions CI (build + test)
- [ ] Push initial commit to GitHub
- [ ] Create `dev` branch, protect `main`

**Definition of Done**: `./gradlew build` succeeds, `./gradlew run` opens FXGL window.

---

## Phase 1: Core Engine & Data Model
**Goal**: Game loop, entity system, and all data loaded from JSON specs.

### Tasks
- [ ] Implement fixed-timestep game loop (60 TPS) in `aow2-core`
- [ ] Create entity model (Unit, Building, Projectile, Mine)
  - Use Java records for immutable data, mutable classes for game state
  - All stats loaded from `complete_unit_stats.json` and `complete_building_stats.json`
- [ ] Create Faction enum (CONFEDERATION, RESISTANCE, NEUTRAL)
- [ ] Create Terrain/Tile system with map grid
- [ ] Implement map loading from JSON (port the 193 map records)
- [ ] Create GameState class that holds all entities, players, and world state
- [ ] Implement EventBus for game events (unit killed, building destroyed, etc.)
- [ ] Write comprehensive unit tests for all data models
- [ ] Validate all loaded stats against RE documentation

**Definition of Done**: All unit/building stats loadable, game loop ticks, map renders as colored grid.

---

## Phase 2: Rendering & UI Framework
**Goal**: Isometric renderer with sprites, camera, selection, and HUD.

### Tasks
- [ ] Implement isometric tile renderer with FXGL
- [ ] Load and render original game sprites (from `assets_processed/`)
- [ ] Implement camera system (pan, zoom, bounds)
- [ ] Create unit rendering with facing directions (8-directional)
- [ ] Create building rendering with construction states
- [ ] Implement minimap
- [ ] Create HUD (resources, selection info, production queues)
- [ ] Implement mouse selection (box select, click select)
- [ ] Implement right-click command (move, attack, garrison)
- [ ] Create production UI (build menu, queue display)
- [ ] Implement fog of war rendering
- [ ] Add health bars and damage indicators
- [ ] Create main menu screen
- [ ] Write UI tests (headless verification of layout)

**Definition of Done**: Can view a map, select units, see HUD, click to move units visually.

---

## Phase 3: Movement & Pathfinding
**Goal**: Units move correctly on the grid with A* pathfinding.

### Tasks
- [ ] Implement A* pathfinding on the grid (reference `pathfinding.md`)
- [ ] Handle terrain passability (water, mountains, buildings block)
- [ ] Implement collision avoidance between units
- [ ] Implement formation movement (group move with spacing)
- [ ] Add stuck detection and re-routing (from original's stuckCounter)
- [ ] Implement garrison enter/exit movement
- [ ] Handle large unit collision (2-cell units like Fortress)
- [ ] Write pathfinding benchmark tests
- [ ] Verify pathfinding matches original game behavior

**Definition of Done**: 50 units can pathfind across a complex map without deadlocks.

---

## Phase 4: Combat System
**Goal**: Complete combat system matching original formulas exactly.

### Tasks
- [ ] Implement damage formula (reference `combat_formulas.md`)
  - `damage = weaponDamage * (10 - targetArmour) / 10`
  - Clamped: `max(min(damage, weaponDamage - armour), 1)`
- [ ] Implement armor calculation with research bonuses
- [ ] Implement projectile system (bullets, missiles, artillery)
  - Projectile types from RE: bullet, rocket, artillery shell, flame
- [ ] Implement splash damage for artillery
- [ ] Implement attack cooldowns per weapon type
- [ ] Implement death animation system (infantry vs machinery)
- [ ] Implement bunker garrison attack logic
- [ ] Implement defensive building attacks (bunker, rocket tower)
- [ ] Write combat simulation tests
  - Test: Infantry vs Infantry matches expected damage
  - Test: Zeus tank vs Infantry matches expected TTK
  - Test: Artillery splash damage area
  - Test: Armor upgrades reduce damage correctly

**Definition of Done**: Combat simulation produces identical results to documented formulas.

---

## Phase 5: Economy & Buildings
**Goal**: Complete economy and building system.

### Tasks
- [ ] Implement auto-resource generation (credits per tick)
  - Base rate from Command Centre
  - Diminishing returns: each additional CC gives 30% less
- [ ] Implement building placement system
  - Must be within Command Centre radius
  - Collision checks with terrain and other buildings
  - Power connection to Generator
- [ ] Implement Generator power system
  - Power radius, power drain
  - Buildings stop functioning without power
- [ ] Implement building construction (build time, progress)
- [ ] Implement production queues (Infantry Centre, Machine Factory)
  - Sequential processing, cost deduction
  - Research-gated units (Snipers, Flame Assault, etc.)
- [ ] Implement Technology Centre research system
  - One research at a time
  - All 16 base techs + 32 asymmetric effects from RE data
- [ ] Implement building destruction and salvage
- [ ] Write economy simulation tests
  - Test: Resource generation rates match spec
  - Test: Power system shuts down buildings correctly
  - Test: Production times match RE data
  - Test: Research tree unlocks correct units

**Definition of Done**: Can build a base, produce units, research tech, and manage economy.

---

## Phase 6: AI System
**Goal**: Competent AI opponent matching original behavior.

### Tasks
- [ ] Implement AI decision system (reference `ai_analysis.md`)
  - Economy management (build order, resource allocation)
  - Military decisions (attack, defend, retreat, flank)
  - Technology research priorities
- [ ] Implement AI difficulty levels (Easy, Normal, Hard)
- [ ] Implement AI base building logic
- [ ] Implement AI unit composition and attack timing
- [ ] Implement AI retreat and regroup behavior
- [ ] Implement AI fog-of-war awareness
- [ ] Write AI behavior tests
  - Test: AI builds base in logical order
  - Test: AI attacks when military advantage is detected
  - Test: AI retreats when outnumbered

**Definition of Done**: AI can play a full game against itself with reasonable strategy.

---

## Phase 7: Campaign System
**Goal**: All campaign missions playable.

### Tasks
- [ ] Implement mission scripting system (Lua)
  - Objectives (destroy, defend, escort, timed)
  - Triggers (area entry, unit count, time elapsed)
  - Dialog/cutscene system
- [ ] Port all 14 campaign missions (7 Ep1 + 7 Ep2)
  - Mission 1-7: Global Confederation (Ep1)
  - Mission 1-7: Liberation of Peru (Ep2)
- [ ] Port 15 custom missions (Global Confederation)
- [ ] Implement save/load system (3 save slots like original)
- [ ] Implement mission briefing screen
- [ ] Implement mission victory/defeat conditions
- [ ] Write campaign integration tests
  - Test: Each mission loads with correct map and objectives
  - Test: Victory condition triggers correctly
  - Test: Save/load preserves full game state

**Definition of Done**: All 14+15 missions playable from start to finish.

---

## Phase 8: Multiplayer (Spring Boot + Lockstep)
**Goal**: Online multiplayer functional.

### Tasks
- [ ] Create Spring Boot server (`aow2-server`)
  - Player registration and authentication (JWT)
  - Matchmaking queue and lobby system
  - Game session management
  - PostgreSQL persistence (accounts, match history, leaderboards)
- [ ] Implement lockstep P2P networking (`aow2-core`)
  - Command serialization (all player actions as commands)
  - Deterministic simulation (both clients run identical game state)
  - Frame synchronization protocol
  - Desync detection and recovery
  - Latency compensation (delay-based input buffering)
- [ ] Implement Netty-based network transport
- [ ] Implement chat system (lobby + in-game)
- [ ] Implement player ranking/ELO system
- [ ] Write network integration tests
  - Test: Two clients can connect and play a match
  - Test: Desync is detected and reported
  - Test: Match results are persisted to database
  - Test: 100+ command simulation remains in sync

**Definition of Done**: Two players can connect, play a match, and see identical results.

---

## Phase 9: Map Builder
**Goal**: Integrated map editor.

### Tasks
- [ ] Create map editor UI (accessible from main menu)
  - Tile painting (terrain types)
  - Building placement
  - Unit placement
  - Resource/mines placement
  - Starting position markers
- [ ] Implement map save/load (JSON format)
- [ ] Implement map validation (playability checks)
- [ ] Implement map testing (play your map immediately)
- [ ] Implement map sharing (upload to server, download community maps)
- [ ] Write map editor tests
  - Test: Created map loads and plays correctly
  - Test: Map validation catches invalid configurations
  - Test: Shared maps are downloadable

**Definition of Done**: Player can create, test, and share custom maps.

---

## Phase 10: Modding System
**Goal**: Data-driven modding with Lua scripting.

### Tasks
- [ ] Implement mod loader (`aow2-modding`)
  - Mod manifest (mod.json)
  - Dependency resolution
  - Hot-reload for development
- [ ] Implement data-driven overrides
  - Unit stats (JSON/YAML)
  - Building stats (JSON/YAML)
  - Tech tree modifications
  - Weapon definitions
  - Cost adjustments
- [ ] Implement Lua scripting via LuaJ
  - Campaign mission scripts
  - Custom AI behaviors
  - Event hooks (onUnitKilled, onBuildingDestroyed, etc.)
- [ ] Implement mod UI (browse, install, uninstall, enable/disable)
- [ ] Write modding tests
  - Test: Mod with stat overrides works
  - Test: Lua scripts execute correctly
  - Test: Invalid mods are rejected gracefully

**Definition of Done**: Can load a mod that changes unit stats and adds a custom campaign script.

---

## Phase 11: Replay System
**Goal**: Full replay recording and playback.

### Tasks
- [ ] Implement command recording (all player inputs serialized)
- [ ] Implement replay file format (binary, compact)
- [ ] Implement replay playback engine
- [ ] Implement seeking (jump to any game tick)
- [ ] Implement replay sharing (upload to server)
- [ ] Implement replay viewer UI (camera follows action)
- [ ] Write replay tests
  - Test: Recorded replay plays back identically
  - Test: Seek produces correct game state
  - Test: Replay survives version changes (format versioning)

**Definition of Done**: Can record a multiplayer match and play it back perfectly.

---

## Phase 12: Web Client
**Goal**: Browser-playable version.

### Tasks
- [ ] Evaluate approach: GraalVM WASM compilation or separate web frontend
- [ ] Implement web client connecting to same Spring Boot backend
- [ ] Implement WebGL/Canvas rendering
- [ ] Implement touch controls for mobile browsers
- [ ] Ensure feature parity with desktop client (single-player)
- [ ] Write web integration tests

**Definition of Done**: Game playable in browser with single-player modes.

---

## Phase 13: Polish & Optimization
**Goal**: Production-quality game.

### Tasks
- [ ] Performance optimization (target 60 FPS with 200+ units)
- [ ] Memory optimization (entity pooling, sprite batching)
- [ ] Sound and music integration
- [ ] Tutorial system
- [ ] Accessibility (colorblind mode, key rebinding, font scaling)
- [ ] Error handling and crash reporting
- [ ] Localization (English + original languages)
- [ ] Docker Compose setup for server deployment
- [ ] Documentation and user guide
- [ ] Full regression test pass

**Definition of Done**: Game runs smoothly, no crashes, all tests pass, ready for release.

---

## Phase 14: Final Testing, Report & Release
**Goal**: Ship it.

### Tasks
- [ ] Full regression test suite pass
- [ ] Multiplayer stress test (10+ simultaneous matches)
- [ ] Campaign completion test (all missions start-to-finish)
- [ ] Mod compatibility test
- [ ] Replay integrity verification
- [ ] Generate development report (markdown)
- [ ] Push everything to GitHub
- [ ] Tag release version

**Definition of Done**: Everything is on GitHub, report is generated, game is playable.
