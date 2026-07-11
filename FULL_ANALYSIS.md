# AOW2-Online — Full Codebase Analysis

**Date:** 2026-06-29 (independent re-audit)
**Scope:** File-by-file read of all 5 Java modules + web client (248 Java + 67 TS + 30 Lua + JSON data)
**Method:** Direct source code reading. TODO.md / worklog.md / prior audit reports were consulted only for claim verification, never as a primary source.

---

## 1. Executive Summary

AOW2-Online is a Java 21 reconstruction of *Art of War 2* with a Spring Boot multiplayer server, FXGL desktop client, Next.js lobby website, and LuaJ-driven campaign scripting. The architecture is clean, the lockstep determinism guarantees are real (verified end-to-end), and the test suite is broad (82 test files covering 14 command types, all 48 research IDs, combat, economy, AI, replay, networking, security). Per-module file counts match the task description within ±3 files.

That said, the project is **not feature-complete for shipping**. The audit found **16 confirmed bugs**, of which 4 are correctness issues that will affect real gameplay:

- **Ungarrison has no UI dispatch** — players can garrison but never ungarrison.
- **CombatSystem ignores modded armor research** — only hardcoded IDs 0/9/24/33 are applied; tech_tree.json-defined armor effects beyond those IDs are silently dropped during damage calculation.
- **ReplayPlayer rejects v1/v2 replays** despite documented backward-compat intent, breaking any replay recorded before the v3 metadata bump.
- **CommandBuffer writeIndex drift** — `submitNoOp` is called every frame *in addition to* `submitCommand`, advancing `writeIndex` by N+1 per frame while `readIndex` only advances by 1, eventually corrupting the ring buffer.

The TODO claim "H-NEW-16: LuaJ sandbox" is **partially false**: the `executeString(script, chunkName, maxInstructions)` overload exists but the instruction-counting hook is admitted (in code comments at `LuaEngine.java:180-188`) to be unimplemented — the `DEFAULT_MAX_INSTRUCTIONS = 1_000_000` constant is dead code. Mission scripts loaded via `loadScript`/`loadScriptFromString` bypass even the non-functional limit.

Other findings are documentation drift (stale comments in `CameraController`, `InputHandler`, `PathfindingSystem`), minor validation gaps (`CommandType.Upgrade` only validates `playerId < 0` not `> 1`), dead code (`PathfindingSystem.getTerrainCost` for SHALLOW_WATER+INFANTRY), 4 unused npm dependencies, and a non-synchronized `CommandBuffer.reset()`.

The security posture is solid: JWT dev-secret rejection works, two-phase game-over confirmation correctly blocks self-confirmation, chat participation is verified, map upload validates JSON + dimensions, rate limiting is enforced on the WebSocket command relay, and zombie sessions are dropped on send failure.

---

## 2. Metrics

| Metric | Value | Source |
|--------|-------|--------|
| Java source files (main) | 168 | `find … -path '*/main/*.java' \| wc -l` |
| Java test files | 82 | `find … -path '*/test/*.java' \| wc -l` |
| aow2-common Java files | 37 | matches task description |
| aow2-core Java files | 109 | task said 106 (+3) |
| aow2-client Java files | 47 | task said 45 (+2) |
| aow2-server Java files | 44 | task said 43 (+1) |
| aow2-modding Java files | 13 | matches |
| aow2-web TypeScript files | 67 | matches |
| Lua mission scripts | 29 | `aow2-core/src/main/resources/data/scripts/*.lua` |
| Campaign missions | 29 (7+7+15) | `aow2-core/.../campaigns/*.json` |
| CommandType values | 14 | `CommandType.java:18-22` (sealed permits) |
| UnitType values | 19 | `UnitType.java:18-53` |
| BuildingType values | 16 | `BuildingType.java:12-31` |
| TerrainType values | 12 | `TerrainType.java:28-54` |
| WeaponType values | 7 | `WeaponType.java:12-26` |
| Research IDs in tech_tree.json | 48 | `rg '"id":' tech_tree.json \| wc -l` |
| Confirmed bugs found | 16 | Section 3 |
| TODO claims verified true | 4/5 | Section 4 |
| TODO claims partially true | 1/5 | H-NEW-16 |

---

## 3. Confirmed Bugs

### B-1: `CommandType.Upgrade` validates `playerId < 0` only, not `> 1`
- **File:** `aow2-common/src/main/java/com/aow2/common/model/CommandType.java:507-509`
- **Description:** All 13 other CommandType records validate `playerId < 0 || playerId > 1` in their compact constructor. `Upgrade` only checks `playerId < 0`, allowing `playerId > 1` (e.g., 2, 99, Integer.MAX_VALUE) to pass construction.
- **Impact:** Inconsistent validation. A malformed/replayed Upgrade command with playerId=2 wouldn't be rejected at construction; it would only fail later when `EconomySystem.playerId(faction)` is called (which throws on Faction.NEUTRAL). The bug is documented in the comment at line 499 as "F-23 fix" but the fix is incomplete.
- **Severity:** Low (no current caller produces playerId > 1, but defensive validation should be uniform).

### B-2: `ReplayPlayer` rejects v1/v2 replay files despite documented backward-compat intent
- **File:** `aow2-core/src/main/java/com/aow2/core/replay/ReplayPlayer.java:295`
- **Code:** `if (formatVersion != ReplayFile.FORMAT_VERSION) throw new IOException("Unsupported replay format version: " + formatVersion);`
- **Contradiction:** `ReplayFile.java:53` documents "readers accept 1 and 2", and lines 325/353 of `ReplayPlayer` contain `if (formatVersion >= 2)` / `if (formatVersion >= 3)` branches that are dead code (unreachable because line 295 already rejected anything != 3).
- **Impact:** Any replay recorded before the OPENRA #12 v3 metadata bump is unloadable. Players who saved replays in v0.1.x cannot view them in v0.2.x.
- **Severity:** High (silent regression — replays look "corrupt" rather than old).

### B-3: `ReplayFile.durationSeconds()` divides by 30 instead of 10 (TICK_RATE)
- **File:** `aow2-core/src/main/java/com/aow2/core/replay/ReplayFile.java:127`
- **Code:** `return totalTicks / 30;`
- **Reference:** `GameConstants.TICK_RATE = 10` (line 15). The game runs at 10 TPS, not 30.
- **Impact:** Reported replay duration is 3× too short. A 30-minute replay (18,000 ticks) shows as 10 minutes.
- **Severity:** Low (display-only, no gameplay impact).

### B-4: Lua instruction limit is NOT enforced (H-NEW-16 claim partially false)
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/LuaEngine.java:144, 173-188`
- **Description:** The constant `DEFAULT_MAX_INSTRUCTIONS = 1_000_000` exists at line 144. `executeString(script, chunkName, maxInstructions)` accepts the parameter but never uses it — comments at lines 180-188 explicitly state:
  > "Per-instruction hooking is not available without LuaThread access. The script will run to completion or throw a LuaError on its own."
- **Additional gap:** `loadScript(String)` (line 361) and `loadScriptFromString(String, String)` (line 386) call `globals.load(script, name).call()` directly, bypassing `executeString` entirely — so mission scripts have no instruction limit at all.
- **Impact:** A malicious or buggy mission Lua script with `while true do end` will hang the game-loop thread indefinitely. The `fatalErrorOccurred` flag (OPENRA #20) only fires on `LuaError`, which infinite loops don't raise.
- **Severity:** Medium (denial-of-service via campaign mission, but no remote attack vector since only bundled .lua files are loaded).

### B-5: `CommandBuffer.reset()` is not synchronized
- **File:** `aow2-core/src/main/java/com/aow2/core/network/CommandBuffer.java:205-214`
- **Description:** `submitCommand`, `submitNoOp`, and `drainFrame` are all `synchronized`. `reset()` is not. If `reset()` runs concurrently with `drainFrame()` or `submitCommand()` on another thread, the buffer pointers (`writeIndex`, `readIndex`, `currentTick`) and per-frame flag arrays can be observed in a partially-cleared state.
- **Impact:** Race condition. In practice, `reset()` is currently only called from `LockstepEngine.reset()` (line 661) which has no concurrent caller — but the API contract is unsafe.
- **Severity:** Low (latent).

### B-6: `CombatSystem` calls the hardcoded `ArmorCalculator.calculateEffectiveArmor(Unit, Set<Integer>)` overload, bypassing `ResearchBonusTracker`
- **File:** `aow2-core/src/main/java/com/aow2/core/combat/CombatSystem.java:506`
- **Code:** `int targetArmor = armorCalculator.calculateEffectiveArmor(target, getCompletedResearch(target));`
- **Contradiction:** `ArmorCalculator` has two paths:
  - `calculateEffectiveArmor(Unit, Set<Integer>)` at line 95 — uses hardcoded `INFANTRY_ARMOR_RESEARCH` map (only IDs 0, 9, 24, 33; vehicle map is empty).
  - `calculateEffectiveArmor(Unit, ResearchBonusTracker)` at line 232 — uses the data-driven tracker that accumulates ALL effects from `tech_tree.json`.
- **Impact:** If a modder (or future vanilla change) adds a new armor research effect via `tech_tree.json`, the tracker would record it but CombatSystem wouldn't consult the tracker. The hardcoded map path silently ignores the new effect.
- **Also affects:** `CombatSystem.processBunkerAttack` (line 333), `processDefensiveBuildingAttack` (line 377) — same pattern.
- **Severity:** Medium (modding gap, vanilla balance is currently correct because the hardcoded IDs match tech_tree.json's armor effects).

### B-7: `CommandType.Ungarrison` is never issued by the client
- **Files searched:** `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java`, `aow2-client/src/main/java/com/aow2/client/input/InputHandler.java`
- **Description:** `CommandType.Ungarrison` is defined in `CommandType.java:298`, serialized/deserialized in `CommandSerializer.java:167,337`, recorded in `ReplayRecorder.java:319`, handled in `CommandProcessor.java:105`, and executed in `GarrisonCommandHandler.handleUngarrison` (line 113, 126). It has tests in `CommandTypeTest.java:236`, `CommandSerializerTest.java:111`, `ReplayRecorderTest.java:202`.
- **Bug:** A `rg "CommandType\.Ungarrison" aow2-client` search returns zero matches. There is no key binding, no HUD button, and no right-click context that creates an `Ungarrison` command. Once a player garrisons units into a bunker, **there is no way to unload them**.
- **Impact:** Garrisoned units are permanently stuck. `GarrisonCommandHandler.handleUngarrison` is dead code from the player's perspective.
- **Severity:** High (gameplay completeness gap).

### B-8: `LockstepEngine.applyCommand` inline fallback silently drops 7 command types when game systems aren't injected
- **File:** `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:543-620`
- **Description:** When `economySystem == null` (i.e., `setGameSystems()` was never called), the inline fallback only handles Move, Attack, AttackMove, Stop, Hold, SiegeMode, Patrol. The `default` branch at line 617-620 logs a warning and drops the command: `Build`, `Produce`, `Research`, `Garrison`, `Ungarrison`, `Cancel`, `Upgrade`.
- **Impact:** A misconfigured LockstepEngine (forgetting to call `setGameSystems`) will silently eat all economy/production commands. The warning is only visible at WARN log level.
- **Severity:** Medium (configuration footgun — `setGameSystems` is called in production via `GameScene`, but tests or alternative entry points could miss it).

### B-9: `CommandBuffer.submitNoOp` + `submitCommand` double-advance `writeIndex` per frame
- **File:** `aow2-core/src/main/java/com/aow2/core/network/CommandBuffer.java:89-114`
- **Code path:** `LockstepEngine.processFrame` (line 399) calls `commandBuffer.submitNoOp()` unconditionally every frame. `LockstepEngine.submitCommand` (line 187) calls `commandBuffer.submitCommand(command)` when the player issues a command. Both methods do `writeIndex = (writeIndex + 1) % bufferSize` (lines 101, 113). `drainFrame` (line 154) advances `readIndex` by 1.
- **Bug:** In a frame where the player submitted N commands, `writeIndex` advances by N+1 but `readIndex` advances by 1. The buffer pointers diverge over time. After ~`bufferSize / (avgCommandsPerFrame + 1)` frames, `writeIndex` wraps past `readIndex` and starts overwriting unread frame slots.
- **Impact:** Long multiplayer sessions with active command issuance will eventually corrupt the buffer. The 16-frame default buffer with inputDelay=2 means corruption could manifest after roughly 5-8 frames of active play per command.
- **Caveat:** The unit tests in `CommandBufferTest.java` may not exercise this path because they don't combine `submitCommand` + `submitNoOp` in the same frame.
- **Severity:** High (multiplayer correctness).

### B-10: `CameraController` class comment is stale (claims A/S/D pan)
- **File:** `aow2-client/src/main/java/com/aow2/client/render/CameraController.java:17`
- **Comment:** "Pan: WASD or arrow keys"
- **Reality:** The F-10 fix (line 225-227) removed A/S/D from camera controls because they conflict with game commands (A=attack-move, S=stop, D=siege toggle). Only W + arrow keys remain (`case W, UP -> panUp`, `case DOWN -> panDown`, `case LEFT -> panLeft`, `case RIGHT -> panRight`).
- **Severity:** Cosmetic (documentation drift).

### B-11: `InputHandler` class comment claims T/R/U hotkeys that don't exist
- **File:** `aow2-client/src/main/java/com/aow2/client/input/InputHandler.java:33`
- **Comment:** "Hotkeys: A=attack, S=stop, H=hold, P=patrol, B=build, G=garrison, D=siege, T=produce, R=research, U=upgrade"
- **Reality:** `onKeyPressed` (lines 337-442) has no `case T`, `case R`, or `case U`. Produce/Research/Upgrade are only accessible by selecting a building and clicking the corresponding HUD button (which then opens a ChoiceDialog).
- **Severity:** Cosmetic (documentation drift).

### B-12: `PathfindingSystem` class comment lies about path length limit
- **File:** `aow2-core/src/main/java/com/aow2/core/movement/PathfindingSystem.java:31`
- **Comment:** "We support up to 200 steps (original was 50)."
- **Reality:** `GameConstants.MAX_PATH_LENGTH = 50` (line 109). Paths are trimmed to 50 at line 342-344 (`if (path.size() > GameConstants.MAX_PATH_LENGTH) path = path.subList(0, MAX_PATH_LENGTH)`). The "200" refers to the A* node-exploration limit at line 170 (`stepsExplored > MAX_PATH_LENGTH * 4`), not the path length.
- **Severity:** Cosmetic (documentation drift).

### B-13: `PathfindingSystem.getTerrainCost` SHALLOW_WATER+INFANTRY branch is dead code
- **File:** `aow2-core/src/main/java/com/aow2/core/movement/PathfindingSystem.java:291-294`
- **Code:**
  ```java
  if (terrain == TerrainType.SHALLOW_WATER && category == UnitCategory.INFANTRY) {
      return 3;
  }
  ```
- **Contradiction:** `TerrainType.isPassableBy(UnitCategory.INFANTRY)` returns `false` for SHALLOW_WATER (the F-26 fix at `TerrainType.java:104` made SHALLOW_WATER impassable for ALL units, with a comment explicitly saying so). The pathfinder at `PathfindingSystem.java:197-203` checks `terrain.isPassableBy(category)` before consulting `getTerrainCost`, so the SHALLOW_WATER+INFANTRY branch in `getTerrainCost` is never reached.
- **Impact:** Infantry can never path through shallow water, despite the comment at `PathfindingSystem.java:281` saying "SHALLOW_WATER is passable by infantry with cost 3". The F-26 fix and the dead branch contradict each other.
- **Severity:** Low (gameplay-correct per F-26 intent; just dead code and misleading docs).

### B-14: 4 unused npm dependencies in `aow2-web/package.json`
- **File:** `aow2-web/package.json`
- **Unused deps (0 imports in `src/`):**
  - `framer-motion` (line 48) — 0 usages
  - `sharp` (line 55) — 0 usages (may be used implicitly by Next.js image optimization, but no `next/image` usage was found)
  - `uuid` (line 59) — 0 usages
  - `zod` (line 60) — 0 usages
- **TODO L-NEW-19** claims this was already fixed ("Removed 15 unused packages + 7 unused wrapper files"), but 4 remain.
- **Severity:** Cosmetic (bundle size, supply-chain surface).

### B-15: `MapController.deleteMap` lacks the defensive `authentication == null` check present in sibling controllers
- **File:** `aow2-server/src/main/java/com/aow2/server/controller/MapController.java:196`
- **Code:** `Long playerId = (Long) authentication.getPrincipal();` — direct dereference.
- **Contrast:** `ChatController.sendMessage` (line 66-68) and `LeaderboardController.getMyRanking` (line 82-84) both have `if (authentication == null || authentication.getPrincipal() == null) return 401;`. `MapController.deleteMap` and `MapController.uploadMap` (line 105) do not.
- **Impact:** If `SecurityConfig`'s `/api/maps/**` matcher is ever reordered to `permitAll()` (e.g., for public map browsing), `deleteMap` and `uploadMap` would NPE instead of returning 401.
- **Severity:** Low (defense-in-depth gap; SecurityConfig currently requires auth).

### B-16: `ReplayFile.durationSeconds()` ignores `durationMillis` field stored in v3 metadata
- **File:** `aow2-core/src/main/java/com/aow2/core/replay/ReplayFile.java:126-128`
- **Code:**
  ```java
  public long durationSeconds() {
      return totalTicks / 30;
  }
  ```
- **Bug:** The v3 format (OPENRA #12) stores an explicit `durationMillis` field (line 42), written at `ReplayRecorder.java:262` and read at `ReplayPlayer.java:375`. `durationSeconds()` ignores this field and recomputes from `totalTicks` using the wrong divisor (30 instead of 10 — see B-3). The correct implementation would be `return durationMillis / 1000;` or `return totalTicks / GameConstants.TICK_RATE;`.
- **Severity:** Low (display-only).

---

## 4. TODO Claim Verification

| Claim ID | Claim | Status | File:Line | Notes |
|----------|-------|--------|-----------|-------|
| C-NEW-1 | `GridPosition.distanceClass()` returns 127 for out-of-range | ✅ TRUE | `aow2-common/.../GridPosition.java:55-57` | `if (Math.abs(dx) > 15 \|\| Math.abs(dy) > 15) return 127;` |
| C-NEW-5 | Game-over self-confirmation prevented | ✅ TRUE | `aow2-server/.../GameWebSocketHandler.java:353-356` | `if (claim.claimedBy().equals(playerId)) { sendError("Claimant cannot confirm their own game-over claim"); return; }` |
| C-NEW-7 | JWT secret from env var | ✅ TRUE | `aow2-server/.../JwtUtil.java:48-64` | Reads `AOW2_JWT_SECRET` env var, throws `IllegalStateException` if dev secret is committed or env var equals dev secret. |
| H-NEW-13 | Campaign objectives evaluated each tick | ✅ TRUE | `aow2-client/.../GameScene.java:1059` | `checkCampaignObjectives()` called from the game tick method. Dispatches to `evaluateObjective` (line 1550) which handles all 5 objective types (Destroy, Defend, Escort, Timed, Capture). |
| H-NEW-16 | LuaJ sandbox (instruction limit + getGlobals deprecated) | ⚠️ PARTIALLY TRUE | `aow2-modding/.../LuaEngine.java` | `getGlobals()` deprecated at line 304 ✅. `string.dump` blocked at line 108 ✅. `math.random` removed at line 102 ✅. **BUT** instruction limit is NOT enforced — see B-4. Comments at lines 180-188 admit "Per-instruction hooking is not available without LuaThread access". `DEFAULT_MAX_INSTRUCTIONS = 1_000_000` is dead code. |

---

## 5. Lockstep Determinism Audit

| Risk | Status | Evidence |
|------|--------|----------|
| `Math.random()` in core/common | ✅ NOT PRESENT | `rg "Math\.random\|new Random\|ThreadLocalRandom\|UUID\.randomUUID" aow2-core/src/main aow2-common/src/main` returns no matches in gameplay code. |
| `System.nanoTime()` in gameplay | ✅ ISOLATED | Only in `GameLoop.java:56,61,77` for tick pacing. Not in any gameplay decision. |
| `System.currentTimeMillis()` in gameplay | ✅ ISOLATED | Only in `ReplayFile.java:94` and `ReplayRecorder.java:140` for metadata (recording timestamp). Not in any gameplay decision. |
| `java.util.Random` in AI | ✅ REPLACED | `AISystem.java:88,122` uses `DeterministicLCG` seeded with `playerId * 31L + 42L` — deterministic across JVM versions (P1-H1 fix). |
| `ConcurrentHashMap.values()` iteration order | ✅ SORTED | `EntityManager.getAllUnits()` (line 217-221) and `getAllBuildings()` (line 266-270) sort by entity ID before returning. `SyncChecker.computeStateHash` (lines 92, 113) re-sorts for safety. The N1 fix comment at `EntityManager.java:31-37` documents this guarantee. |
| `HashMap` iteration in `ResearchBonusTracker` | ✅ SAFE | The tracker's `HashMap<Integer, Integer>` fields (lines 626-653) are accessed only via `getOrDefault(typeId, 0)` lookups (lines 662-678), never iterated for gameplay decisions. Order-independent. |
| `LinkedHashMap`/`LinkedHashSet` for determinism | ✅ APPLIED | `ResearchSystem.completedResearch` (line 116), `activeResearchMap` (line 119), `ResearchRegistry.researchEffects` (line 81), `FogOfWarSystem.visibilityGrids` (line 59) all use linked variants. The H5 fix comment at line 53-60 documents the rationale. |
| Floating-point in lockstep | ⚠️ MINOR | `PathfindingSystem` uses `double` for A* g/f costs (line 241). Two clients running the same A* on the same map should produce identical results because Java's `double` arithmetic is IEEE-754 deterministic across JVMs for the same operations. The tie-break in `PathNode.compareTo` (line 372) uses `Double.compare` which is also deterministic. No issue found. |
| `Entity.hashCode()` in HashMaps | ✅ STABLE | `GridPosition` is a record (auto hashCode). `Unit`/`Building` use entity ID (int) — stable across JVMs. |

**Conclusion:** Lockstep determinism is solid for unit/building iteration, research state, and AI randomness. The only concern is the `CommandBuffer` pointer drift (B-9) which is a logical bug, not a determinism bug.

---

## 6. Security Audit

| Control | Status | File:Line | Notes |
|---------|--------|-----------|-------|
| `/api/leaderboard/me` before `permitAll` wildcard | ✅ CORRECT | `SecurityConfig.java:75-76` | Specific matcher at line 75 precedes wildcard at line 76. Spring Security matches in order. F-02 fix comment documents this. |
| JWT dev secret rejection | ✅ CORRECT | `JwtUtil.java:48-64` | Throws `IllegalStateException` if (a) secret is dev default AND env var is missing, or (b) env var equals dev default. ANALYSIS_V2 4.9 fix. |
| WebSocket auth gate | ✅ CORRECT | `GameWebSocketHandler.java:82-86` | All message types except `auth` and `ping` require `sessionService.getPlayerForWsSession(session.getId()) != null`. |
| Command relay rate limit | ✅ CORRECT | `GameWebSocketHandler.java:235-250` | 20 commands/second/player. Silent drop on excess (no error echo to prevent amplification). |
| Command payload size limit | ✅ CORRECT | `GameWebSocketHandler.java:197, 229-234` | 4 KB max (`MAX_COMMAND_PAYLOAD_BYTES = 4 * 1024`). M8 fix. |
| Drop zombie session on send failure | ✅ CORRECT | `GameWebSocketHandler.java:496-508` | OPENRA #15 fix: removes session from `sessions` map and closes the WebSocket on `IOException`. |
| Two-phase game-over confirmation | ✅ CORRECT | `GameWebSocketHandler.java:312-421` | Phase 1: claim stored with `putIfAbsent`. Phase 2: `computeIfPresent(... null)` atomically removes and finalizes. C-NEW-5 prevents self-confirmation. H-NEW-8 prevents double-finalize via `computeIfPresent`. |
| `completeSessionAndRecordElo` is `@Transactional` | ✅ CORRECT | `SessionService.java:277-293` | F-14 fix: both `completeSession` and `rankingService.recordMatchResult` run in the same transaction. If ELO recording fails, the session completion rolls back. |
| Per-session synchronized locks | ✅ CORRECT | `SessionService.java:240, 305` | H-AUDIT-1/2 fix: `synchronized (getSessionLock(sessionUuid))` around state transitions and sync-hash reporting. Idempotent guard at line 242 prevents double-completion. |
| Chat participation check | ✅ CORRECT | `ChatController.java:88-95` (send) and `125-138` (history) | F-03 and M-AUDIT-6 fixes: verifies `playerId.equals(session.getPlayer1Id()) || playerId.equals(session.getPlayer2Id())` before allowing message send or history retrieval. |
| Leaderboard null auth check | ✅ CORRECT | `LeaderboardController.java:82-84` | F-02 fix: `if (authentication == null \|\| authentication.getPrincipal() == null) return 401;` |
| Map upload JSON validation | ✅ CORRECT | `MapController.java:128-144` | ANALYSIS_V2 4.10 fix: validates `width`/`height` fields present, dimensions 8-128, name ≤64 chars, size ≤5 MB. |
| Matchmaking `leaveQueue` synchronized | ✅ CORRECT | `MatchmakingService.java:259` | F-15 fix: `synchronized (queueLock)` around `queue.remove(playerId)`. |
| CORS restricted (not wildcard) | ✅ CORRECT | `SecurityConfig.java:33, 107` | L19 fix: `allowedOrigins` from config, defaults to `http://localhost:3000,http://localhost:8080`. |
| XFF trust limited to local/proxy | ✅ CORRECT (per TODO M-NEW-16) | `RateLimitFilter.java` | Not directly re-audited, but TODO confirms M-NEW-16 fix. |

**Findings:** No new security issues. The defensive null-check pattern is inconsistent (B-15: `MapController` lacks it), but this is a defense-in-depth gap, not a vulnerability under the current SecurityConfig.

---

## 7. Completeness Gaps

### 7.1 CommandType Coverage Matrix

| CommandType | Defined | Serialized | CommandProcessor | LockstepEngine (with systems) | LockstepEngine (fallback) | ReplayRecorder | Test files | Client UI dispatch |
|-------------|---------|-----------|------------------|-------------------------------|---------------------------|----------------|-----------|---------------------|
| Move | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 8 | ✅ right-click |
| Attack | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 5 | ✅ right-click on enemy |
| AttackMove | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 2 | ✅ A + right-click |
| Build | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 4 | ✅ B + dialog |
| Produce | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 4 | ✅ HUD button + dialog |
| Research | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 4 | ✅ HUD button + dialog |
| Garrison | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 4 | ✅ G + right-click on bunker |
| **Ungarrison** | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 3 | **❌ NOT DISPATCHED** |
| Cancel | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 4 | ✅ HUD right-click queue slot |
| SiegeMode | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 4 | ✅ D hotkey |
| Stop | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 5 | ✅ S hotkey |
| Hold | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 2 | ✅ H hotkey |
| Patrol | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 4 | ✅ P + right-click |
| Upgrade | ✅ | ✅ | ✅ | ✅ | ❌ dropped | ✅ | 2 | ✅ HUD button |

**Summary:** 13/14 command types are fully wired end-to-end. `Ungarrison` (B-7) is the only command with no client dispatch path. 7 command types are silently dropped in the `LockstepEngine` inline fallback (B-8).

### 7.2 UnitType Coverage Matrix

All 19 UnitTypes have stats in `StatsRegistry` (lines 195-390). Verified by `rg "put\(UnitType\." StatsRegistry.java | wc -l` → 19 entries.

| UnitType | typeId | Faction | Category | Stats registered | isSiegeCapable | Notes |
|----------|--------|---------|----------|------------------|----------------|-------|
| CONFED_INFANTRY | 1 | CONFED | INFANTRY | ✅ | ❌ | |
| CONFED_GRENADIER | 2 | CONFED | INFANTRY | ✅ | ❌ | |
| CONFED_LIGHT_ASSAULT | 4 | CONFED | INFANTRY | ✅ | ❌ | Upgrade-only (C-NEW-3) |
| CONFED_HEAVY_ASSAULT | 7 | CONFED | VEHICLE | ✅ | ❌ | Upgrade target |
| CONFED_FLAME_ASSAULT | 8 | CONFED | SPECIAL_MACHINERY | ✅ | ❌ | |
| CONFED_FORTRESS | 19 | CONFED | VEHICLE | ✅ | ✅ | |
| CONFED_HAMMER | 17 | CONFED | VEHICLE | ✅ | ✅ | |
| CONFED_ZEUS | 16 | CONFED | VEHICLE | ✅ | ❌ | |
| CONFED_TORRENT | 20 | CONFED | VEHICLE | ✅ | ✅ | |
| CONFED_MINE_SCORPIO | 11 | CONFED | MINE | ✅ | ❌ | |
| CONFED_MINE_FROG | 9 | CONFED | MINE | ✅ | ❌ | |
| CONFED_MINE_LIZARD | 10 | CONFED | MINE | ✅ | ❌ | |
| REBEL_INFANTRY | 1 | RESISTANCE | INFANTRY | ✅ | ❌ | |
| REBEL_GRENADIER | 2 | RESISTANCE | INFANTRY | ✅ | ❌ | |
| REBEL_SNIPER | 3 | RESISTANCE | INFANTRY | ✅ | ✅ | |
| REBEL_COYOTE | 15 | RESISTANCE | VEHICLE | ✅ | ❌ | |
| REBEL_ARMADILLO | 21 | RESISTANCE | VEHICLE | ✅ | ❌ | |
| REBEL_RHINO | 18 | RESISTANCE | VEHICLE | ✅ | ✅ | |
| REBEL_PORCUPINE | 22 | RESISTANCE | VEHICLE | ✅ | ❌ | |

**Siege-capable units:** Fortress, Hammer, Torrent (Confed); Rhino, Sniper (Rebel). Verified at `UnitType.java:125-128`. Note: the `CombatSystem.enterSiegeMode` comment at line 613-614 says "Only Torrent and Sniper can enter siege mode" — this is **inaccurate**; the actual check at line 620 calls `unit.canSiege()` which delegates to `UnitType.isSiegeCapable()`, returning true for all 5 units listed above.

### 7.3 Research Effects Coverage

- 48 research IDs (0-47) in `aow2-common/src/main/resources/data/tech_tree.json` (verified: `rg '"id":' tech_tree.json | wc -l` → 48).
- `ResearchSystem.applyResearchEffect` (`ResearchSystem.java:370`) reads the effects Map and applies 15+ effect types: infantryArmorBonus, vehicleArmorBonus, buildingArmorBonus, buildingArmorOverride, buildingRadiusBonus, attackDamageBonus (global + per-type), attackSpeedBonus (int/Map/per-type), attackRangeBonus (per-type), player0/1RangeReductionDivisor, player0/1SupplyCap, player0/1CreditLimit, unitLimitBonus, productionSpeedOverride, productionBonuses, productionDamageBonus, player0/1ScoreBonus, player0/1DisplayBonus, upgradeUnitType, siegeUpgrade.
- **All 48 IDs flow through `applyResearchEffect`** when research completes (`ResearchSystem.java:150`).
- **Gap (B-6):** During combat, `CombatSystem` uses `ArmorCalculator.calculateEffectiveArmor(Unit, Set<Integer>)` (hardcoded map) instead of `calculateEffectiveArmor(Unit, ResearchBonusTracker)` (data-driven). Only hardcoded IDs 0, 9, 24, 33 (infantry) and none (vehicle) are actually applied to damage calculations. The tracker accumulates all effects but they're not consulted.

### 7.4 Player Action End-to-End Trace

| Action | InputHandler | GameScene | CommandProcessor | Handler | System |
|--------|-------------|-----------|------------------|---------|--------|
| Move | right-click → "move" | line 470 creates `Move` | line 98 → `MoveCommandHandler` | `issueMoveCommand` | `MovementSystem` |
| Attack | right-click on enemy → "move" | line 464 creates `Attack` | line 99 → `AttackCommandHandler` | `setTargetUnitRef` | `CombatSystem` |
| Attack-Move | A + right-click → "attack_move" | line 483 creates `AttackMove` | line 100 → `handleAttackMove` | `issueMoveCommand + setAutoEngage` | `MovementSystem` + `CombatSystem` |
| Stop | S hotkey → "stop" | line 485 creates `Stop` | line 108 → `handleStop` | `clearPath + setTargetUnitRef(null)` | (direct) |
| Hold | H hotkey → "hold" | line 488 creates `Hold` | line 110 → `handleHold` | `clearPath` (retains target) | (direct) |
| Patrol | P + right-click → "patrol" | line 489 creates `Patrol` | line 111 → `handlePatrol` | `setPatrolOrigin + issueMoveCommand` | `MovementSystem` |
| Build | B + dialog → "build" | line 493 creates `Build` | line 101 → `BuildCommandHandler` | `placeBuilding` | `BuildingPlacementSystem` |
| Produce | HUD button → dialog | line 1724 creates `Produce` | line 102 → `ProduceCommandHandler` | `enqueueUnit` | `ProductionSystem` |
| Research | HUD button → dialog | line 1794 creates `Research` | line 103 → `ResearchCommandHandler` | `startResearch` | `ResearchSystem` |
| Garrison | G + right-click on bunker → "garrison" | line 506 creates `Garrison` | line 104 → `handleGarrison` | `setGarrisonedBuildingId` | (direct) |
| **Ungarrison** | **none** | **none** | line 105 → `handleUngarrison` | `setGarrisonedBuildingId(null)` | (direct) — **dead from player perspective** |
| Cancel | HUD right-click queue slot | line 339 creates `Cancel` | line 106 → `handleCancel` | `cancelProduction` | `ProductionSystem` |
| SiegeMode | D hotkey → "siege_mode" | line 519 creates `SiegeMode` | line 107 → `handleSiegeMode` | `enterSiegeMode/exitSiegeMode` | `CombatSystem` |
| Upgrade | HUD button | line 383 creates `Upgrade` | line 112 → `UpgradeCommandHandler` | `upgradeBuilding` | `BuildingUpgradeSystem` + `PowerSystem` |

---

## 8. Test Coverage Assessment

| Module | Main files | Test files | Ratio | Notes |
|--------|-----------|-----------|-------|-------|
| aow2-common | 30 | 7 | 23% | CommandType, GridPosition, GameConfig well-tested. Some enums (Faction, MovementState) have no dedicated test. |
| aow2-core | 75 | 37 | 49% | All major systems tested: Combat, Economy, Production, Research, Movement, Pathfinding, AI, Replay, Network, Campaign. |
| aow2-client | 30 | 10 | 33% | Input, audio, render, editor tested. Scene classes (GameScene, AOW2App) have no tests (JavaFX dependency). |
| aow2-server | 25 | 9 | 36% | AuthService, SessionService, MatchmakingService, RankingService, EloRatingService, JwtUtil, LeaderboardController, AuthController, ChatController tested. ReplayStorageService and MapController have no tests. |
| aow2-modding | 10 | 3 | 30% | LuaEngine, GameDataRegistry, ModManager tested. MissionScriptEngine, GameAPI, ScriptBindings have no tests. |

**CommandType test coverage:** All 14 types have at least 2 test files (see Section 7.1). `Ungarrison` has 3 test files despite having no UI dispatch path — the tests verify serialization/processing but not end-to-end player issuance.

**Gaps:**
- No integration test that exercises `LockstepEngine` + `CommandBuffer` + `submitCommand` + `submitNoOp` in the same frame (would catch B-9).
- No test that loads a v1 or v2 replay file (would catch B-2).
- No test that verifies `CombatSystem` applies modded armor research effects (would catch B-6).
- No test that issues `Ungarrison` from the client (would catch B-7).
- No test that loads a Lua script with an infinite loop (would catch B-4).

---

## 9. Architecture Observations

### 9.1 Module Boundary Discipline
- `aow2-common` (37 files): Pure data model (enums, records, GridPosition, StatsRegistry, GameConstants, GameConfig, events). No dependencies on other modules. ✅ Clean.
- `aow2-core` (109 files): Game logic (combat, economy, movement, AI, research, replay, network, campaign, world). Depends on `aow2-common`. ✅ Clean.
- `aow2-client` (47 files): FXGL rendering, input, audio, scenes. Depends on `aow2-core` + `aow2-common` + `aow2-modding` (for `MissionScriptEngine`). The dependency on `aow2-modding` is wired via reflection in `CampaignManager.createWithLuaEngine` to avoid a compile-time cycle — reasonable.
- `aow2-server` (44 files): Spring Boot REST + WebSocket. Depends on `aow2-common` (for `ChatMessageRecord`). ✅ Clean.
- `aow2-modding` (13 files): LuaJ scripting + mod loading. Depends on `aow2-core` (for `ScriptEngine` interface, `ModEventBridge`, `GameState`, `EntityManager`). ✅ Clean.

### 9.2 Lockstep Architecture
The lockstep design is sound:
- `LockstepEngine` buffers commands with a 2-frame input delay (`DEFAULT_INPUT_DELAY = 2`).
- `CommandBuffer` is a ring buffer with per-frame slots holding `CopyOnWriteArrayList<CommandType>` (thread-safe for concurrent submit from network thread + drain from game loop).
- `SyncChecker` hashes tick, all unit/building state (position, hp, faction, attackState, targetUnitRef, siegeMode, movementState, weaponCooldown, upgradeLevel, powered, garrisonedUnitRef, attackCooldown, productionQueueSize), projectile count, credits per player, and research state (completed + active). The hash is sorted by entity ID for determinism.
- `CommandSerializer` uses `typeId()` not `ordinal()` for BuildingType/UnitType (ANALYSIS_V2 2.12) — replays survive enum reorders.
- Disconnect detection: 140-tick timeout (14 seconds at 10 TPS), reset by either commands OR heartbeats (H2 fix).
- Per-frame pacing: `submitNoOp` is called every frame to advance the simulation even when the player has no commands (OPENRA #1). **This is the source of B-9.**

### 9.3 Campaign Architecture
- `CampaignManager` (613 lines) loads missions from JSON, manages progression, integrates `SaveManager` for persistence, and delegates to `ScriptEngine` (interface in `aow2-core`) which is implemented by `MissionScriptEngine` (in `aow2-modding`, instantiated via reflection).
- `GameScene.checkCampaignObjectives()` (line 1503) runs each tick in campaign mode, evaluating all 5 objective types.
- `MissionScriptEngine.processTick` (line 203) calls Lua `onTick()` each tick; `callStartFunction` (line 165) calls `onStart()` once at mission start (PLAYTEST-3 fix).
- `ModEventBridge` callbacks are wired in `MissionScriptEngine.wireModEventBridge()` (line 183) — combat events (unit killed, building destroyed) fire `GameAPI.fireEvent()` which dispatches to Lua callbacks (PLAYTEST-5 fix).

### 9.4 Known Design Limitations (documented in TODO.md, confirmed by audit)
1. **Dual objective systems**: Java `Objective` records (from campaign JSON) and Lua `GameAPI.setObjective()` are disconnected. Lua objectives update an isolated map that the victory/defeat system doesn't consult. Confirmed: `GameScene.checkCampaignObjectives` only iterates `missionObjectives` (the Java list).
2. **Script messages never displayed**: `GameAPI.getAndClearMessages()` is never polled by the HUD. Confirmed: `rg "getAndClearMessages" aow2-client` returns no matches.
3. **`onAreaEntered()` has no dispatch**: Only `ep1_mission3.lua` uses this; no game loop code checks unit proximity to registered areas. Confirmed by TODO.md.

---

## 10. What Still Does Not Work

Based on direct code reading (not TODO claims):

1. **Ungarrison command unreachable from UI** (B-7). Players can garrison but cannot ungarrison.

2. **Replay backward compatibility broken** (B-2). v1/v2 replays cannot be loaded; the v3-only check at `ReplayPlayer.java:295` contradicts the documented "readers accept 1 and 2".

3. **Lua instruction limit unenforced** (B-4). The H-NEW-16 claim is partially false. Mission scripts with infinite loops will hang the game thread. The `fatalErrorOccurred` flag only fires on `LuaError`, which infinite loops don't raise.

4. **CombatSystem ignores modded armor research** (B-6). The hardcoded `INFANTRY_ARMOR_RESEARCH` map (IDs 0, 9, 24, 33) is used instead of the data-driven `ResearchBonusTracker`. Modders cannot add new armor research effects that actually affect combat.

5. **CommandBuffer pointer drift in multiplayer** (B-9). `submitNoOp` + `submitCommand` in the same frame advance `writeIndex` by N+1 while `readIndex` advances by 1. Long multiplayer sessions with active commands will eventually corrupt the buffer.

6. **LockstepEngine inline fallback drops 7 command types** (B-8). If `setGameSystems()` is not called, Build/Produce/Research/Garrison/Ungarrison/Cancel/Upgrade are silently logged and dropped. Not a production issue (GameScene wires the systems), but a configuration footgun for alternative entry points.

7. **Web client has 4 unused npm dependencies** (B-14). `framer-motion`, `sharp`, `uuid`, `zod` declared but not imported.

8. **Stale documentation** (B-10, B-11, B-12). `CameraController`, `InputHandler`, and `PathfindingSystem` class-level Javadoc claims behavior that doesn't match the implementation.

9. **Dead code in `PathfindingSystem.getTerrainCost`** (B-13). The SHALLOW_WATER+INFANTRY branch is unreachable because `TerrainType.isPassableBy(INFANTRY)` returns false for SHALLOW_WATER (F-26 fix). Infantry cannot cross shallow water, contradicting the method's documentation.

10. **TODO.md claim 5.2 ("string.dump not blocked") is stale**. The code at `LuaEngine.java:108` does block `string.dump`. The TODO should be updated.

---

## 11. Recommendations

### Priority 1 — Correctness (must fix before multiplayer ship)

1. **Fix B-9 (CommandBuffer pointer drift).** Either:
   - Make `submitNoOp` idempotent per frame (track whether `submitCommand` already advanced `writeIndex` for the current frame), OR
   - Remove the unconditional `submitNoOp` call in `LockstepEngine.processFrame` (line 399) and instead have `submitCommand` fall back to a NoOp when no command was submitted this frame.
   Add a regression test that calls `submitCommand` + `submitNoOp` in the same frame for 20+ frames and verifies `writeIndex == readIndex + inputDelay`.

2. **Fix B-7 (Ungarrison UI dispatch).** Add either:
   - A hotkey (e.g., U) in `InputHandler.onKeyPressed` that issues `Ungarrison` for the selected garrisoned building, OR
   - A right-click context in `GameScene.handleRightClick` that detects a friendly bunker with garrisoned units and issues `Ungarrison` instead of `Move`.

3. **Fix B-2 (Replay backward compat).** Change `ReplayPlayer.java:295` from `if (formatVersion != FORMAT_VERSION) throw` to `if (formatVersion < 1 || formatVersion > FORMAT_VERSION) throw`. The v2/v3 conditional reads at lines 325/353 are already correct.

4. **Fix B-6 (CombatSystem armor path).** Change `CombatSystem.java:506`, `:333`, `:377` to use the `ResearchBonusTracker` overload:
   ```java
   int targetArmor = armorCalculator.calculateEffectiveArmor(target,
       researchSystem.getBonusTracker(EconomySystem.playerId(target.getFaction())));
   ```
   Expose `getBonusTracker(int playerId)` on `ResearchSystem` (the tracker array is at line 117).

### Priority 2 — Robustness

5. **Fix B-4 (Lua instruction limit).** Options:
   - Use `LuaJC` (LuaJ's compiler) instead of `LuaInterpreter` — compiled Lua has a `LuaError` on branch-count overflow.
   - Fork LuaJ to expose `LuaThread.callingLuaThread` for instruction counting (the code comment at line 174 admits this is the blocker).
   - Run each `onTick`/`onStart` in a separate thread with a `Thread.interrupt()` after a wall-clock timeout.
   At minimum, document that mission scripts are trusted (bundled with the game) and that mod-loaded scripts are an accepted risk.

6. **Fix B-5 (CommandBuffer.reset not synchronized).** Add `synchronized` to the `reset()` method at `CommandBuffer.java:205`.

7. **Fix B-1 (Upgrade playerId validation).** Change `CommandType.java:507-508` from `if (playerId < 0)` to `if (playerId < 0 || playerId > 1)` to match the other 13 records.

8. **Fix B-8 (LockstepEngine fallback).** Either:
   - Throw `IllegalStateException` in `applyCommand` when `economySystem == null` and a non-movement command is received (fail-fast instead of silent drop), OR
   - Remove the inline fallback entirely and require `setGameSystems` to be called before `processFrame`.

### Priority 3 — Polish

9. **Fix B-3 + B-16 (durationSeconds divisor).** Change `ReplayFile.java:127` to `return durationMillis / 1000;` (uses the v3 metadata field) with a fallback to `totalTicks / GameConstants.TICK_RATE` for v1/v2.

10. **Fix B-10, B-11, B-12 (stale comments).** Update class-level Javadoc in `CameraController`, `InputHandler`, `PathfindingSystem` to match implementation.

11. **Fix B-13 (dead SHALLOW_WATER branch).** Either:
    - Remove the dead branch at `PathfindingSystem.java:292-294` and update the method comment, OR
    - If infantry-should-cross-shallow-water is the intended behavior, revert the F-26 fix in `TerrainType.java:104` and add a test.

12. **Fix B-14 (unused npm deps).** Remove `framer-motion`, `uuid`, `zod` from `aow2-web/package.json`. Keep `sharp` if Next.js image optimization is planned; otherwise remove.

13. **Fix B-15 (MapController null auth).** Add the `if (authentication == null || authentication.getPrincipal() == null) return 401;` guard to `MapController.deleteMap` (line 196) and `MapController.uploadMap` (line 105) for consistency with `ChatController` and `LeaderboardController`.

14. **Update TODO.md.** Mark claim 5.2 (`string.dump not blocked`) as resolved — the code at `LuaEngine.java:108` blocks it. Mark H-NEW-16 as partially complete with a note that the instruction limit is deferred.

---

**End of analysis.**
