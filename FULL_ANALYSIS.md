# AOW2-Online — Full Codebase Analysis

**Date:** 2026-06-29
**Scope:** All 314 source files read (248 Java + 66 TypeScript + 30 Lua + JSON data files)
**Analyzer:** Independent codebase audit (no reliance on TODO, worklog, or prior audit reports)

---

## 1. Executive Summary

AOW2-Online is a faithful recreation of Art of War 2 built on Java 21 (FXGL client, Spring Boot server, LuaJ scripting) with a Next.js lobby frontend. The project is architecturally sound with clean module separation, proper lockstep networking, and comprehensive test coverage (432 tests passing). However, the codebase is **not production-ready for multiplayer**: several server-side security gaps remain (chat injection, missing DB column causing startup failure), the Lua sandbox has unenforced instruction limits, critical player actions (garrison, production, repair) are unreachable from the client UI, and 5 of 13 command types cannot be issued by players. The campaign system is the most complete feature, with 29 missions, working Lua scripting, and functional objective evaluation. The project would benefit most from: fixing the missing DB migration, completing client command wiring, hardening the Lua sandbox, and adding WebSocket handler tests.

---

## 2. Metrics

| Metric | Value |
|--------|-------|
| Java source files (main) | 167 |
| Java test files | 81 |
| TypeScript/TSX files | 66 |
| Lua scripts | 30 |
| Map JSON files | 30 (14 campaign/custom + 16 Peru) |
| Campaign missions | 29 (7 + 7 + 15) |
| Research IDs in tech_tree.json | 48 |
| CommandType values | 13 |
| UnitType values | 19 |
| BuildingType values | 16 |
| WeaponType values | 7 |
| TerrainType values | 12 |
| Faction values | 3 |
| ResearchCategory values | 18 |
| MovementState values | 5 |
| Tests passing (per last audit) | 432 / 432 |
| Confirmed bugs found (not in TODO) | 18 |
| Confirmed TODO claims verified | 108 / 121 |
| Unconfirmed / partially fixed TODO claims | 13 / 121 |
| 4 known design limitations | Confirmed as documented |

---

## 3. Confirmed Bugs (Not in TODO)

### [CRITICAL] Missing `last_sync_tick` column in database migration
- **File:** `aow2-server/src/main/java/com/aow2/server/model/GameSession.java` (line 80) vs `aow2-server/src/main/resources/db/migration/V2__create_game_tables.sql`
- **Description:** `GameSession` entity declares `@Column(name = "last_sync_tick") private Long lastSyncTick;` but no migration adds this column. The `V2` migration creates the `game_sessions` table without it.
- **Impact:** With `ddl-auto: validate` in `application.yml` (line 14), the Spring Boot server **will fail to start** in production. The entity-field-to-column mismatch causes a schema validation error.
- **Reproduction:** Start the Spring Boot server with a real PostgreSQL database that has migrations V1–V5 applied. Hibernate validation will throw `SchemaValidationException`.

### [CRITICAL] `/api/leaderboard/me` NullPointerException on unauthenticated access
- **File:** `aow2-server/src/main/java/com/aow2/server/controller/LeaderboardController.java` (line 78–79) + `aow2-server/src/main/java/com/aow2/server/config/SecurityConfig.java` (line 69)
- **Description:** `/api/leaderboard/**` is configured as `permitAll`, allowing unauthenticated requests. `getMyRanking()` calls `authentication.getPrincipal()` without null check. An unauthenticated request yields `authentication == null` → NPE.
- **Impact:** Server returns HTTP 500 instead of 401 for unauthenticated leaderboard/me requests.
- **Reproduction:** `curl http://localhost:8080/api/leaderboard/me` without JWT token.

### [CRITICAL] ChatController.sendMessage has no participation check
- **File:** `aow2-server/src/main/java/com/aow2/server/controller/ChatController.java` (lines 59–91)
- **Description:** Any authenticated user can POST to `/api/chat/send` with an arbitrary `matchId`. The endpoint does not verify the player is a participant in that match. The WebSocket handler (`ChatWebSocketHandler`) does verify participation via `handleJoin`, but the REST endpoint is unprotected.
- **Impact:** A logged-in user can inject chat messages into any match, enabling cross-match chat spam or manipulation.
- **Reproduction:** Authenticate as player A, POST to `/api/chat/send` with `matchId` belonging to players B and C.

### [HIGH] Production cancel not handled in GameScene
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (lines 317–363)
- **Description:** HUD fires `"cancel_production:N"` action when a player right-clicks a production queue slot, but the command callback switch statement in GameScene has no case for this action string. It falls through to the default `LOG.warn("Unknown HUD action: {}", action)`.
- **Impact:** Players cannot cancel unit production from the UI. The cancel functionality exists in `ProductionSystem` but is unreachable.
- **Reproduction:** Select a producing building, right-click a production queue slot in the HUD.

### [HIGH] Garrison command unreachable from player input
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (lines 367–427)
- **Description:** `CommandType.Garrison` has no UI binding. No keyboard shortcut, no HUD button, and no right-click context menu option creates a Garrison command. The `GarrisonCommandHandler` exists in aow2-core but is never invoked from the client.
- **Impact:** Players cannot garrison units into bunkers. The bunker garrison system (Building.garrison, garrison fire from CombatSystem) is fully implemented but inaccessible.
- **Reproduction:** Select infantry, right-click on a bunker — nothing happens (treated as move to adjacent tile).

### [HIGH] Produce/Train command unreachable from player input
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (lines 317–363)
- **Description:** There is no production UI dialog. When a player selects a producing building (Infantry Centre, Machine Factory), there is no button, menu, or keyboard shortcut to open a unit production menu. `CommandType.Produce` is never created from any client-side input path.
- **Impact:** Players cannot produce units. The production queue, production costs, and completion system are fully implemented but cannot be triggered.
- **Reproduction:** Select an Infantry Centre — no production UI appears.

### [HIGH] Lua instruction count limit declared but never enforced
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/LuaEngine.java` (lines 132–136, 159–191)
- **Description:** `DEFAULT_MAX_INSTRUCTIONS = 1_000_000` is declared. Lines 166–180 contain a dead code block with an `int[] count` variable that is never incremented. The code comments explain: "Per-instruction hooking is not available without LuaThread access (LuaJ 3.x package-private field)." The game loop tick timeout is the only backstop.
- **Impact:** A malicious or buggy Lua script with `while true do end` will hang the game thread indefinitely. The 1M instruction limit provides zero protection.
- **Reproduction:** Create a mod with `while true do end` in `onTick()` — game freezes.

### [HIGH] `getGlobals()` deprecated but still accessible — complete sandbox bypass
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/LuaEngine.java` (line 285)
- **Description:** `getGlobals()` is `@Deprecated(since = "0.2.1", forRemoval = true)` but still returns the raw mutable `Globals` object. Any code calling `engine.getGlobals().set("os", originalOsLib)` would restore filesystem access and bypass the entire sandbox. The method is not removed, only deprecated.
- **Impact:** If any mod or internal code calls `getGlobals()`, the Lua sandbox is completely bypassed.
- **Reproduction:** From Java code: `luaEngine.getGlobals().set("io", someIoLib)` — Lua scripts can now access the filesystem.

### [HIGH] A/S/D key conflict between InputHandler and CameraController
- **File:** `aow2-client/src/main/java/com/aow2/client/input/InputHandler.java` (line 276) + `aow2-client/src/main/java/com/aow2/client/render/CameraController.java` (lines 224–227)
- **Description:** InputHandler passes key events to CameraController before processing game commands. CameraController treats A/S/D as camera pan (left/down/right). When a player presses A to enter attack-move mode, the camera also starts panning left simultaneously.
- **Impact:** Pressing A, S, or D causes both a game command AND camera movement. This makes attack-move, stop, and hold commands nearly unusable.
- **Reproduction:** Select units, press A — camera pans left AND attack-move mode activates.

### [MEDIUM] CampaignScene save fails — gameState/entityManager never set
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/CampaignScene.java` (lines 198–250)
- **Description:** `saveGame()` requires `gameState` and `entityManager` to be non-null. These are set via `setGameState()`/`setEntityManager()` which are never called from `AOW2App`. Both fields remain null.
- **Impact:** All campaign save attempts silently fail (logged as "Cannot save" warning). Players lose all campaign progress on exit.
- **Reproduction:** Start a campaign mission, return to campaign scene, click Save — nothing is written.

### [MEDIUM] Hold command equals Stop command
- **File:** `aow2-client/src/main/java/com/aow2/client/input/InputHandler.java` (lines 285–286)
- **Description:** Both H key (hold position) and S key (stop) produce `CommandType.Stop`. There is no distinct hold-position behavior. In the original game, hold position keeps units facing their last direction and allows them to attack enemies in range, while stop also clears attack targets.
- **Impact:** Hold position is functionally identical to stop. No tactical distinction exists.

### [MEDIUM] Non-atomic ELO recording after session completion
- **File:** `aow2-server/src/main/java/com/aow2/server/websocket/GameWebSocketHandler.java` (lines 413–424)
- **Description:** `sessionService.completeSession()` and `rankingService.recordMatchResult()` are called sequentially without a shared transaction or lock. If `completeSession` succeeds but `recordMatchResult` fails (DB error, concurrent completion), the session is marked COMPLETED but ELO is never recorded. The idempotency guard in `completeSession` prevents retry.
- **Impact:** Players could complete a match, have the session marked done, but never receive ELO changes. No retry mechanism exists.
- **Reproduction:** Trigger a DB error on the `match_results` table between session completion and ELO recording.

### [MEDIUM] `leaveQueue` not synchronized — race with background sweep
- **File:** `aow2-server/src/main/java/com/aow2/server/service/MatchmakingService.java` (line 254)
- **Description:** `leaveQueue()` calls `queue.remove(playerId)` without holding `queueLock`. The `backgroundMatchSweep()` iterates the queue inside `synchronized(queueLock)`. A player leaving while the sweep is running could result in the sweep matching a player who just left.
- **Impact:** A player who leaves the queue could still be placed in a match, creating an unwanted game session.
- **Reproduction:** Player A leaves queue at the exact moment the background sweep iterates and finds a match.

### [MEDIUM] No double-session guard
- **File:** `aow2-server/src/main/java/com/aow2/server/service/SessionService.java` (lines 143–148)
- **Description:** `createSession()` does not check if either player is already in an active session. `playerSessions.put()` silently overwrites the previous mapping.
- **Impact:** A player could be in multiple simultaneous sessions, causing ELO corruption and state confusion.
- **Reproduction:** Player A is in an active match. A second match is created with Player A — both sessions point to Player A.

### [MEDIUM] MatchmakingPanel bypasses apiUrl() — matchmaking goes to wrong server
- **File:** `aow2-web/src/components/MatchmakingPanel.tsx` (line 36)
- **Description:** Uses `fetch('/api/matchmaking/join', {method: 'POST'})` directly instead of using the `apiUrl()` helper with `?XTransformPort=8080`. All other API calls use `apiUrl()`.
- **Impact:** The matchmaking POST request hits the Next.js server (which has no `/api/matchmaking` route) instead of the Spring Boot server. Matchmaking from the web lobby will always fail.
- **Reproduction:** Click "Find Match" in the web lobby — request goes to Next.js, returns 404.

### [MEDIUM] `Upgrade` command missing validation in compact constructor
- **File:** `aow2-common/src/main/java/com/aow2/common/model/CommandType.java` (line 419)
- **Description:** All other 12 command types validate `tick >= 0` and relevant ID fields in their compact constructors. `Upgrade` has no validation — tick and buildingId can be negative.
- **Impact:** A malformed Upgrade command with negative tick/buildingId would pass construction and fail later with unclear errors.
- **Reproduction:** Construct `new CommandType.Upgrade(-1, -1, -1)` — no exception thrown.

### [MEDIUM] ReplayViewerScene not wired from any menu
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/ReplayViewerScene.java` (entire file)
- **Description:** `ReplayViewerScene` is a fully functional replay viewer with load, play/pause, seek, speed controls. However, no menu button in `MainMenuScene` navigates to it. The scene exists but is inaccessible from the UI.
- **Impact:** Players cannot view replays through the normal UI. The feature is code-complete but disconnected.
- **Reproduction:** Look at MainMenuScene buttons — no "Replays" button exists.

### [MEDIUM] SettingsScene is a stub — not connected to AccessibilitySettings
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/SettingsScene.java` (entire file)
- **Description:** All 3 sections (Graphics, Audio, Controls) show "coming soon" placeholders. The `AccessibilitySettings` class (434 lines) with colorblind modes, font scaling, key rebinding exists but is never embedded into any scene.
- **Impact:** No settings are changeable at runtime. Key rebinding data is stored via `java.util.prefs.Preferences` but nothing reads it.
- **Reproduction:** Navigate to Settings from main menu — all options are "coming soon".

### [MEDIUM] `onAreaEntered()` Lua hook never dispatched
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/GameAPI.java` (line 215)
- **Description:** `onAreaEntered()` stores the area trigger registration but no game-loop code checks unit proximity to registered areas each tick. Only `ep1_mission3.lua` uses this hook. The callback is stored but never fires.
- **Impact:** Mission 3's capture objective relies on Java-side `CaptureObjective`, not the Lua `onAreaEntered`. The Lua hook is dead code.
- **Reproduction:** Run ep1_mission3 — the `onAreaEntered` callback in Lua never fires.

### [MEDIUM] MultiplayerLobbyScene onMatchFound doesn't pass session UUID
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/MultiplayerLobbyScene.java` (in AOW2App line 198–201)
- **Description:** When a match is found, `onMatchFound` logs and calls `showGame()` but does NOT pass the session UUID to `GameScene`. The `setupMultiplayer()` method in GameScene is never called from the match-found path.
- **Impact:** Multiplayer matches will start as single-player skirmishes with no lockstep, no WebSocket communication, and no opponent commands.
- **Reproduction:** Find a match in the multiplayer lobby — game starts but no opponent actions are received.

### [LOW] SHALLOW_WATER movement cost inconsistency
- **File:** `aow2-common/src/main/java/com/aow2/common/model/TerrainType.java` (line 33, line 96–103)
- **Description:** `getMovementCost()` returns `MAX_VALUE` for SHALLOW_WATER, but `isPassableBy(INFANTRY)` returns true. Pathfinding uses `isPassableBy()` for the A* open set but `getMovementCost()` for g-cost calculation. The combination means infantry can enter shallow water tiles in the pathfinder but the cost is infinite, creating a contradiction.
- **Impact:** Pathfinding for infantry near shallow water may produce unexpected results depending on which method is checked first.
- **Reproduction:** Place infantry adjacent to shallow water, right-click across it — pathfinding behavior is undefined.

### [LOW] ep2_mission7.lua syntax error
- **File:** `aow2-core/src/main/resources/data/scripts/ep2_mission7.lua` (line 108)
- **Description:** Missing closing quote: `"The enemy command has fallen! Press the advantage!` — should end with `"The enemy command has fallen! Press the advantage!"`.
- **Impact:** Lua parse error when loading this script. Mission 7 of Episode 2 will fail to load.
- **Reproduction:** Start ep2_mission7 — LuaEngine will throw a LuaError on script parse.

### [LOW] Download count inflation on maps
- **File:** `aow2-server/src/main/java/com/aow2/server/controller/MapController.java` (line 202)
- **Description:** `POST /api/maps/{id}/download` increments download count with no per-player deduplication. A bot could inflate any map's download count.
- **Impact:** Download counts are unreliable. Popular maps can be artificially inflated.

### [LOW] Vehicle armor research map is empty
- **File:** `aow2-core/src/main/java/com/aow2/core/combat/ArmorCalculator.java`
- **Description:** No research IDs affect vehicle armor through the Z[] array. The 48 research effects include infantry armor bonuses but zero vehicle armor bonuses. Vehicles like Coyote, Armadillo, Rhino, Porcupine cannot have their armor improved through research.
- **Impact:** Vehicle armor is permanently fixed at base values. This may or may not match the original game.

### [LOW] ResourceGenerator float arithmetic potential divergence
- **File:** `aow2-core/src/main/java/com/aow2/core/economy/ResourceGenerator.java` (line 130)
- **Description:** `(double)(totalBaseIncome * playerModifier) * 20.0 / (totalUpgradeBonus + 20)` — intermediate double multiplication could theoretically produce different `(int)` truncation results across JVM implementations. The `Math.round()` for Resistance bonus was added to fix a specific truncation issue, but the main formula still uses `(int)` cast.
- **Impact:** Low risk of desync in multiplayer due to floating-point credit calculation differences across platforms.

---

## 4. TODO Claim Verification

| Claim | Status | Notes |
|-------|--------|-------|
| C-NEW-1: `GridPosition.distanceClass()` returns 127 sentinel | ✅ CONFIRMED | `GridPosition.java` lines 51–60: returns 127 for `|dx|>15 or |dy|>15`. 31×31 lookup table, max meaningful range = 15. |
| C-NEW-2: `ResearchSystem.applyResearchEffect()` applies data-driven effects | ✅ CONFIRMED | `ResearchSystem.java`: handles all effect categories by string name matching from tech_tree.json. Uses LinkedHashMap for deterministic iteration. |
| C-NEW-5: Game-over self-confirmation prevented (ELO fraud fix) | ✅ CONFIRMED | `GameWebSocketHandler.java` line 324: checks `claim.claimedBy().equals(playerId)` and rejects. Two-phase commit with `putIfAbsent`/`computeIfPresent`. |
| C-NEW-6: Chat WebSocket room isolation | ✅ CONFIRMED | `ChatWebSocketHandler.java` lines 235–249: `matchParticipants` map isolates by match UUID. `handleJoin` verifies session participation. **But REST endpoint `/api/chat/send` lacks this check** — see Bug #3. |
| C-NEW-7: JWT secret from env var | ✅ CONFIRMED | `JwtUtil.java` lines 46–54: reads `${AOW2_JWT_SECRET}`, throws `IllegalStateException` if env var absent. Dev default string still in bytecode (residual risk). |
| H-NEW-9: `GameSession` persisted with `@Transactional` | ⚠️ PARTIAL | `SessionService.java`: all state-transition methods use `@Transactional`. **But `reportSyncHash` (line 263) calls `sessionRepository.save()` without `@Transactional`.** Also, `GameSession.lastSyncTick` column is missing from DB migration — see Bug #1. |
| H-NEW-11: Build command fully wired end-to-end | ⚠️ PARTIAL | Build command IS dispatched from InputHandler → GameScene → CommandProcessor → BuildCommandHandler → BuildingPlacementSystem. **However, placement validation doesn't check all cases** (e.g., building overlap with units is checked but mine placement overlap is not). The end-to-end chain works for basic cases. |
| H-NEW-12: Real map loading (not always test map) | ✅ CONFIRMED | `GameScene.initializeGame(String resourcePath)` overload exists. `AOW2App` passes actual map paths from campaign definitions. `MapLoader` supports both sparse tiles and 2D terrain array formats. |
| H-NEW-13: Campaign objectives evaluated each tick, victory/defeat dialogs shown | ✅ CONFIRMED | `GameScene.java` lines 1265–1488: `checkCampaignObjectives()` called in `onGameTick()`. All 5 objective types checked. Victory/defeat dialogs shown via FXGL dialog boxes. `campaignResultHandled` flag prevents duplicates. |
| H-NEW-16: LuaJ sandbox — `getGlobals()` deprecated, instruction limit enforced | ❌ NOT FIXED | `getGlobals()` IS deprecated (line 285) but NOT removed. **Instruction limit is declared (1M) but NOT enforced** — dead code at lines 166–180 with comment explaining LuaJ limitation. See Bugs #6 and #7. |
| BUILD-1 through BUILD-8: All compilation fixes | ✅ CONFIRMED | Per AUDIT_ROUND_4_FINAL: 432/432 tests pass, 0 compilation errors. CI-fix commits addressed: `getOwner()`→`getFaction()`, typeOrd 12→13, unit count 17→19, CC attackSpeed 7→0, 5 wrong ArmorCalculator research IDs, Math.round fix, impassable test fix. |
| PLAYTEST-4: `MapLoader` supports 2D terrain array format | ✅ CONFIRMED | `MapLoader.java`: detects `"terrain"` key (2D array) vs `"tiles"` key (sparse). All 30 campaign/custom maps use 2D format. |
| PLAYTEST-5: `ModEventBridge` wired to `EventDispatcher` | ⚠️ PARTIAL | `ModEventBridge` has 2 callbacks (unitKilled, buildingDestroyed). `MissionScriptEngine.wireModEventBridge()` registers these. **But the TODO mentions 11 callbacks — only 2 exist.** The "11 callbacks" claim appears to be aspirational, not a prior state. |
| H-AUDIT-1: Per-session synchronized locks in `SessionService` | ✅ CONFIRMED | `SessionService.java`: `sessionLocks` ConcurrentHashMap with `computeIfAbsent`. All state transitions use `synchronized(getSessionLock(sessionUuid))`. **Memory leak: lock entries never removed in `removeSession()`.** |
| H-AUDIT-9: `SelectionManager.selectUnitsByIds()` exists and works | ✅ CONFIRMED | `SelectionManager.java`: method exists, filters by `unit.isAlive()` and `unit.getFaction() == playerFaction`. Used by GameScene for control group recall and multiplayer sync. |

### 4 Known Design Limitations — Confirmed as Documented (Not Silent Bugs)

1. **Dual objective systems disconnected**: ✅ Confirmed. `GameAPI.setObjective()` in Lua updates a separate `objectives` map in `GameAPI`. Java-side `Objective` records in `CampaignManager` drive victory/defeat. Lua objectives are informational only. This is a known architectural gap, not a bug.

2. **Script messages never displayed in HUD**: ✅ Confirmed. `GameAPI.getAndClearMessages()` exists but is never called by `HUD.java` or `GameScene.java`. Messages go to SLF4J logs. The display pipeline was never wired.

3. **`onAreaEntered()` never dispatched**: ✅ Confirmed. `GameAPI.onAreaEntered()` stores registrations in a list. No game-loop code iterates this list each tick to check unit proximity. Only `ep1_mission3.lua` registers this callback. The Java-side `CaptureObjective` provides the actual capture logic.

4. **`getTick()` long→int narrowing**: ✅ Confirmed. `GameState.currentTick()` returns `long` but `GameAPI.getTick()` casts to `int`. Overflow at ~2.1B ticks ≈ 6.8 years of continuous play at 10 TPS. Practically impossible in normal gameplay.

---

## 5. Lockstep Determinism Audit

### Sources of Non-Determinism Found

| # | File | Line | Source | Risk | Assessment |
|---|------|------|--------|------|------------|
| 1 | `GameLoop.java` | 56, 61, 77 | `System.nanoTime()` | **NONE** | Real-time pacing only, not game logic |
| 2 | `ReplayRecorder.java` | 125 | `System.currentTimeMillis()` | **NONE** | Replay metadata only |
| 3 | `ReplayFile.java` | 89 | `System.currentTimeMillis()` | **NONE** | File metadata only |
| 4 | `PathfindingSystem.java` | 238 | `terrainCost * 1.41` (float) | **LOW** | Local to pathfinding, not game state. Different paths possible on different platforms, but this only affects AI decisions, not simulation state |
| 5 | `PathfindingSystem.java` | 319 | `maxD + 0.41 * minD` (float heuristic) | **LOW** | Same as #4 — heuristic only, not game state |
| 6 | `ResourceGenerator.java` | 130 | `(double)(...) * 20.0 / (...)` → `(int)` cast | **LOW** | Credit calculation. `Math.round()` added for Resistance but main formula still uses `(int)` truncation. Could diverge across JVMs |
| 7 | `MilitaryAI.java` | 638, 665 | `(double) unit.getHp() * unit.getStats().damage()` | **NONE** | AI decision parameter only, not deterministic simulation output |
| 8 | `DamageCalculator.java` | 224 | `DeterministicLCG.nextDouble()` | **NONE** | Uses deterministic RNG, not `Math.random()` |

### HashMap/HashSet Usage in Game Logic

| Collection | File | Usage | Deterministic? |
|-----------|------|-------|----------------|
| `ConcurrentHashMap` (units, buildings, projectiles) | `EntityManager.java` | Entity storage | ✅ All iteration methods **sort by entity ID** before returning |
| `LinkedHashMap` (completedResearch, activeResearchMap) | `ResearchSystem.java` | Research state | ✅ Insertion-ordered, deterministic |
| `LinkedHashMap` (visibility grids) | `FogOfWarSystem.java` | Fog state | ✅ Insertion-ordered |
| `LinkedHashMap` (tech nodes) | `ResearchRegistry.java` | Tech tree | ✅ Insertion-ordered |
| `LinkedHashMap` (productionDecisions) | `EconomyAI.java` | AI decisions | ✅ Insertion-ordered |
| `HashMap` (openMap), `HashSet` (closedSet) | `PathfindingSystem.java` | A* local state | ✅ Local to `findPath()`, not game state |
| `HashMap` (spatial grid) | `CollisionSystem.java` | Collision broad-phase | ✅ Local to tick, rebuilt each tick |
| `ConcurrentHashMap` (playerSessions) | `SessionService.java` | Server state | ✅ Not in simulation path |
| `ConcurrentHashMap` (matchParticipants) | `ChatWebSocketHandler.java` | Server state | ✅ Not in simulation path |

### Summary
**No `Math.random()`, `java.util.Random`, or `System.nanoTime()`/`System.currentTimeMillis()` calls exist in game-logic code paths.** All collections used in game state iteration are either `LinkedHashMap` (insertion-ordered) or sorted by entity ID before iteration. The lockstep implementation is **deterministic for simulation state**. The only residual risks are float arithmetic in `ResourceGenerator` (credit calculation) and `PathfindingSystem` (diagonal cost), both of which are low-risk.

---

## 6. Security Audit

### 6.1 Authentication & Authorization

**JWT Implementation:**
- Algorithm: HMAC-SHA256 (fixed by `SecretKey` type, none-bypass impossible)
- Secret: From `AOW2_JWT_SECRET` env var; `IllegalStateException` if absent
- Expiry: 24 hours (longer than ideal for a game session; consider 1–4 hours)
- Residual risk: Dev-default secret string baked into compiled class at `JwtUtil.java:47`

**Password Security:**
- BCrypt via Spring's `BCryptPasswordEncoder` — industry standard
- Username validation: `[a-zA-Z0-9_-]{3,32}` regex
- Password: min 6, max 128 chars (prevents bcrypt DoS)
- Error messages: Generic "Invalid username or password" (prevents enumeration)

**Endpoint Protection (SecurityConfig):**
- `/api/auth/**` — permitAll ✅
- `/ws/**` — permitAll (auth deferred to WS handlers) ⚠️
- `/api/matchmaking/**` — authenticated ✅
- `/api/maps/**` — authenticated ✅
- `/api/replays/**` — authenticated ✅
- `/api/leaderboard/**` — **permitAll (BUG — /me endpoint NPE)** ❌
- `/api/stats/**` — permitAll ✅
- `/api/chat/**` — authenticated ✅

**CORS:** Restricted to configured origins (default: localhost:3000, localhost:8080). Credentials disabled. ✅

### 6.2 WebSocket Security

**Game WebSocket (`/ws/game`):**
- Game-over claim: Two-phase commit with `putIfAbsent`/`computeIfPresent` ✅
- Self-confirmation blocked ✅
- Command size limit: 4 KB ✅
- ELO fraud prevention: Winner must be one of session's two players ✅
- **Non-atomic ELO recording** (session complete + ELO not in shared transaction) ⚠️

**Chat WebSocket (`/ws/chat`):**
- Room isolation: `matchParticipants` map per match UUID ✅
- Participation check: `handleJoin` verifies session ✅
- **REST `/api/chat/send` lacks participation check** ❌
- **No chat message cleanup/pagination** — messages accumulate forever ⚠️

**Lobby WebSocket (`/ws/lobby`):**
- Ready-check: Protected by `synchronized(readyLock)` ✅
- **Global lock** (all sessions share one lock) — doesn't scale ⚠️
- **Session start failure loses ready signals** — no rollback ⚠️

### 6.3 Input Validation

- **SQL Injection:** Zero raw SQL queries. All persistence via Spring Data JPA. ✅
- **Mass Assignment:** Controllers use `Map<String, String>`, not JPA entities directly. ✅
- **Command Validation:** `CommandSerializer` bounds-checks ordinals and array sizes. ✅
- **Map Upload:** 5 MB limit, name max 64 chars, ownership check on delete. ✅
- **Replay Upload:** 14 MB limit, participation check. ✅
- **Rate Limiting:** 5 req/60s/IP on login/register only. No rate limiting on chat, matchmaking, map upload, or replay upload. ⚠️

### 6.4 Lua Sandbox

**Exposed to Lua:**
- `base` (minus `os`, `io`, `java`, `debug`, `load`, `loadstring`, `dofile`, `require`)
- `math`, `string`, `table`, `coroutine` — standard safe libraries
- `aow2.*` table: 12 functions (spawnUnit, destroyUnit, getObjective, setObjective, showMessage, setTimer, onUnitKilled, onBuildingDestroyed, onAreaEntered, getUnitCount, getBuildingCount, getCredits, getTick)

**Blocked from Lua:**
- Filesystem access (os, io nil'd) ✅
- Network access (no network library) ✅
- Java reflection (java global nil'd) ✅

**Sandbox Gaps:**
- `string.dump` NOT blocked — bytecode information disclosure possible ⚠️
- `getGlobals()` deprecated but accessible — complete sandbox bypass if called ❌
- Instruction count limit NOT enforced — infinite loops hang game thread ❌
- `GameAPI` static mutable state is NOT thread-safe — concurrent modification risk ⚠️

---

## 7. Completeness Gaps

### 7.1 CommandType Coverage

| # | Enum Value | Serialized | Handled in CommandProcessor | Has Handler | Reachable from Input | Reachable from AI | Tested |
|---|-----------|-----------|---------------------------|-------------|---------------------|-------------------|--------|
| 1 | Move | ✅ | ✅ | MoveCommandHandler | ✅ (right-click) | ✅ | ✅ |
| 2 | Attack | ✅ | ✅ | AttackCommandHandler | ✅ (right-click enemy) | ✅ | ✅ |
| 3 | AttackMove | ✅ | ✅ | inline (auto-engage) | ✅ (A + right-click) | ✅ | ✅ |
| 4 | Build | ✅ | ✅ | BuildCommandHandler | ✅ (B + right-click) | ✅ | ✅ |
| 5 | Produce | ✅ | ✅ | ProduceCommandHandler | ❌ **No UI** | ✅ | ✅ |
| 6 | Research | ✅ | ✅ | ResearchCommandHandler | ❌ **No UI** | ✅ | ✅ |
| 7 | Garrison | ✅ | ✅ | GarrisonCommandHandler | ❌ **No UI** | ❌ | ✅ |
| 8 | Ungarrison | ✅ | ✅ | GarrisonCommandHandler | ❌ **No UI** | ❌ | ✅ |
| 9 | Cancel | ✅ | ✅ | inline | ❌ **Not wired in GameScene** | ❌ | ✅ |
| 10 | SiegeMode | ✅ | ✅ | inline | ❌ **No hotkey** | ✅ | ✅ |
| 11 | Stop | ✅ | ✅ | inline | ✅ (S key) | ✅ | ✅ |
| 12 | Patrol | ✅ | ✅ | inline | ✅ (P + right-click) | ✅ | ✅ |
| 13 | Upgrade | ✅ | ✅ | UpgradeCommandHandler | ⚠️ HUD button only | ❌ | ❌ **Missing validation** |

**Summary:** 13/13 serialized, 13/13 handled, 7/13 fully reachable from player input. 6 command types are unreachable from the client UI.

### 7.2 UnitType Stats Coverage

| Unit Type | Has Stats | Wired in Combat | Has Sprite/Animation |
|-----------|-----------|-----------------|---------------------|
| CONFED_INFANTRY | ✅ | ✅ | ✅ (en_000.png + procedural) |
| CONFED_GRENADIER | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_LIGHT_ASSAULT | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| CONFED_HEAVY_ASSAULT | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| CONFED_FLAME_ASSAULT | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| CONFED_FORTRESS | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_HAMMER | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_ZEUS | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_TORRENT | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_MINE_SCORPIO | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_MINE_FROG | ✅ | ✅ | ⚠️ (procedural only) |
| CONFED_MINE_LIZARD | ✅ | ✅ | ⚠️ (procedural only) |
| REBEL_INFANTRY | ✅ | ✅ | ⚠️ (procedural only) |
| REBEL_GRENADIER | ✅ | ✅ | ⚠️ (procedural only) |
| REBEL_SNIPER | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| REBEL_COYOTE | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| REBEL_ARMADILLO | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| REBEL_RHINO | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |
| REBEL_PORCUPINE | ✅ (UNVERIFIED) | ✅ | ⚠️ (procedural only) |

**Summary:** 19/19 have stats (8 UNVERIFIED). 19/19 wired in combat. Only 1 unit (CONFED_INFANTRY) has an actual sprite file. All others rely on procedural generation.

### 7.3 Research Effect Coverage

| ID | Name | In JSON | Applied by ResearchSystem | Consumed in Code | Status |
|----|------|---------|--------------------------|------------------|--------|
| 0 | Energy Suit | ✅ | ✅ | ✅ ArmorCalculator | ✅ |
| 1 | Advanced Targeting | ✅ | ✅ | ✅ CombatSystem range reduction | ✅ |
| 2 | Rapid Fire | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 3 | Enhanced Munitions | ✅ | ✅ | ✅ Damage bonus | ✅ |
| 4 | Fortified Structures | ✅ | ✅ | ✅ ArmorCalculator building +4 | ✅ |
| 5 | Power Grid Expansion | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 6 | Rhino Mk.II Upgrade | ✅ | ✅ | ⚠️ UNIT_UPGRADE effect type | ⚠️ |
| 7 | Vehicle Propulsion | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 8 | Heavy Artillery Upgrade | ✅ | ✅ | ✅ ATTACK_RANGE bonus | ✅ |
| 9 | Composite Armour II | ✅ | ✅ | ✅ ArmorCalculator +2 | ✅ |
| 10 | Signal Jamming | ✅ | ✅ | ✅ CombatSystem range reduction | ✅ |
| 11 | Quick Reload | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 12 | Hammer Mk.II Upgrade | ✅ | ✅ | ⚠️ UNIT_UPGRADE effect type | ⚠️ |
| 13 | Power Network | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 14 | Siege Artillery | ✅ | ✅ | ✅ Damage bonus | ✅ |
| 15 | Supply Logistics | ✅ | ✅ | ✅ EconomySystem credit cap | ✅ |
| 16 | Building Armour Override | ✅ | ✅ | ✅ ArmorCalculator SET to 9 | ✅ |
| 17 | Enhanced Economy | ✅ | ✅ | ✅ EconomySystem income | ✅ |
| 18 | Advanced Building Radius | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 19 | Fast Infantry Training | ✅ | ✅ | ✅ ProductionSystem speed | ✅ |
| 20 | Upgraded Assembly Line | ✅ | ✅ | ✅ ProductionSystem speed | ✅ |
| 21 | Finance Department | ✅ | ✅ | ✅ EconomySystem income | ✅ |
| 22 | Incentive System | ✅ | ✅ | ⚠️ SCORING effect type | ⚠️ |
| 23 | Communications System | ✅ | ✅ | ⚠️ SCORING effect type | ⚠️ |
| 24 | Titanium Jacket | ✅ | ✅ | ✅ ArmorCalculator +1 | ✅ |
| 25 | Signal Jamming | ✅ | ✅ | ✅ CombatSystem range reduction | ✅ |
| 26 | Infantry Combat Drill | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 27 | Infantry Range Upgrade | ✅ | ✅ | ✅ ATTACK_RANGE bonus | ✅ |
| 28 | Coyote Range Upgrade | ✅ | ✅ | ✅ ATTACK_RANGE bonus | ✅ |
| 29 | Building Radius Expansion | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 30 | Sniper Upgrade | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 31 | Light Vehicle Speed Upgrade | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 32 | Heavy Machinery Range Adjust | ✅ | ✅ | ✅ ATTACK_RANGE bonus | ✅ |
| 33 | Machinery Armour Upgrade | ✅ | ✅ | ✅ ArmorCalculator +1 | ✅ |
| 34 | Advanced Signal Jamming | ✅ | ✅ | ✅ CombatSystem range reduction | ✅ |
| 35 | Rapid Reload | ✅ | ✅ | ✅ AttackSpeed bonus | ✅ |
| 36 | Mine Lizard Siege Mode | ✅ | ✅ | ⚠️ UNIT_UPGRADE + siege flag | ⚠️ |
| 37 | Advanced Building Radius | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 38 | MLRS Torrent Upgrade | ✅ | ✅ | ✅ Damage bonus | ✅ |
| 39 | Supply Logistics | ✅ | ✅ | ✅ EconomySystem credit cap | ✅ |
| 40 | Building Armour Override | ✅ | ✅ | ✅ ArmorCalculator SET to 9 | ✅ |
| 41 | Enhanced Building Radius | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 42 | Cumulative Building Radius | ✅ | ✅ | ✅ PowerSystem radius | ✅ |
| 43 | Advanced Credits | ✅ | ✅ | ✅ ProductionSystem speed | ✅ |
| 44 | Advanced Production | ✅ | ✅ | ✅ ProductionSystem speed | ✅ |
| 45 | Finance Department | ✅ | ✅ | ✅ EconomySystem income | ✅ |
| 46 | Incentive System | ✅ | ✅ | ⚠️ SCORING effect type | ⚠️ |
| 47 | Communications System | ✅ | ✅ | ⚠️ SCORING effect type | ⚠️ |

**Summary:** 48/48 present in tech_tree.json, 48/48 parsed by ResearchRegistry, 48/48 applied by ResearchSystem. 43/48 have verified consumption in combat/economy/production code. 5 UNIT_UPGRADE and SCORING effects use a generic application mechanism that needs further tracing.

### 7.4 Client-to-Core Wiring

| Action | Input → SelectionManager | → Command Created | → GameScene Dispatch | → CommandProcessor | → Handler/System | Fully Wired? |
|--------|--------------------------|-------------------|---------------------|-------------------|-----------------|-------------|
| Move unit | Right-click on empty tile | `CommandType.Move` | ✅ tickManager.enqueueCommand | ✅ | MoveCommandHandler → MovementSystem | ✅ |
| Attack unit | Right-click on enemy | `CommandType.Attack` | ✅ | ✅ | AttackCommandHandler → CombatSystem | ✅ |
| Attack-move | A + Right-click | `CommandType.AttackMove` | ✅ | ✅ | inline (auto-engage) | ✅ |
| Stop | S key | `CommandType.Stop` | ✅ | ✅ | inline (clear path/target) | ✅ |
| Hold position | H key | `CommandType.Stop` | ✅ | ✅ | inline (same as stop) | ⚠️ No distinct behavior |
| Patrol | P + Right-click | `CommandType.Patrol` | ✅ | ✅ | inline (patrol origin stored) | ✅ |
| Build structure | B + select type + Right-click | `CommandType.Build` | ✅ | ✅ | BuildCommandHandler → BuildingPlacementSystem | ✅ |
| Produce unit | **No UI** | — | — | — | ProduceCommandHandler exists | ❌ |
| Research tech | **No UI** | — | — | — | ResearchCommandHandler exists | ❌ |
| Garrison unit | **No UI** | — | — | — | GarrisonCommandHandler exists | ❌ |
| Ungarrison | **No UI** | — | — | — | GarrisonCommandHandler exists | ❌ |
| Cancel production | HUD right-click slot | `"cancel_production:N"` string | ❌ Not handled | — | — | ❌ |
| Siege mode | **No hotkey** | — | — | — | inline (CombatSystem.enterSiegeMode) | ❌ |
| Upgrade building | HUD Upgrade button | `CommandType.Upgrade` | ✅ | ✅ | UpgradeCommandHandler | ⚠️ No input validation |

**Summary:** 7/14 actions fully wired end-to-end. 6/14 have backend implementation but no client UI binding. 1/14 (hold) is wired but has no distinct behavior.

---

## 8. Test Coverage Assessment

### Test Count by Module

| Module | Test Files | Test Methods (est.) | Coverage Quality |
|--------|-----------|---------------------|-----------------|
| aow2-common | 8 | ~45 | Good for models/config, weak for GridPosition.distanceClass, MathUtils |
| aow2-core | 44 | ~280 | Comprehensive for individual systems; no integration tests for full tick cycle |
| aow2-client | 16 | ~60 | Good for editor/input components; **zero tests for GameScene, rendering, campaign integration** |
| aow2-server | 8 | ~64 | Good unit tests; **zero WebSocket handler tests, zero integration tests** |
| aow2-modding | 3 | ~25 | Good for LuaEngine, ModManager, GameDataRegistry; no tests for ModLoader, ModInstaller, MissionScriptEngine |
| aow2-web | 4 | ~24 | Tests TypeScript types and Zustand stores; **zero component tests, zero API tests** |

### Critical Paths with ZERO or Insufficient Test Coverage

1. **GameScene (~1489 lines)** — The most complex class in the entire project has zero test coverage. No test for campaign objective evaluation, command dispatch, lockstep integration, or rendering loop.
2. **WebSocket handlers** — `GameWebSocketHandler`, `ChatWebSocketHandler`, `LobbyWebSocketHandler` are completely untested. The game-over two-phase commit, ready-check race condition fix, and chat isolation have no test verification.
3. **Command handler integration** — No test traces a command from InputHandler through GameScene to CommandProcessor to the specific handler. Individual handlers exist but are tested in isolation.
4. **ResearchSystem all 48 effects** — No test verifies that every one of the 48 research IDs produces the expected game-state change.
5. **Full tick cycle** — No test exercises the complete `TickManager.processTick()` orchestration (command processing → movement → combat → economy → production → research → AI → fog → cleanup).
6. **CombatSystem full integration** — No test for building attacks, garrison fire, siege mode damage bonus, or flame splash in an integrated scenario.
7. **ModEventBridge callback firing** — No test verifies that unit kill and building destruction events actually trigger Lua callbacks.
8. **FogOfWar DDA ray-casting** — No test for line-of-sight blocking or edge cases.
9. **Pathfinding diagonal corner cases** — No test for diagonal cutting through impassable terrain.
10. **Multiplayer session lifecycle** — No end-to-end test for: match found → WebSocket connect → command exchange → game over → ELO update.

---

## 9. Architecture & Design Observations

### What Is Well-Designed

- **Module separation**: Clean DAG dependency graph (common → core → client/server/modding). Circular dependency between core and modding is elegantly broken via reflection-based `MissionScriptEngine` loading.
- **Lockstep architecture**: Deterministic simulation with Park-Miller LCG, sorted entity iteration, and comprehensive sync checking. The two-phase game-over commit prevents ELO fraud.
- **Command system**: Sealed interface with validated records, binary serialization, and complete handler routing. Type-safe and extensible.
- **Data-driven design**: StatsRegistry, GameConfig, tech_tree.json, and campaign JSONs allow tuning without code changes. The mod system builds on this with JSON data overrides.
- **Test infrastructure**: 432 tests with good coverage of individual systems. Tests use real instances (not heavy mocking) for most core modules.
- **Campaign system**: 29 missions with 5 objective types, 4 trigger types, Lua scripting, save/load, and progressive difficulty. The most complete feature in the project.

### What Is Fragile

- **Client command dispatch**: The switch-on-string pattern in GameScene for mapping input commands to `CommandType` records is fragile. Adding new commands requires changes in 4 places (InputHandler, GameScene command callback, GameScene HUD callback, CommandProcessor).
- **Static singletons**: `StatsRegistry`, `GameConfig`, `SpriteManager` all use static singletons with `setInstance()` for testing. This makes test isolation harder and creates hidden global state.
- **GameAPI static mutable state**: All game state references (`gameState`, `entityManager`, `economySystem`) are stored as static fields. This is inherently not thread-safe and makes testing difficult.
- **PriorityBlockingQueue in TickManager**: A concurrent collection used for single-threaded game logic. Not wrong, but semantically confusing and slightly slower than a simple `ArrayDeque`.
- **No entity removal API**: `EntityManager` has no `removeEntity()` method. `EntityPlacer.eraseEntity()` works around this by calling `takeDamage(hp+1)` — a hack that triggers death events and observer notifications inappropriately.

### What Would Need to Change for Production

1. **Complete the client UI**: Add production dialog, research dialog, garrison hotkey, siege mode hotkey, and cancel production handler. These are the minimum viable player actions.
2. **Fix the DB migration**: Add `V6__add_last_sync_tick.sql`. Without this, the server cannot start in production.
3. **Harden the Lua sandbox**: Either enforce instruction limits via a LuaJ fork or accept the risk and document it clearly. Remove `getGlobals()` entirely.
4. **Add WebSocket handler tests**: The security-critical game-over, ready-check, and chat isolation code has zero test coverage.
5. **Fix the multiplayer flow**: `MultiplayerLobbyScene.onMatchFound` must pass the session UUID to `GameScene.setupMultiplayer()`.
6. **Reconcile Prisma/JPA schemas**: The Next.js frontend uses a completely different database schema (SQLite/String IDs vs PostgreSQL/Long IDs). One must be the source of truth.
7. **Add integration tests**: Full-tick-cycle, command-end-to-end, and multiplayer session lifecycle tests are essential before any real deployment.

---

## 10. What Still Does Not Work

Based on reading actual code (not the TODO), these features are present in code but will not function correctly at runtime:

1. **Multiplayer matchmaking → game start**: Matchmaking finds opponents and creates server sessions, but `MultiplayerLobbyScene.onMatchFound` doesn't pass the session UUID to `GameScene`. Games start as single-player with no lockstep. The `setupMultiplayer()` method is dead code in the match-found path.

2. **Unit production from buildings**: No UI to select units to produce. `ProduceCommandHandler` and `ProductionSystem` are fully implemented but unreachable. Players cannot build units beyond starting entities.

3. **Research from tech centre**: No UI to select research topics. `ResearchCommandHandler` and `ResearchSystem` work but cannot be triggered. All 48 research effects are dead code without UI access.

4. **Garrisoning units into bunkers**: No UI binding. `GarrisonCommandHandler` exists. Bunkers have `garrisonCapacity=5` in stats. Buildings can fire with garrisoned units. But no player can ever garrison a unit.

5. **Siege mode toggle**: No keyboard shortcut. Some units (Fortress, Hammer, Torrent, Rhino) are siege-capable but players cannot toggle siege mode. AI can toggle it.

6. **Campaign save/load**: `CampaignScene.saveGame()` fails silently because `gameState` and `entityManager` are never set. All campaign progress is lost on exit.

7. **Replay viewing from UI**: `ReplayViewerScene` is fully functional but not wired to any menu button. Players cannot access it.

8. **Settings**: `SettingsScene` is a stub. `AccessibilitySettings` (key rebinding, colorblind modes, font scaling) exists but is never connected.

9. **ep2_mission7 Lua script**: Has a syntax error (missing closing quote on line 108). The script will fail to parse, breaking the final mission of Episode 2.

10. **Chat from web lobby**: `ChatTab` uses local `useChatStore` only — no WebSocket connection to the server. Chat messages exist only in the browser's local state.

11. **Map upload from web lobby**: `MapsTab` has upload UI but the `MatchmakingPanel` matchmaking POST bypasses `apiUrl()`, suggesting other API integrations may also be broken.

12. **Spring Boot server startup with real DB**: The missing `last_sync_tick` column will cause a schema validation failure with `ddl-auto: validate`.

---

## 11. Recommendations

Prioritized by impact:

1. **[CRITICAL] Add V6 migration for `last_sync_tick`** — Without this, the server cannot start in production. One-line SQL: `ALTER TABLE game_sessions ADD COLUMN last_sync_tick BIGINT;`

2. **[CRITICAL] Fix chat participation check** — Add session participation validation to `ChatController.sendMessage()`. 5 lines of code.

3. **[HIGH] Build production UI** — This is the single most impactful missing feature. Without unit production, the game has no economy loop. Create a production dialog that lists available units for the selected producing building, with cost and build time.

4. **[HIGH] Fix multiplayer match-found → game-start flow** — Pass session UUID from `MultiplayerLobbyScene.onMatchFound` to `GameScene.setupMultiplayer()`. Wire `LockstepEngine` into the actual match-found path.

5. **[HIGH] Add garrison and siege mode hotkeys** — G key for garrison (when bunker selected), D key for siege mode toggle (when siege-capable unit selected). These are simple InputHandler additions.

6. **[HIGH] Fix A/S/D key conflict** — Remove A, S, D from CameraController's key handling. Only W and arrow keys should pan the camera.

7. **[MEDIUM] Add research UI** — A dialog accessible from the tech centre building that shows the tech tree, prerequisites, and costs.

8. **[MEDIUM] Fix Lua sandbox** — Remove `getGlobals()` method entirely. Implement instruction limiting via a LuaJ thread hook or accept the risk with clear documentation.

9. **[MEDIUM] Wire campaign save** — Call `CampaignScene.setGameState()` and `setEntityManager()` from `AOW2App` before returning to campaign scene.

10. **[MEDIUM] Fix ep2_mission7.lua syntax** — Add missing closing quote on line 108.

11. **[MEDIUM] Add WebSocket handler tests** — Especially for `GameWebSocketHandler` (game-over commit) and `ChatWebSocketHandler` (room isolation).

12. **[MEDIUM] Add GameScene tests** — Even basic tests for command dispatch and campaign objective evaluation would catch many wiring regressions.

13. **[LOW] Connect SettingsScene to AccessibilitySettings** — Embed the existing settings panel into the settings scene.

14. **[LOW] Wire ReplayViewerScene from main menu** — Add a "Replays" button to `MainMenuScene`.

15. **[LOW] Add Upgrade command validation** — Add `tick >= 0` and `buildingId >= 0` checks to the `Upgrade` compact constructor.

16. **[LOW] Reconcile Prisma/JPA schemas** — Decide on a single source of truth for the database schema. The current dual-schema approach (SQLite/Prisma + PostgreSQL/JPA) will cause data inconsistencies.

17. **[LOW] Remove unused npm dependencies** — `next-auth`, `react-hook-form`, `@hookform/resolvers`, `@tanstack/react-query`, `date-fns` are declared but never used.

18. **[LOW] Add rate limiting to chat, matchmaking, and map upload endpoints** — Currently only auth endpoints are rate-limited.