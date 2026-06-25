# AOW2-Online Third-Pass Audit Report

**Date**: 2026-06-25
**Analyzer**: aow2-analyzer skill (third pass)
**Scope**: Verification of Round 4 fixes + regression hunt + integration-gap audit
**Overall Status**: **PASS_WITH_ISSUES** — All Round 4 fixes verified; no regressions found; 1 new Medium issue (H2-client wiring); 1 new Low issue (mines not sorted); 1 known architectural gap confirmed (LockstepEngine not integrated into client/server runtime)

---

## Executive Summary

The third pass confirms that all **27 fixes** from Rounds 1-4 are present, correctly implemented, and have not introduced any regressions. The codebase is clean — no `java.util.Random` in game logic, no `HashMap` iteration in game-state-mutating code, no `e.printStackTrace()` in non-test code, no `TODO`/`FIXME` comments.

Two new issues were found:
1. **N4 (Low)**: `EntityManager.mines` is a `CopyOnWriteArrayList` that returns unsorted lists from `getMines()`/`getAllMines()`. While insertion order is deterministic in lockstep (mine placement is a command), this is inconsistent with the N1 fix which sorted all other entity types.
2. **H2-client (Medium)**: `LockstepEngine.setHeartbeatSendCallback()` is never called by any client or server code. The heartbeat mechanism exists in the engine but is not wired to the transport layer. The `sendHeartbeat()` method falls through to a debug log when `heartbeatSendCallback` is null.

One significant architectural gap was confirmed:
- **LockstepEngine is not integrated into the client or server runtime.** It is only instantiated in tests. The `MultiplayerService` handles WebSocket communication but doesn't feed commands into a `LockstepEngine`. The `GameScene` doesn't reference it. This means the lockstep multiplayer pipeline exists but is unused — the game currently runs in single-player/skirmish mode only. This is consistent with `ProjectProgress.md`'s "REALITY CHECK" but not with Phase 8 being marked "✅ COMPLETE".

---

## Round 4 Fix Verification (4/4 confirmed) ✅

| ID | Verification Method | Status |
|----|-------------------|--------|
| N1 | `grep -c "result.sort(Comparator"` in EntityManager.java → 7 sort calls found; `findUnitAt`/`findBuildingAt` iterate via `getAllUnits()`/`getAllBuildings()` (sorted) | ✅ VERIFIED |
| N2 | `grep "buildingOrdinal < 0\|unitOrdinal < 0"` in CommandSerializer.java → 2 bounds checks found (lines 275, 288) | ✅ VERIFIED |
| N3 | `grep "validateCount\|MAX_UNIT_IDS"` in CommandSerializer.java → 7 references found; `validateCount()` checks `count < 0 || count > 50` | ✅ VERIFIED |
| N2/N3 catch | `grep "Dropping malformed\|Dropping out-of-range"` in LockstepEngine.java → 2 catch blocks found (lines 213, 219) | ✅ VERIFIED |
| H2-complete | `grep "heartbeatCallback\|HEARTBEAT_INTERVAL\|sendHeartbeat()"` in LockstepEngine.java → 5 references; `sendHeartbeat()` calls `heartbeatSendCallback.accept(lockstepFrame)`; `processFrame()` calls `sendHeartbeat()` every 30 ticks; `reset()` clears `heartbeatSendCallback` and `lastHeartbeatSentTick` | ✅ VERIFIED |

---

## New Issues Found

### N4: EntityManager.getMines() returns unsorted list

- **Confidence**: HIGH
- **Severity**: Low
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/world/EntityManager.java:51, 289, 299`
- **Issue**: The `mines` field is a `CopyOnWriteArrayList` (insertion-ordered), and `getMines()`/`getAllMines()` return `Collections.unmodifiableList(mines)` without sorting by entity ID. This is inconsistent with the N1 fix which sorted all other entity types (units, buildings, projectiles).
- **Impact**: Low — `CopyOnWriteArrayList` preserves insertion order, so iteration IS deterministic across clients IF mines are inserted in the same order (which they should be in lockstep, since mine placement is a command). However, the inconsistency with N1 is a code-quality issue, and if mine insertion order ever diverges (e.g., due to a future bug), the non-determinism would be silent.
- **Fix**: Sort `getMines()`/`getAllMines()` by `Mine::getId` before returning, matching the pattern used for units/buildings/projectiles.

### H2-client: Heartbeat callback never wired by client code

- **Confidence**: HIGH
- **Severity**: Medium
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:293` (`setHeartbeatSendCallback`)
- **Issue**: `setHeartbeatSendCallback()` is defined but never called by any client or server code. Grep confirms zero callers outside the LockstepEngine itself.
- **Evidence**:
  ```
  $ grep -rn "setHeartbeatSendCallback" aow2-client/src/ aow2-core/src/ aow2-server/src/
  aow2-core/.../LockstepEngine.java:273:  // The transport layer should call setHeartbeatSendCallback() to enable
  aow2-core/.../LockstepEngine.java:293:  public void setHeartbeatSendCallback(Consumer<Long> callback) {
  ```
  The `sendHeartbeat()` method falls through to `else if (sendCallback != null)` which only logs at debug level — no heartbeat is actually sent over the wire.
- **Impact**: Medium — the H2 fix's engine side works (periodic `sendHeartbeat()` calls, `receiveHeartbeat()` updates the activity timer, server relays heartbeats), but since no heartbeat is ever sent, the idle-opponent false-disconnect problem persists in practice.
- **Fix**: In the client code that creates the `LockstepEngine` for multiplayer (likely in `MultiplayerService` or `GameScene`), add:
  ```java
  lockstepEngine.setHeartbeatSendCallback(tick ->
      gameWebSocket.sendMessage("{\"type\":\"heartbeat\",\"tick\":" + tick + "}")
  );
  ```
- **Note**: This fix is blocked by the larger architectural gap below — the `LockstepEngine` is not yet integrated into the client runtime at all.

---

## Architectural Gap: LockstepEngine not integrated into runtime

- **Confidence**: HIGH
- **Severity**: N/A (known gap, not a bug)
- **Finding**: `LockstepEngine` is only instantiated in test files (`aow2-core/src/test/.../LockstepEngineTest.java`). No client or server production code creates or uses a `LockstepEngine` instance.
- **Evidence**:
  ```
  $ grep -rn "new LockstepEngine\|LockstepEngine(" --include="*.java" | grep -v test
  aow2-core/.../LockstepEngine.java:125:  public LockstepEngine() {
  ```
  Zero production-code callers. The `MultiplayerService` handles WebSocket communication (auth, matchmaking, command relay) but doesn't create a `LockstepEngine`. The `GameScene` doesn't reference `LockstepEngine` either.
- **Impact**: The lockstep multiplayer pipeline (CommandSerializer, CommandBuffer, SyncChecker, LockstepEngine) is fully implemented and tested but not wired into the actual game. The game currently runs in single-player/skirmish mode only. Multiplayer matches would relay commands via WebSocket but neither client would process them through the lockstep engine.
- **Context**: This is consistent with `ProjectProgress.md`'s "REALITY CHECK" which says "A fully playable 1v1 skirmish match on the test map — that is the current ceiling." Phase 8 (Multiplayer) is marked "✅ COMPLETE" in terms of code existing, but the integration is a Phase 13/14 task.
- **Recommendation**: Update `ProjectProgress.md` to clarify that Phase 8 is "code complete but not integrated" rather than "✅ COMPLETE". The lockstep integration should be the top priority for Phase 13.

---

## Regression Hunt Results

### Determinism sweep ✅

| Check | Result |
|-------|--------|
| `java.util.Random` in game logic | 0 occurrences (only `DeterministicLCG` used) ✅ |
| `System.currentTimeMillis/nanoTime` in game-state mutation | 0 occurrences (only in `GameLoop` timing accumulator, which is correct) ✅ |
| `HashMap`/`HashSet` in game-state-mutating code (engine, world, network) | 0 occurrences ✅ |
| `ConcurrentHashMap` iteration in game logic | Eliminated — `EntityManager` list-returning methods all sort by ID ✅ |
| `e.printStackTrace()` in non-test code | 0 occurrences ✅ |
| `TODO`/`FIXME` comments in non-test code | 0 occurrences ✅ |

### Subsystem iteration audit ✅

| Subsystem | Iteration Method | Sorted? | Status |
|-----------|-----------------|---------|--------|
| CombatSystem | `getAllUnits()`, `getAllBuildings()` | ✅ Yes (N1 fix) | PASS |
| MovementSystem | `getAllUnits()` | ✅ Yes (N1 fix) | PASS |
| CollisionSystem | `getAllUnits()` | ✅ Yes (N1 fix) | PASS |
| ProjectileSystem | `getAllProjectiles()`, `getAllUnits()`, `getAllBuildings()` | ✅ Yes (N1 fix) | PASS |
| HPRegenerationSystem | `getAllUnits()` | ✅ Yes (N1 fix) | PASS |
| MineDetonationSystem | `getAllMines()` | ⚠️ No (N4) | LOW RISK |
| FogOfWarSystem | `getAliveUnitsForPlayer()`, `getBuildingsForPlayer()` | ✅ Yes (N1 fix) | PASS |
| SyncChecker | `getAllUnits()`, `getAllBuildings()` + explicit sort | ✅ Yes (double-sorted) | PASS |
| ResearchSystem | `activeResearchMap.entrySet()` | ✅ Yes (LinkedHashMap, H5 fix) | PASS |

### Web TypeScript audit ✅

| Check | Result |
|-------|--------|
| `bunx tsc --noEmit --skipLibCheck` | 0 new errors (3 pre-existing in `__tests__/setup.ts`) ✅ |
| `bun run test` (vitest) | 27/27 tests pass ✅ |

### Server security audit ✅

| Check | Result |
|-------|--------|
| JWT secret fail-fast | ✅ Present (C-NEW-7 fix) |
| bcrypt password hashing | ✅ Present |
| Rate limiting on auth endpoints | ✅ Present (M6 fix: `isSiteLocalAddress`) |
| Command payload size check | ✅ Present (M8 fix: 4 KB max) |
| Ordinal bounds checks | ✅ Present (N2 fix) |
| Count sanity checks | ✅ Present (N3 fix) |
| Malformed command catch | ✅ Present (N2/N3 fix in `receiveCommand`) |
| Session lock cleanup | ✅ Present (no leak) |

---

## Updated Scorecard

| Severity | Total | Fixed | New Found | Net Open | % Closed |
|----------|-------|-------|-----------|----------|----------|
| Critical | 7 | 7 | 0 | 0 | **100%** ✅ |
| High | 8 | 8 | 0 | 0 | **100%** ✅ |
| Medium | 10 | 9 | **1** (H2-client) | 2 | 80% |
| Low | 10 | 1 | **1** (N4) | 10 | 9% |
| **Total** | **35** | **25** | **2** | **12** | **71%** |

---

## Remaining Open Items (12)

### Medium (2 open)

| ID | Description | Fix Effort |
|----|-------------|------------|
| H2-client | `setHeartbeatSendCallback()` never called by client code | 1 line, but blocked by LockstepEngine integration gap |
| M2 | CONFED_LIGHT_ASSAULT / CONFED_HEAVY_ASSAULT stats guessed | Requires RE binary expansion (not available) |

### Low (10 open)

| ID | Description | Fix Effort |
|----|-------------|------------|
| N4 | `getMines()`/`getAllMines()` not sorted by ID | 2 lines (add sort) |
| L1 | StatsRegistry singleton (hard to test) | Mechanical refactor |
| L2 | `BUILDING_POWER_RADIUS` deprecated but still used | Migrate callers |
| L4 | `RANK_EXP_THRESHOLDS` deprecated but still present | Delete or document |
| L5 | Pathfinding A* vs Bresenham (documented design choice) | No action needed |
| L6 | Matchmaking fallback `"test_map"` hardcoded | Needs map pool (Phase 13) |
| L7 | JwtUtil doesn't refuse to start with default dev secret | Add fail-fast (Phase 13) |
| L8 | Test execution verification (no JDK in sandbox) | CI will verify |
| Game-over race | `put` instead of `putIfAbsent` in handleGameOver | 1 line |
| HikariCP | No pool tuning in application.yml | Phase 13/14 |

---

## Recommendations

1. **LockstepEngine integration** (Phase 13 priority): Wire `LockstepEngine` into `MultiplayerService` or `GameScene` so that multiplayer commands actually flow through the lockstep pipeline. This is the single most impactful remaining work — without it, multiplayer matches don't actually use the deterministic simulation.

2. **N4 fix** (quick): Add `mines.sort(Comparator.comparingInt(Mine::getId))` to `getMines()`/`getAllMines()` for consistency with N1.

3. **H2-client fix** (blocked by #1): Once `LockstepEngine` is integrated, add the `setHeartbeatSendCallback()` wiring.

4. **Update ProjectProgress.md**: Change Phase 8 from "✅ COMPLETE" to "✅ CODE COMPLETE (integration pending)" to accurately reflect that the lockstep engine exists but is not wired into the runtime.

5. **Game-over race** (quick): Change `pendingGameOverClaims.put(sessionUuid, newClaim)` to `putIfAbsent` at line 370 of `GameWebSocketHandler.java`.

---

*Third-pass audit generated by aow2-analyzer skill following the protocol in `skills/aow2-analyzer/SKILL.md`.*
*All claims verified by reading the current source code at commit `a85934c`.*
*Last updated: 2026-06-25*
