# Project Progress — Art of War 2: Online

> This file tracks everything that has been implemented and what remains.
> Updated after each development session.

## Status: PHASES 0–11 COMPLETE (Phase 12 partial, Phases 13–14 pending)

## Phase 0: Project Scaffolding
- [x] Gradle multi-module project initialized (Kotlin DSL)
- [x] Java 21 + FXGL + Spring Boot dependencies configured
- [x] .gitignore, README.md, LICENSE created
- [x] Basic FXGL application opens a window (`AOW2App.java`)
- [x] JUnit 5 + Mockito configured (76 test files, 1,463 test methods)
- [x] GitHub Actions CI set up (`.github/workflows/ci.yml`)
- [x] Initial commit pushed to GitHub

## Phase 1: Core Engine & Data Model
- [x] Fixed-timestep game loop (10 TPS) in `TickManager` + `GameLoop`
- [x] Entity model (Unit, Building, Projectile, Mine)
- [x] Faction enum (`CONFEDERATION`, `RESISTANCE`, `NEUTRAL`)
- [x] Terrain/Tile system with 12 terrain types from RE spec
- [x] Map loading from JSON (31 maps including campaign + custom + test)
- [x] `GameState` class with event queue
- [x] All data models loaded from `units.json`, `buildings.json`, `tech_tree.json`, `game_config.json`
- [x] Data model tests

## Phase 2: Rendering & UI Framework
- [x] Isometric tile renderer (`IsometricRenderer`, 30x20 diamond tiles)
- [x] Sprite loading and rendering (`SpriteManager`, `ProceduralSpriteGenerator`)
- [x] Camera system (`CameraController` — pan, zoom, bounds, smooth interpolation)
- [x] Unit rendering with 8-directional facing (`EntityRenderer`)
- [x] Building rendering with construction states
- [x] Minimap (`MinimapRenderer`)
- [x] HUD (`HUD` — resources, selection info, production queues)
- [x] Mouse selection (box select, click select — `SelectionManager`)
- [x] Right-click command (move, attack, garrison — `InputHandler`)
- [x] Production UI (build menu, queue display)
- [x] Fog of war rendering (`FogRenderer`)
- [x] Health bars and damage indicators
- [x] Main menu screen (`MainMenuScene`)
- [x] Accessibility settings (`AccessibilitySettings`)
- [x] Tutorial system (`TutorialSystem`)
- [x] Audio system (`AudioManager`, `MusicPlayer`) — wired into `GameScene`

## Phase 3: Movement & Pathfinding
- [x] A* pathfinding (`PathfindingSystem` — 8-directional, octile heuristic, 200-step max)
- [x] Terrain passability (per-unit-type via `TerrainType.isPassableBy()`)
- [x] Collision avoidance (`CollisionSystem`)
- [x] Group movement (`MovementSystem.groupMove`)
- [x] Stuck detection and re-routing
- [x] Garrison enter/exit movement
- [x] Movement tests

## Phase 4: Combat System
- [x] Damage formula (two-step clamp: `max(min(dmg*10-arm/10, dmg-arm), 1)`)
- [x] Armor calculation with research bonuses (`ArmorCalculator`)
- [x] Projectile system (`ProjectileSystem` — bullets, rockets, artillery, flame; 400 max)
- [x] Splash damage (artillery no falloff, nuclear Chebyshev falloff via 31x31 lookup)
- [x] Attack cooldowns per weapon type
- [x] Death animation frame calculation (`DamageCalculator.calculateDeathAnimationFrame`)
- [x] Bunker garrison attacks
- [x] Defensive building attacks (bunker, rocket tower)
- [x] Ranged attack state machine (wind-up → firing → cooldown) [FIX M-25]
- [x] Siege mode with damage bonus (research ID 36)
- [x] Infantry vs machinery damage reduction (0.7x)
- [x] Infantry vs building damage reduction (0.5x)
- [x] Mine detonation system (`MineDetonationSystem` — Scorpio anti-tank, Frog area, Lizard multi-charge)
- [x] HP regeneration (infantry near powered buildings) with power proximity check
- [x] `DeterministicLCG` for all random in combat (no `java.util.Random`) [FIX M-10]
- [x] Combat simulation tests

## Phase 5: Economy & Buildings
- [x] Auto-resource generation (`EconomySystem` — 128-tick cycles, diminishing CC returns)
- [x] Building placement system (`BuildingPlacementSystem` — CC radius, terrain check, power check)
- [x] Generator power system (`PowerSystem` — 6-level radius [10,20,30,40,60,127], Chebyshev distance)
- [x] Building construction (build time, progress tracking)
- [x] Production queues (`ProductionSystem` — sequential, cost deduction, research gates)
- [x] Technology Centre research system (`ResearchSystem` — 48 research nodes, one at a time)
- [x] Building destruction
- [x] Kill reward formula based on unit cost and distance
- [x] Economy simulation tests

## Phase 6: AI System
- [x] AI decision system (`AISystem` — coordinates 3 sub-AIs)
- [x] Difficulty levels (Easy: 60 ticks, Normal: 30, Hard: 15)
- [x] Base building logic (`EconomyAI` — CC → Generator → Infantry Centre → Tech Centre → Factory)
- [x] Unit composition and attack timing (`MilitaryAI`)
- [x] Retreat behavior (when outnumbered)
- [x] Fog-of-war awareness (AI uses `FogOfWarSystem` for limited vision)
- [x] Research priorities (`ResearchAI` — phase-based: early/mid/late)
- [x] Deterministic AI (`DeterministicLCG` — Park-Miller, no `java.util.Random`)
- [x] Sealed interface `MilitaryAction` (Attack, Defend, Retreat, Harass, HoldPosition)
- [x] AI behavior tests

## Phase 7: Campaign System
- [x] Mission scripting system (Lua 5.2 via LuaJ)
- [x] Episode 1 missions (7 Lua scripts — `ep1_mission1.lua` through `ep1_mission7.lua`)
- [x] Episode 2 missions (7 Lua scripts — `ep2_mission1.lua` through `ep2_mission7.lua`)
- [x] Custom missions (15 Lua scripts — `custom_mission1.lua` through `custom_mission15.lua`)
- [x] Save/load system (`SaveData`, `SaveManager` — full state serialization)
- [x] Mission briefing screen (`CampaignScene`)
- [x] Victory/defeat conditions (via Lua triggers/objectives)
- [x] Campaign manager (`CampaignManager` — 558 lines)

## Phase 8: Multiplayer (Spring Boot + Lockstep)
- [x] Spring Boot server (`AOW2ServerApp` — port 8080)
- [x] Player authentication (JWT with bcrypt, `AuthService`)
- [x] Matchmaking system (`MatchmakingService` — ELO range 100→500 widening)
- [x] Lockstep P2P networking (`LockstepEngine` — 2-frame input delay, command buffer ring buffer)
- [x] Desync detection (`SyncChecker` — 150-tick interval state hash comparison)
- [x] Chat system (lobby + in-game WebSocket — `ChatWebSocketHandler`)
- [x] ELO ranking (`RankingService` — K-factor 32 new / 16 experienced)
- [x] Session management (`SessionService`)
- [x] Rate limiting (`RateLimitFilter`)
- [x] PostgreSQL persistence (Flyway migrations V1–V4)
- [x] Player accounts, match history, leaderboards

## Phase 9: Map Builder
- [x] Map editor UI (`MapEditorScene`, `MapEditor`)
- [x] Map save/load (JSON format)
- [x] Map validation (`MapValidationResult`)
- [x] Map testing (play your map immediately)
- [x] Map sharing (`MapShareService`, `MapController` — upload/download)
- [x] Tile painting with variable brush sizes (`TilePainter`)
- [x] Entity placement (`EntityPlacer`)

## Phase 10: Modding System
- [x] Mod loader (`ModLoader` — directory scanning, `mod.json` parsing, version check)
- [x] Data-driven overrides (`DataOverride`, `GameDataRegistry` — unit/building stats)
- [x] Lua scripting via LuaJ (`LuaEngine`, `ScriptBindings`, `GameAPI`)
- [x] Campaign mission scripts (`MissionScriptEngine`)
- [x] Mod manager (`ModManager` — discover, load, enable, disable, hot-reload)
- [x] Mod installer (`ModInstaller`)
- [x] Mod event bridge (`ModEventBridge` — core ↔ modding events)
- [x] Example mod (`example_mod/`)
- [x] Mod manager UI (`ModManagerScene`)

## Phase 11: Replay System
- [x] Command recording (`ReplayRecorder` — all 11 command types, binary format)
- [x] Replay file format (AOW2 magic, version, map name, player factions, commands)
- [x] Replay playback (`ReplayPlayer`)
- [x] Replay sharing (server storage via `ReplayStorageService`, `ReplayController`)
- [x] Replay tests

## Phase 12: Web Client
- [ ] Web-playable game client (GraalVM WASM or GWT compilation) — **NOT IMPLEMENTED**
- [x] Web companion dashboard (`aow2-web/` — Next.js + shadcn/ui + Tailwind + Prisma)
  - [x] Landing page with login/register
  - [x] Leaderboard display
  - [x] Map browser
  - [x] Replay browser
  - [x] Chat UI
  - [x] Matchmaking UI
  - [x] 41 shadcn/ui components
- [ ] Touch controls for mobile browsers

## Phase 13: Polish & Optimization
- [ ] Performance optimization (target 60 FPS with 200+ units)
- [ ] Memory optimization (entity pooling, sprite batching)
- [x] Sound and music infrastructure (`AudioManager` + `MusicPlayer` wired)
- [x] Tutorial system (`TutorialSystem`)
- [x] Accessibility (colorblind mode, key rebinding — `AccessibilitySettings`)
- [ ] Localization / i18n (no resource bundles yet)
- [x] Docker Compose setup for server deployment
- [ ] Full regression test pass
- [ ] Documentation and user guide

## Phase 14: Final Testing & Release
- [ ] Full regression test suite pass
- [ ] Multiplayer stress test (10+ simultaneous matches)
- [ ] Campaign completion test (all 29 missions start-to-finish)
- [ ] Mod compatibility test
- [ ] Replay integrity verification
- [ ] Development report
- [ ] GitHub push
- [ ] Release tag

---

## Bug Fix History

All tracked P0–P3 issues resolved. Recent session fixes:

| ID | Fix | File |
|----|-----|------|
| M-10 | Replaced `java.util.Random` with `DeterministicLCG` in DamageCalculator | `DamageCalculator.java` |
| M-30 | Implemented LOS blocking via DDA ray-casting in fog of war | `FogOfWarSystem.java` |
| M-33 | Wired `AudioManager` into `GameScene.initializeGame()` | `GameScene.java` |
| H-22 | Added isometric offset support to `CameraController.centerOnGrid()` | `CameraController.java` |
| M-25 | Implemented ranged attack state machine (wind-up → fire → cooldown) | `CombatSystem.java`, `Unit.java` |
| H-10 | Updated mine integration status (core logic complete, rendering noted) | `Mine.java` |
| — | Added power proximity check to infantry HP regeneration | `HPRegenerationSystem.java` |
| M-12 | Replaced hand-rolled JSON parser with Jackson ObjectMapper | `ResearchRegistry.java` |

---

## Assumptions Log

> Track all assumptions made during development.

| Date | Phase | Assumption | Reason | Status |
|------|-------|-----------|--------|--------|
| Original | 5 | `CC_PLACEMENT_RADIUS = 20` | RE doesn't specify exact value | Documented |
| Original | 5 | `ARTILLERY_FIXED_FLIGHT_TIME = 15` | RE confirms fixed flight but not exact tick count | Documented |
| Original | 5 | `ARM_DELAY_TICKS = 10` | RE confirms arm delay but not exact tick count | Documented |
| Original | 5 | `CANCEL_REFUND_PERCENT = 0.50` | RE doesn't specify exact value | Documented |
| Original | 5 | `BUILDING_ATTACK_COOLDOWN = 5` | RE doesn't specify exact value | Documented |
| Original | 5 | `INFANTRY_BASE_RECOVERY = 1` | RE doesn't specify exact value | Documented |
| Original | 5 | `MACHINERY_BASE_REPAIR = 2` | RE doesn't specify exact value | Documented |
| Original | 5 | Rebel building stats copied from Confederation | RE only provides upgrade costs for rebel buildings | Documented |
| Original | 3 | A* used instead of original Bresenham pathfinding | Deliberate improvement, documented | Accepted |
| Original | 4 | Nuclear distance factor uses divisor 12 | RE confirms 31x31 table, exact formula reconstructed | Documented |

## Codebase Metrics

| Metric | Value |
|--------|-------|
| Java source files | 161 |
| Test files | 76 |
| Test methods | 1,463 |
| Java LOC (src/main) | ~35,800 |
| Lua campaign scripts | 30 (14 campaign + 15 custom + 1 example) |
| Map JSON files | 31 |
| Data JSON files | 43 |
| Web components (shadcn/ui) | 41 |
| Remaining TODOs | 0 |
| `java.util.Random` in game logic | 0 |