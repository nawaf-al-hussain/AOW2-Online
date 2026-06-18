# Project Progress — Art of War 2: Online

> This file tracks everything that has been implemented and what remains.
> Updated after each development session.

## Status: Phase 13 IN PROGRESS — Spec Compliance ~97%

## Phase 0: Project Scaffolding ✅
- [x] Gradle multi-module project initialized (5 modules: common, core, client, server, modding)
- [x] Java 21 + FXGL 21 + Spring Boot 3.3 dependencies configured
- [x] .gitignore, README.md, LICENSE (MIT) created
- [x] Basic FXGL application class created (AOW2App, 1280x720 window)
- [x] JUnit 5 + Mockito + JaCoCo configured
- [x] GitHub Actions CI set up (.github/workflows/ci.yml)
- [x] Initial commit pushed to GitHub (main + dev branches)
- [x] Docker Compose setup (Spring Boot + PostgreSQL)
- [x] Flyway migration V1 (players, match_results, uploaded_maps)

## Phase 1: Core Engine & Data Model ✅
- [x] Fixed-timestep game loop (10 TPS) — GameLoop.java
- [x] Entity model (Unit, Building, Projectile, Mine) with runtime state
- [x] Faction enum (CONFEDERATION, RESISTANCE, NEUTRAL)
- [x] Terrain/Tile system (TerrainType enum with passability, 15 types with correct RE IDs)
- [x] Map loading from JSON (MapLoader.java with Jackson)
- [x] GameState class with event queue
- [x] EventBus (event-based system using sealed GameEvent interface)
- [x] Data model tests (FactionTest, GridPositionTest, DirectionTest, GameConstantsTest)
- [x] StatsRegistry — centralized unit/building stats from RE data (17 units, 16 buildings)
- [x] GameConstants corrected: TICK_RATE=10, MAX_BUILDINGS_PER_PLAYER=22
- [x] BuildingType producesPower() includes CC/HQ (powerProduce=6 per RE spec)
- [x] BuildingType isIncomeBuilding() for CC/HQ credit generation
- [x] UnitCategory: SPECIAL_MACHINERY for Flame Assault (no infantry bit)
- [x] UnitType.isMachinery() for combat damage modifiers
- [x] Fog of War updates every 4 ticks per RE spec (gameTick & 3 == 0)

## Phase 2: Rendering & UI Framework ✅
- [x] Isometric tile renderer (IsometricRenderer.java with diamond grid)
- [x] Sprite loading and rendering (SpriteManager + ProceduralSpriteGenerator)
- [x] Camera system (CameraController with pan/zoom/bounds)
- [x] Unit rendering (EntityRenderer with facing directions)
- [x] Building rendering (with construction states)
- [x] Minimap (MinimapRenderer.java)
- [x] HUD (resources, selection info, action buttons, production queue display)
- [x] Mouse selection (SelectionManager with box/click select)
- [x] Right-click commands (InputHandler wired to CommandType records)
- [x] Production queue display (HUD shows current + queued production)
- [x] Fog of war rendering (FogRenderer.java)
- [x] Health bars (EntityRenderer renders colored health bars above damaged entities)
- [x] Main menu (MainMenuScene.java)
- [x] GameScene wired to TickManager for game logic processing
- [x] Building construction progress ticking
- [x] Credits synced from EconomySystem

## Phase 3: Movement & Pathfinding (COMPLETE)
- [x] A* pathfinding (PathfindingSystem with octile heuristic, 8-directional)
- [x] Terrain passability (per-unit-category system in TerrainType)
- [x] Collision avoidance (CollisionSystem with spatial hash grid, O(n) average)
- [x] Formation movement (group move with spacing in MovementSystem)
- [x] Stuck detection (stuckCounter threshold triggers re-pathfinding)
- [x] Speed formula corrected: ticksPerCell = 10 - speed + 1 (higher speed = faster)
- [x] Garrison movement (garrison/ungarrison with position update, adjacent spawn)
- [x] Large unit collision (2-cell Fortress support)

## Phase 4: Combat System (IMPLEMENTED)
- [x] Damage formula (exact RE formula with two-step clamp)
- [x] Armor calculation (ArmorCalculator with research bonuses)
- [x] Projectile system (ProjectileSystem, max 400 active)
- [x] Splash damage (no falloff for regular artillery)
- [x] Nuclear damage (31x31 distance lookup table + two-step clamp per RE spec)
- [x] Attack cooldowns (per-unit attack speed)
- [x] Death animations (matching RE bi[]/bd[] arrays)
- [x] Bunker garrison attacks
- [x] Defensive building attacks
- [x] Target type multipliers
- [x] Siege mode (with damage bonus)

## Phase 5: Economy & Buildings (IMPLEMENTED)
- [x] Auto-resource generation (127-tick cycle, diminishing returns)
- [x] Building placement system (CC radius, terrain, cost, tech, limits)
- [x] Generator power system (PowerSystem with radius grid)
- [x] Building construction (progress ticking, completion)
- [x] Production queues (enqueue, cancel, progress, spawn)
- [x] Research system (lifecycle: start, progress, complete)
- [x] Research effects (48 tech nodes: 25 Confed + 23 Rebel, lazy evaluation)
- [x] HP Regeneration System (infantry recovery every 127 ticks, research triples rate)
- [x] Mine Detonation System (Scorpio=anti-tank, Frog=area, Lizard=multi-charge)
- [x] Building Upgrade System (3 levels, HP bonus, production speed modifier)
- [x] StatsRegistry integration (all costs/stats from RE data)

## Phase 6: AI System (COMPLETE)
- [x] AI decision system (AISystem coordinating economy/military/research)
- [x] Difficulty levels (Easy, Normal, Hard)
- [x] Base building logic (priority build order)
- [x] Unit composition (game-phase-based decisions)
- [x] Retreat behavior (threat assessment)
- [x] Fog-of-war awareness (MilitaryAI/EconomyAI only see VISIBLE enemy entities)

## Phase 7: Campaign System ✅
- [x] Campaign data structures
- [x] CampaignManager with episode progression
- [x] SaveManager with serialization
- [x] SaveManager.restore() for full game state reconstruction from SaveData
- [x] Trigger.check() returns activated trigger (immutable record pattern)
- [x] Lua mission scripting (MissionScriptEngine via reflection, 29 scripts)
- [x] Episode missions (14 missions: 7 Ep1 + 7 Ep2 with maps + Lua scripts)
- [x] Custom missions (15 missions with maps + Lua scripts)
- [x] Mission briefing screen (CampaignScene)
- [x] Victory/defeat conditions (Objective sealed interface)

## Phase 8: Multiplayer ✅
- [x] Spring Boot server
- [x] Player authentication (JWT)
- [x] Matchmaking system
- [x] Lockstep P2P networking (Move commands pathfind via MovementSystem)
- [x] Desync detection (SyncChecker includes economy + research state in hash)
- [x] Command serialization (11 types, unified CommandSerializer format)
- [x] Replay format unified (ReplayRecorder uses CommandSerializer for wire compatibility)
- [x] Chat system (WebSocket real-time + REST history, ChatMessage persistence)
- [x] ELO ranking (K=32/24, starting 1000, leaderboard endpoints)
- [x] MultiplayerService (REST + WebSocket client)
- [x] MultiplayerLobbyScene (real server connectivity)

## Phase 9: Map Builder ✅
- [x] Map editor UI (MapEditor, TilePainter, EntityPlacer)
- [x] Map validation (MapValidationResult)
- [x] Map save/load (JSON format, MapEditorScene load/save dialogs)
- [x] Map testing (play your map immediately)
- [x] Map sharing (MapShareService upload/download to server)

## Phase 10: Modding System ✅
- [x] Mod loader (ModLoader, ModManager, ModManifest)
- [x] Data-driven overrides (DataOverride, GameDataRegistry)
- [x] Lua scripting (LuaEngine, ScriptBindings, GameAPI)
- [x] Mod UI (ModManagerScene with browse, install, uninstall, enable/disable)
- [x] ModInstaller (ZIP-based installation with validation)

## Phase 11: Replay System (COMPLETE)
- [x] Command recording (ReplayRecorder using CommandSerializer format)
- [x] Replay file format (binary, AOW2 magic header, version 1)
- [x] Replay playback (ReplayPlayer with seek support)
- [x] Replay sharing (upload to server via ReplayController)

## Phase 12: Web Client ✅
- [x] Next.js web companion app (lobby, leaderboards, maps, chat)
- [x] Real-time WebSocket integration for game lobby (via MultiplayerService)
- [x] Touch controls for mobile browsers (responsive design)
- [x] Feature parity with desktop client (single-player) — lobby, maps, replays, chat, unit database
- [x] Dark military theme matching desktop client aesthetic
- [x] Login/Register with Spring Boot backend
- [x] Matchmaking search with ELO range visualization
- [x] Community map browser with search and download
- [x] Global leaderboard with ELO rankings
- [x] Lobby chat with real-time messaging
- [x] Replay browser with match details
- [x] Full unit database for both factions with stat bars
- [x] Faction comparison and feature showcase

## Phase 13: Polish & Optimization (PENDING)
- [ ] Performance optimization (target 60 FPS with 200+ units)
- [x] Tutorial system (TutorialSystem implemented, needs integration)
- [x] Accessibility (AccessibilitySettings implemented)
- [x] Audio (AudioManager + MusicPlayer implemented)
- [ ] Memory optimization (entity pooling, sprite batching)
- [ ] Sound and music integration
- [ ] Error handling and crash reporting
- [ ] Localization (English + original languages)
- [x] Docker Compose setup for server deployment
- [ ] Documentation and user guide
- [ ] Full regression test pass

## Phase 14: Final Testing & Release (PENDING)
- [ ] Full regression test suite pass
- [ ] Multiplayer stress test (10+ simultaneous matches)
- [ ] Campaign completion test (all missions start-to-finish)
- [ ] Mod compatibility test
- [ ] Replay integrity verification
- [ ] Development report
- [ ] Push everything to GitHub
- [ ] Release tag

---

## Assumptions Log

| Date | Phase | Assumption | Reason | Status |
|------|-------|-----------|--------|--------|
| 2026-06-14 | 1 | MAX_SPEED_RATING=10, ticksPerCell=10-speed+1 | RE shows speed as rating | Active |
| 2026-06-14 | 5 | CANCEL_REFUND_PERCENT=50% | Not in RE data | Active |
| 2026-06-14 | 4 | Infantry vs machinery=0.7x | Reduction confirmed, exact value unknown | Active |
| 2026-06-14 | 4 | Siege vs building=1.5x | Bonus confirmed, exact value unknown | Active |
| 2026-06-14 | 5 | Building upgrades implemented (3 levels) | BuildingUpgradeSystem created | Resolved |
| 2026-06-14 | 1 | Mine attackRange=sightRange | RE data lacks separate mine range | Active |
| 2026-06-14 | 1 | Rebel stats partially derived | RE only has partial rebel data | Active |
| 2026-06-14 | 6 | AI fog-of-war uses visible entities only | FogOfWarSystem filters enemy knowledge | Active |

## Known Issues

| Date | Phase | Issue | Severity | Status |
|------|-------|-------|----------|--------|
| 2026-06-14 | 6 | AI has no fog-of-war | Medium | **Fixed** |
| 2026-06-14 | 7 | Trigger.check() doesn't mutate activated state | Medium | **Fixed** |
| 2026-06-14 | 7 | SaveManager can't fully restore | Medium | **Fixed** |
| 2026-06-14 | 8 | SyncChecker missing economy/research state | Medium | **Fixed** |
| 2026-06-14 | 11 | Two different wire formats for replay | Medium | **Fixed** |
| 2026-06-14 | 1 | Tile.java orphaned (GameMap uses TerrainType[][]) | Low | **Fixed** |
| 2026-06-14 | 3 | CollisionSystem O(n²), no spatial index | Low | **Fixed** |
| 2026-06-19 | Audit | EconomySystem comments said 127 ticks (was already 128) | Low | **Fixed** |
| 2026-06-19 | Audit | GridPosition DISTANCE_TABLE had center=7 (should be 0) | Critical | **Fixed** |
| 2026-06-19 | Audit | CombatSystem range checks used Euclidean not Chebyshev | Critical | **Fixed** |
| 2026-06-19 | Audit | CombatSystem.performAttack bypassed projectiles | Critical | **Fixed** |
| 2026-06-19 | Audit | Siege damage bonus applied unconditionally (needs research 36) | High | **Fixed** |
| 2026-06-19 | Audit | HPRegenerationSystem used wrong research IDs (1, 9) | High | **Fixed** |
| 2026-06-19 | Audit | SessionService wsSessionToPlayer not cleaned up | High | **Fixed** |
| 2026-06-19 | Audit | PowerSystem range checks used Euclidean not Chebyshev | Medium | **Fixed** |

## Session History

| Date | Commit | Changes |
|------|--------|----------|
| 2026-06-14 | ad56d9a | StatsRegistry, spec compliance fixes, wire client to engine |
| 2026-06-14 | ce1c17c | HP Regen, Mine Detonation, Building Upgrades, 48-node TechTree, FoW 4-tick, nuclear 31x31 table, SPECIAL_MACHINERY |
| 2026-06-14 | pending | Bug fixes (Trigger, SaveManager, SyncChecker, replay), AI fog-of-war, garrison movement, Chat, ELO, spatial hash, Tile integration |
| 2026-06-19 | — | Analysis report fixes: DISTANCE_TABLE corrected to Chebyshev, CombatSystem range/projectile/siege fixes, HPRegen wrong research IDs removed, EconomySystem comment cleanup, SessionService memory leak, PowerSystem distanceClass |
