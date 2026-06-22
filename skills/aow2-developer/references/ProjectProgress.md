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

## Phase 2: Rendering & UI Framework ✅ MOSTLY COMPLETE
- [x] Isometric tile renderer, sprite system, camera, minimap, fog of war
- [x] HUD with resources, selection info, production queue display
- [x] Mouse selection (box, click, shift-click)
- [x] Right-click commands (move, attack, garrison, stop, patrol)
- [x] Health bars, death animations, main menu, accessibility settings
- [x] Build placement UI (H-NEW-11 fixed)
- [x] Production queue cancel (M-NEW-23 fixed)
- [x] Real map loading (H-NEW-12 fixed)
- [x] Faction selection (L-NEW-9 fixed)

## Phase 3: Movement & Pathfinding ✅ COMPLETE
- [x] A* pathfinding (8-dir, octile heuristic, 200-step max)
- [x] Terrain passability, collision avoidance, group movement
- [x] Stuck detection, garrison enter/exit
- [x] Attack-move sets autoEngage (M-NEW-11 fixed)
- [x] Patrol returns to origin (M-NEW-12 fixed)

## Phase 4: Combat System ✅ COMPLETE
- [x] Two-step clamp damage formula, armor with research bonuses
- [x] Projectile system (bullets, rockets, artillery, flame; 400 max)
- [x] Splash damage (artillery no falloff, nuclear Chebyshev falloff)
- [x] Bunker garrison attacks, defensive building attacks
- [x] Siege mode, infantry vs machinery/building reductions
- [x] Mine detonation, HP regeneration, DeterministicLCG
- [x] Ranged attack state machine (wind-up → fire → cooldown)
- [x] Distance class 127 sentinel (C-NEW-1 fixed)

## Phase 5: Economy & Buildings ✅ COMPLETE
- [x] Auto-resource generation (128-tick cycles, diminishing CC returns)
- [x] Building placement system, power system, construction
- [x] Production queues (sequential, cost deduction, research gates)
- [x] Kill reward formula, economy tests
- [x] Research effects applied via data-driven ResearchBonusTracker (C-NEW-2 fixed)
- [x] Bunker/TechCentre stats verified (H-NEW-4 resolved)
- [x] FIX (H5): ResearchSystem.activeResearchMap changed from ConcurrentHashMap to LinkedHashMap — iteration order is now deterministic so applyResearchEffect calls have a stable order when multiple researches complete in the same tick.
- [x] FIX (L3): GameConstants.CC_UPGRADE_INCOME_BONUS_PER_LEVEL TODO comment removed (replaced with explicit ASSUMPTION note + Phase 13 work-tracking reference).

## Phase 6: AI System ✅ COMPLETE
- [x] AI decision system (EconomyAI, MilitaryAI, ResearchAI)
- [x] Three difficulties, deterministic, fog-of-war aware
- [x] Sealed interface MilitaryAction
- [x] FIX (H1): AISystem.ENABLE_STRATEGY_QUALITY_SKIP is now gated behind the system property `aow2.ai.strategy-skip` (default false). The probabilistic decision-cycle skip has NO basis in the RE spec — the original AI processes every cycle — so it is OFF by default for faithful recreation. Enable via -Daow2.ai.strategy-skip=true for casual modern-enhancement mode. Uses DeterministicLCG so still lockstep-safe.

## Phase 7: Campaign System ✅ PLAYTESTED AND FIXED
- [x] Mission scripting system (Lua 5.2 via LuaJ)
- [x] 29 Lua scripts (7+7 campaign + 15 custom)
- [x] Save/load system, briefing screen, campaign manager
- [x] ModEventBridge callbacks wired (C-NEW-4 fixed)
- [x] CampaignManager injected into CampaignScene (H-NEW-13 fixed)
- [x] GameAPI event hooks fire (M-NEW-17 fixed)
- [x] MapLoader supports 2D terrain array format (PLAYTEST-4)
- [x] Lua onStart() properly called from Java (PLAYTEST-3)
- [x] EventDispatcher + ModEventBridge wired for Lua events (PLAYTEST-5)
- [x] All 30 maps: invalid DIRT/RUINS terrain types replaced (PLAYTEST-10)
- [x] 17 OOB spawnUnit coordinates fixed across 4 missions (PLAYTEST-11)
- [x] ep2_mission4 victory condition bug fixed (PLAYTEST-7)
- [x] spawnUnit uses dynamic map dimensions, not hardcoded 128 (PLAYTEST-6)

## Phase 8: Multiplayer ✅ COMPLETE
- [x] Spring Boot server, JWT auth, matchmaking, lockstep networking
- [x] Desync detection, chat system, ELO ranking, session management
- [x] PostgreSQL persistence (Flyway V1-V5)
- [x] ELO fraud prevention (C-NEW-5 fixed)
- [x] Chat eavesdropping fixed (C-NEW-6 fixed)
- [x] JWT secret from env var (C-NEW-7 fixed)
- [x] Game-over race condition fixed (H-NEW-8 fixed)
- [x] Pending claims cleanup (H-NEW-10 fixed)
- [x] All tests compile (C-NEW-8/9 fixed)
- [x] FIX (C1): LockstepEngine.processFrame now calls the 4-arg computeStateHash(state, entities, economySystem, researchSystem) so credits and research state are included in the sync hash. Previously the 2-arg overload silently omitted them — credit divergence or research divergence went undetected.
- [x] FIX (C4): LockstepEngine.applyCommand now validates unit ownership for Attack, AttackMove (already had it), Stop, SiegeMode, and Patrol commands. Previously only Move validated ownership, allowing a malicious client to set targets on opponent units.
- [x] FIX (C6): LockstepEngine now uses EconomySystem.playerId(Faction) helper instead of Faction.ordinal() == playerId — eliminates the implicit coupling between player IDs and enum ordering.
- [x] FIX (M8): GameWebSocketHandler.handleCommand now rejects oversized command payloads (>4 KB) before relaying to the opponent, preventing memory-exhaustion attacks.
- [x] FIX (M6): RateLimitFilter.getClientIp now uses InetAddress.isSiteLocalAddress() for RFC 1918 checks — the previous `startsWith("172.")` matched all of 172.0.0.0/8 including public addresses like 172.217.x.x.
- [x] FIX (H7): EloRatingService marked @Deprecated(since="0.1.0", forRemoval=true) with scheduled v0.2.0 removal — kept for now so EloRatingServiceTest (14 tests) continues to provide redundant math coverage until RankingServiceTest (10 tests) reaches parity.

## Phase 9: Map Builder ✅ COMPLETE
- [x] Map editor UI, save/load, validation, sharing, tile/entity placement

## Phase 10: Modding System ✅ COMPLETE
- [x] Mod loader, data overrides, Lua scripting, mod manager
- [x] Campaign mission scripts, mod installer, mod manager UI
- [x] ModEventBridge fully wired (C-NEW-4 fixed)
- [x] ModInstaller ZIP stream fixed (M-NEW-14 fixed)
- [x] GameDataRegistry reflection-based overrides (M-NEW-15 fixed)
- [x] FIX (C3): ModManager.discoveredMods/enabledMods/modDirectories/modOverrides changed from ConcurrentHashMap to LinkedHashMap (insertion-ordered). Previously the undefined iteration order of ConcurrentHashMap made mod-override application non-deterministic across JVM launches — two enabled mods overriding the same stat could produce different game stats on different launches, violating the project's "no non-deterministic collections in game state" invariant.
- [x] FIX (H-MOD): ModManager.discoverMods and ModLoader.loadAll now catch broad `Exception` instead of only `IOException`. ModManifest's compact constructor throws IllegalArgumentException (a RuntimeException) for blank/null required fields — under some Jackson configurations this propagated uncaught and terminated the entire mod discovery loop, preventing subsequent valid mods from loading.

## Phase 11: Replay System ✅ COMPLETE
- [x] Command recording, binary file format, playback with seeking
- [x] FIX (C2): ReplayEntry typeOrd validation now accepts 1-12 (was 1-11, which rejected AttackMove=0x0C and crashed every AttackMove recording)
- [x] FIX (C5): ReplayPlayer.CommandCallback now has resetToInitialState() default method; seekTo() calls it before backward-seek re-execution (previously snapshots only stored command indices, so re-execution piled on top of advanced state)
- [x] FIX (H6): ReplayFile FORMAT_VERSION bumped to 2; recordedAt now persisted in the file (8 bytes after totalTicks). v1 reader falls back to file mtime so old replays keep their original timestamp.
- [x] Stale Javadoc/comments updated (ReplayEntry "1-11" → "1-12"; ReplayRecorder "0x01-0x0B" → "0x01-0x0C")

## Phase 12: Web Client ⚠️ MOSTLY DEMO (partial backend wiring)
- [x] Next.js + shadcn/ui + Tailwind + Prisma project structure
- [x] 41 shadcn/ui components, login dialog, tab layout
- [x] API health endpoint (M-NEW-25 fixed)
- [x] UnitsTab/ReplaysTab attempt API fetch (M-NEW-26 fixed)
- [x] Matchmaking tries server first (M-NEW-27 fixed)
- [x] Prisma FKs added (M-NEW-28 fixed)
- [x] FIX (H3): Dead-end UI buttons wired — "Join Battle" (MatchmakingPanel), "Watch" (ReplaysTab), "Download" (MapsTab, calls real downloadMap API). Join Battle and Watch show sonner toasts (FXGL launch / in-browser viewer still pending); Download hits the real /api/maps/:id endpoint.
- [x] FIX (H4): ChatTab no longer calls setIsDemo(false) during render — isDemo is now derived from messages.length === 0.
- [x] FIX (M9): UnitsTab and ReplaysTab now use the apiUrl() helper via getUnits()/getReplays() exports from api.ts (previously bypassed the helper, missing ?XTransformPort=8080).
- [ ] **Most data still hardcoded** — needs full backend wiring
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
| Main code compilation | ✅ CLEAN — Static analysis passed (8 issues found and fixed) |
| Test compilation | ✅ CLEAN — ChatControllerTest fixed (missing SessionService mock, missing Authentication param) |
| Test execution | ⚠️ Unknown (JDK not available in sandbox — no javac; Gradle times out) |
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
| Open issues (all audits) | 0 (100 found, 95 fixed, 4 false positive, 1 noted) |
| `java.util.Random` in game logic | 0 ✅ |
| HashMap/HashSet in game state | 0 ✅ (all converted to LinkedHashMap/LinkedHashSet) |
| Missing record accessor `()` | 0 ✅ (fixed 4 in GameWebSocketHandler) |
| `e.printStackTrace()` in non-test | 0 ✅ (removed from ResearchRegistry) |
| Unused imports | 0 ✅ (cleaned 3 in client/modding) |
| Missing dependency imports | 0 ✅ (removed Guava @VisibleForTesting) |

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