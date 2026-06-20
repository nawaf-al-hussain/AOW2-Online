# Project Progress — Art of War 2: Online (Honest Reassessment)

> This file tracks actual project status. Previous version was over-optimistic.
> Updated: 2026-06-20 — Critical re-audit as senior game developer.

## REALITY CHECK: What's Actually Playable

**A fully playable 1v1 skirmish match on the test map** — that is the current ceiling.
You can: select units, move/attack, watch AI opponents, see the economy tick, observe
combat with projectiles and splash damage, and view replays. Everything else has gaps.

## Phase 0: Project Scaffolding ✅ COMPLETE
- [x] Gradle multi-module project (Kotlin DSL)
- [x] Java 21 + FXGL + Spring Boot
- [x] JUnit 5 + Mockito (76 test files, ~1,463 test methods)
- [x] GitHub Actions CI (`.github/workflows/ci.yml`)
- [x] GitHub repo pushed

## Phase 1: Core Engine & Data Model ✅ COMPLETE (with caveats)
- [x] Fixed-timestep game loop (10 TPS)
- [x] Entity model (Unit, Building, Projectile, Mine)
- [x] Faction enum, Terrain/Tile system
- [x] Map loading from JSON (31 maps)
- [x] GameState with event queue
- [ ] **C-NEW-3**: Missing unit types (4, 7, 12, 13, 14) needed by tech tree
- [ ] **H-NEW-1**: `game_config.json` battle_time_limits wrong
- [ ] **H-NEW-5**: Rebel building stats are guessed (copied from Confed)

## Phase 2: Rendering & UI Framework ✅ MOSTLY COMPLETE
- [x] Isometric tile renderer, sprite system, camera, minimap, fog of war
- [x] HUD with resources, selection info, production queue display
- [x] Mouse selection (box, click, shift-click)
- [x] Right-click commands (move, attack, garrison, stop, patrol)
- [x] Health bars, death animations, main menu, accessibility settings
- [ ] **H-NEW-11**: Build command silently dropped — no build placement UI
- [ ] **M-NEW-23**: Production queue is display-only — no unit selection UI
- [ ] **H-NEW-12**: Always plays on test map — no real map loading
- [ ] **L-NEW-9**: Faction hardcoded to CONFEDERATION
- [ ] **H-NEW-14**: Zero audio files — system is silent

## Phase 3: Movement & Pathfinding ✅ COMPLETE
- [x] A* pathfinding (8-dir, octile heuristic, 200-step max)
- [x] Terrain passability, collision avoidance, group movement
- [x] Stuck detection, garrison enter/exit
- [ ] **M-NEW-11**: Attack-move doesn't set autoEngage flag (just does regular move)
- [ ] **M-NEW-12**: Patrol only moves one-way (no return path)

## Phase 4: Combat System ✅ COMPLETE
- [x] Two-step clamp damage formula, armor with research bonuses
- [x] Projectile system (bullets, rockets, artillery, flame; 400 max)
- [x] Splash damage (artillery no falloff, nuclear Chebyshev falloff)
- [x] Bunker garrison attacks, defensive building attacks
- [x] Siege mode, infantry vs machinery/building reductions
- [x] Mine detonation, HP regeneration, DeterministicLCG
- [x] Ranged attack state machine (wind-up → fire → cooldown)
- [ ] **C-NEW-1**: `distanceClass()` clamps instead of returning 127 sentinel

## Phase 5: Economy & Buildings ⚠️ PARTIALLY COMPLETE
- [x] Auto-resource generation (128-tick cycles, diminishing CC returns)
- [x] Building placement system, power system, construction
- [x] Production queues (sequential, cost deduction, research gates)
- [x] Kill reward formula, economy tests
- [ ] **C-NEW-2**: ResearchEffect model can't represent 70%+ of effects
- [ ] **H-NEW-3**: `applyResearchEffect()` only logs — effects never actually applied
- [ ] **H-NEW-4**: Bunker and TechCentre may have swapped stats

## Phase 6: AI System ✅ COMPLETE
- [x] AI decision system (EconomyAI, MilitaryAI, ResearchAI)
- [x] Three difficulties, deterministic, fog-of-war aware
- [x] Sealed interface MilitaryAction

## Phase 7: Campaign System ⚠️ ARCHITECTURE EXISTS, NOT FUNCTIONAL
- [x] Mission scripting system (Lua 5.2 via LuaJ)
- [x] 29 Lua scripts (7+7 campaign + 15 custom)
- [x] Save/load system, briefing screen, campaign manager
- [ ] **C-NEW-4**: ModEventBridge callbacks never registered — Lua events never fire
- [ ] **H-NEW-13**: CampaignManager never injected into CampaignScene
- [ ] **M-NEW-17**: GameAPI event hooks never fired

## Phase 8: Multiplayer ⚠️ ARCHITECTURE EXISTS, SECURITY ISSUES
- [x] Spring Boot server, JWT auth, matchmaking, lockstep networking
- [x] Desync detection, chat system, ELO ranking, session management
- [x] PostgreSQL persistence (Flyway V1-V4)
- [ ] **C-NEW-5**: Game-over self-confirmation ELO fraud
- [ ] **C-NEW-6**: Chat eavesdropping vulnerability
- [ ] **C-NEW-7**: JWT default secret in source
- [ ] **H-NEW-8**: Race condition in game-over claims
- [ ] **H-NEW-10**: Pending claims memory leak
- [ ] **C-NEW-8/9**: Test suite doesn't compile (2 test files broken)

## Phase 9: Map Builder ✅ COMPLETE
- [x] Map editor UI, save/load, validation, sharing, tile/entity placement

## Phase 10: Modding System ⚠️ MOSTLY COMPLETE, KEY GAP
- [x] Mod loader, data overrides, Lua scripting, mod manager
- [x] Campaign mission scripts, mod installer, mod manager UI
- [ ] **C-NEW-4**: ModEventBridge never wired — events fire into void
- [ ] **M-NEW-14**: ModInstaller ZIP stream exhaustion bug
- [ ] **M-NEW-15**: GameDataRegistry must be manually updated per field

## Phase 11: Replay System ✅ COMPLETE
- [x] Command recording, binary file format, playback with seeking

## Phase 12: Web Client ❌ DEMO SHELL ONLY
- [x] Next.js + shadcn/ui + Tailwind + Prisma project structure
- [x] 41 shadcn/ui components, login dialog, tab layout
- [ ] **ALL data hardcoded** — no backend integration
- [ ] **API route is stub** ("Hello, world!")
- [ ] **Matchmaking is fake** (8-second timer)
- [ ] **No real web-playable game client**

## Phase 13: Polish & Optimization ❌ NOT STARTED
- [x] Sound/music infrastructure (no audio files)
- [x] Tutorial system, accessibility settings, Docker setup
- [ ] Performance optimization (entity pooling, sprite batching)
- [ ] Localization / i18n
- [ ] Full regression test pass
- [ ] Documentation

## Phase 14: Final Testing & Release ❌ NOT STARTED
- [ ] Stress test, campaign completion test, mod compatibility
- [ ] Replay integrity, development report, release tag

---

## Build & Test Status

| Check | Status |
|-------|--------|
| Main code compilation | ⚠️ Unknown (Gradle times out in sandbox) |
| Test compilation | ❌ FAILS (CommandTypeTest, ChatControllerTest) |
| Test execution | ❌ Cannot run (compilation failure blocks) |
| Web build | ✅ Compiles (Next.js) |
| Web tests | ✅ Pass (3 test files, store logic only) |

---

## Codebase Metrics

| Metric | Value |
|--------|-------|
| Java source files | 161 |
| Test files | 76 |
| Test methods (claimed) | ~1,463 |
| Test methods (verified passing) | Unknown — suite won't compile |
| Java LOC (src/main) | ~35,800 |
| Lua campaign scripts | 30 |
| Map JSON files | 31 |
| Data JSON files | 43 |
| Web components | 41 shadcn/ui + 11 custom |
| Open issues (NEW audit) | 79 (9 Critical, 16 High, 32 Medium, 22 Low) |
| `java.util.Random` in game logic | 0 ✅ |
| HashMap/HashSet in game state | 4 (potential determinism risk) |

---

## Assumptions Log (20 unverified — NOT fixed)

| Assumption | Value | Status |
|-----------|-------|--------|
| `SIEGE_RANGE_BONUS` | 3 | Unverified |
| `ARTILLERY_FIXED_FLIGHT_TIME` | 15 | Unverified |
| `CC_PLACEMENT_RADIUS` | 20 | Unverified |
| `ARM_DELAY_TICKS` | 10 | Unverified |
| `CANCEL_REFUND_PERCENT` | 0.50 | Unverified |
| `BUILDING_ATTACK_COOLDOWN` | 5 | Unverified |
| `INFANTRY_BASE_RECOVERY` | 1 | Unverified |
| `MACHINERY_BASE_REPAIR` | 2 | Unverified |
| `RESISTANCE_INCOME_MULTIPLIER` | 1.15 | Unverified |
| `CC_UPGRADE_INCOME_BONUS` | 2/level | Unverified |
| Rebel building stats | Copied Confed | Unverified |
| `REBEL_WALL` stats | Guessed | Unverified |
| Nuclear divisor | 12 | Reconstructed |
| Ranged wind-up | attackSpeed/2 | Unverified |
| Infantry vs building | 0.5x | Unverified |
| Infantry vs machinery | 0.7x | Unverified |
| `battle_time_limits` | [1001,1100,...] | **WRONG** |
| Bunker/TechCentre stats | Identical | Possible error |
| Infantry Centre power | 2 | Unverified |
| Research 2-3 targets | "Assault" | Ambiguous |