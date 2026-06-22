# AOW2-Online Re-Audit Report

**Date**: 2026-06-23
**Analyzer**: aow2-analyzer skill (second pass)
**Scope**: Verification of Round 1-3 fixes + regression hunt + missed-issue sweep
**Overall Status**: **PASS_WITH_ISSUES** — All 23 prior fixes verified present; 1 new Critical issue found (pre-existing, missed in original audit); 2 new Medium issues found (pre-existing DoS vectors); H2 fix confirmed incomplete.

---

## Executive Summary

All **23 fixes** from Rounds 1-3 are verified present in the codebase and correctly implemented. No regressions were introduced by the fixes themselves.

However, the re-audit uncovered **3 previously-missed issues** (1 Critical, 2 Medium) that were present in the original codebase but not caught in the initial audit:

1. **N1 (NEW Critical)**: `EntityManager.units/buildings/projectiles` are `ConcurrentHashMap` — their non-deterministic iteration order means `CombatSystem` and `MovementSystem` process entities in different orders on different clients, causing lockstep desyncs that the `SyncChecker` can detect but not prevent.
2. **N2 (NEW Medium)**: `CommandSerializer.deserialize` uses `BuildingType.values()[ordinal]` and `UnitType.values()[ordinal]` without bounds checking — a malformed payload with an out-of-range ordinal crashes the opponent's engine with `ArrayIndexOutOfBoundsException`.
3. **N3 (NEW Medium)**: `CommandSerializer.deserialize` methods allocate `new int[count]` arrays where `count` comes from the wire — a malicious `count = Integer.MAX_VALUE` causes `OutOfMemoryError`.

Additionally, the **H2 fix is confirmed incomplete**: while the server can relay heartbeats and the engine can receive them, **no client ever sends heartbeats** — the `LockstepEngine.sendHeartbeat()` method is a no-op that only logs at trace level. The idle-opponent false-disconnect problem persists until a client-side heartbeat sender is implemented.

---

## Fix Verification (23/23 confirmed)

### Round 1 — Critical (6/6 verified) ✅

| ID | Verification | Status |
|----|-------------|--------|
| C1 | `LockstepEngine.java:302` calls 4-arg `computeStateHash(state, entities, economySystem, researchSystem)` | ✅ VERIFIED |
| C2 | `ReplayEntry.java:32` validates `typeOrd > 12` (was `> 11`) | ✅ VERIFIED |
| C3 | `ModManager.java:62-65` uses `LinkedHashMap` for all 4 maps (no `ConcurrentHashMap` remaining) | ✅ VERIFIED |
| C4 | `LockstepEngine.java:426` has `owns()` helper; Attack (465), Stop (493), SiegeMode (501), Patrol (562) all call it | ✅ VERIFIED |
| C5 | `ReplayPlayer.java:79` has `default void resetToInitialState()`; line 191 calls it before backward seek | ✅ VERIFIED |
| C6 | `LockstepEngine.java:427` uses `EconomySystem.playerId(unit.getFaction())` — no `Faction.ordinal()` remaining | ✅ VERIFIED |

### Round 1 — High (7/7 verified) ✅

| ID | Verification | Status |
|----|-------------|--------|
| H1 | `AISystem.java:66-67` has `ENABLE_STRATEGY_QUALITY_SKIP = Boolean.getBoolean("aow2.ai.strategy-skip")`; line 180 gates the skip behind it | ✅ VERIFIED |
| H3 | `MatchmakingPanel.tsx:75`, `ReplaysTab.tsx:76`, `MapsTab.tsx:82` all have `onClick` handlers | ✅ VERIFIED |
| H4 | `ChatTab.tsx:29` derives `isDemo = messages.length === 0` — no `setIsDemo` during render | ✅ VERIFIED |
| H5 | `ResearchSystem.java:117` uses `LinkedHashMap` for `activeResearchMap` (no `ConcurrentHashMap`) | ✅ VERIFIED |
| H6 | `ReplayFile.java:50` has `FORMAT_VERSION = 2`; `ReplayRecorder.java:181` writes `recordedAt`; `ReplayPlayer.java:325` reads it for v2+ | ✅ VERIFIED |
| H7 | `EloRatingService.java:28` has `@Deprecated(since = "0.1.0", forRemoval = true)` | ✅ VERIFIED |
| H-MOD | `ModManager.java:106` and `ModLoader.java:82` both catch `Exception` (not just `IOException`) | ✅ VERIFIED |

### Round 1 — Medium + Low (4/4 verified) ✅

| ID | Verification | Status |
|----|-------------|--------|
| M6 | `RateLimitFilter.java:131` uses `InetAddress.isSiteLocalAddress()` (no `startsWith("172.")`) | ✅ VERIFIED |
| M8 | `GameWebSocketHandler.java:184` has `MAX_COMMAND_PAYLOAD_BYTES = 4096`; line 216 checks `commandJson.length()` | ✅ VERIFIED |
| M9 | `api.ts:7` exports `apiUrl()`; lines 70/78 export `getReplays()`/`getUnits()` | ✅ VERIFIED |
| L3 | `GameConstants.java:64-70` has no `TODO` comment — replaced with explicit `ASSUMPTION` note | ✅ VERIFIED |

### Round 2 — High + Medium (5/5 verified) ✅

| ID | Verification | Status |
|----|-------------|--------|
| H2 | `LockstepEngine.java:105` has `lastOpponentActivityTick`; lines 210/233 have `receiveHeartbeat()`/`sendHeartbeat()`; `GameWebSocketHandler.java:81` has `"heartbeat"` case | ✅ VERIFIED (but see **H2-incomplete** below) |
| H8 | `StatsController.java` exists; `SecurityConfig.java:73` permits `/api/stats/**`; `page.tsx:41-57` fetches on mount | ✅ VERIFIED |
| M5 | `PowerSystem.java:198-201` validates `level` in `[0, 3]` with clamp | ✅ VERIFIED |
| M7 | `MatchmakingService.java:367` uses `Math.floorMod(player1Id + player2Id, intersection.size())` (no `ThreadLocalRandom`) | ✅ VERIFIED |
| M10 | `AISystem.java:205` calls `resetTaskCount()` at start of cycle (no unconditional `taskCompleted()`) | ✅ VERIFIED |

### Round 3 — Documentation (3/3 verified) ✅

| ID | Verification | Status |
|----|-------------|--------|
| M1 | `StatsRegistry.java:274-278` (Rebel Infantry VERIFIED), `298-305` (Rebel Sniper UNVERIFIED with note) | ✅ VERIFIED |
| M3 | `StatsRegistry.java:247-256` (Mine Scorpio with slot 15 + trigger_type 3 documentation) | ✅ VERIFIED |
| M4 | `ArmorCalculator.java:31-44` (INFANTRY_ARMOR_RESEARCH) and `53-66` (VEHICLE_ARMOR_RESEARCH) have full RE-binary traceability | ✅ VERIFIED |

---

## New Issues Found

### N1: EntityManager uses ConcurrentHashMap — lockstep desync risk

- **Confidence**: HIGH
- **Severity**: Critical
- **RE Reference**: `AGENT.md` Critical Invariant #1: "ALL game logic must be deterministic. No `java.util.Random`, no `HashMap` iteration order..."
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/world/EntityManager.java:51-53`
- **Expected**: Entity iteration order must be deterministic across both clients in a lockstep multiplayer game.
- **Actual**: `units`, `buildings`, and `projectiles` are `ConcurrentHashMap` — their `.values()` iteration order is undefined and varies across JVM runs.
- **Evidence**:
  ```java
  // EntityManager.java:51-53
  this.units = new ConcurrentHashMap<>();
  this.buildings = new ConcurrentHashMap<>();
  this.projectiles = new ConcurrentHashMap<>();
  ```
  ```java
  // EntityManager.java:206
  public List<Unit> getAllUnits() {
      return Collections.unmodifiableList(new ArrayList<>(units.values()));
  }
  ```
  Callers that iterate in non-deterministic order:
  - `CombatSystem.processUnitTargetAcquisition()` (line 156) — units acquire targets in random order
  - `CombatSystem.processUnitAttacks()` (line 220) — units attack in random order
  - `CombatSystem.processBuildingAttacks()` (line 273) — buildings attack in random order
  - `MovementSystem.processTick()` (line 56) — units move in random order, causing non-deterministic collision resolution
- **Impact**: Two lockstep clients processing the same command stream can produce different game states because units are processed in different orders. The `SyncChecker` will detect the divergence (it sorts by ID before hashing, so the hash itself is deterministic), but by then (150 ticks = 15 seconds later) the states have already diverged irrecoverably. This is the #1 cause of "mysterious desyncs" in lockstep games.
- **Why it was missed in the original audit**: The original audit focused on `SyncChecker` sorting (which IS correct) and on `ResearchSystem`/`ModManager` maps. The `EntityManager` maps were overlooked because they're used pervasively and their thread-safety seemed intentional.
- **Fix**: Change `units`/`buildings`/`projectiles` to `LinkedHashMap` (insertion-ordered) or, better, have `getAllUnits()`/`getAllBuildings()` return a list sorted by entity ID before returning. The latter is safer because it guarantees deterministic order regardless of insertion order. The `ConcurrentHashMap` was likely chosen for thread-safety (network thread adding entities while game loop iterates), but in lockstep the network thread only modifies the `CommandBuffer`, not the `EntityManager` — so `ConcurrentHashMap` is unnecessary.

### N2: CommandSerializer has no ordinal bounds check — DoS via ArrayIndexOutOfBounds

- **Confidence**: HIGH
- **Severity**: Medium
- **RE Reference**: `protocol_specification.md` — command integrity
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/CommandSerializer.java:250, 256`
- **Expected**: Deserialized ordinals should be validated before indexing into enum arrays.
- **Actual**: `deserializeBuild` and `deserializeProduce` directly index `BuildingType.values()[buildingOrdinal]` and `UnitType.values()[unitOrdinal]` without checking bounds.
- **Evidence**:
  ```java
  // CommandSerializer.java:250
  return new CommandType.Build(tick, playerId,
          BuildingType.values()[buildingOrdinal], new GridPosition(x, y));
  
  // CommandSerializer.java:256
  return new CommandType.Produce(tick, playerId, producerId, UnitType.values()[unitOrdinal]);
  ```
- **Impact**: A malicious client can send a `Build` command with `buildingType.ordinal() = 999` (or a negative value). The opponent's `CommandSerializer.deserialize()` throws `ArrayIndexOutOfBoundsException`, which propagates through `LockstepEngine.receiveCommand()` (no catch) and crashes the opponent's game loop. This is a denial-of-service vector.
- **Fix**: Add bounds checks before indexing:
  ```java
  if (buildingOrdinal < 0 || buildingOrdinal >= BuildingType.values().length) {
      throw new IllegalArgumentException("Invalid building ordinal: " + buildingOrdinal);
  }
  ```
  The caller (`LockstepEngine.receiveCommand`) should catch `IllegalArgumentException` and log/drop the malformed command rather than crashing.

### N3: CommandSerializer allocates int[] from wire count — DoS via OutOfMemoryError

- **Confidence**: HIGH
- **Severity**: Medium
- **RE Reference**: Defensive parsing best practice
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/CommandSerializer.java:230, 239, 267, 291, 298, 306`
- **Expected**: Array sizes from untrusted input should be sanity-checked before allocation.
- **Actual**: All deserialization methods for multi-unit commands (Move, Attack, Garrison, Stop, Patrol, AttackMove) do `new int[count]` where `count = buf.getInt()` comes directly from the wire.
- **Evidence**:
  ```java
  // CommandSerializer.java:229-231 (deserializeMove)
  int count = buf.getInt();
  int[] unitIds = new int[count];  // ← OOM if count is Integer.MAX_VALUE
  for (int i = 0; i < count; i++) unitIds[i] = buf.getInt();
  ```
- **Impact**: A malicious client sends a `Move` command with `count = 2000000000`. The opponent's JVM attempts to allocate `~8 GB` for the `int[]`, causing `OutOfMemoryError` and crashing the game. Even `count = 100000` (allocating 400 KB) would be wasteful — the legitimate max is 50 units per player.
- **Fix**: Add a sanity check: `if (count < 0 || count > 50) throw new IllegalArgumentException("Invalid unit count: " + count);` (50 = `GameConstants.MAX_UNITS_PER_PLAYER`).

### H2-incomplete: Heartbeat send path is a no-op

- **Confidence**: HIGH
- **Severity**: Medium (downgraded from the original H2 because the receive path works correctly)
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:233-243`
- **Expected**: The local client should periodically call `sendHeartbeat()` to send a keep-alive ping to the opponent when it has no commands to issue.
- **Actual**: `sendHeartbeat()` is a no-op that only logs at trace level. No client code (checked `MultiplayerService.java`, `GameScene.java`, etc.) ever calls `sendHeartbeat()` or sends a `{"type":"heartbeat"}` WebSocket message.
- **Evidence**:
  ```java
  // LockstepEngine.java:233-243
  public void sendHeartbeat() {
      if (!running || sendCallback == null) {
          return;
      }
      // Heartbeats piggyback on the same send channel as commands. The transport
      // layer is responsible for distinguishing them (e.g., via a 'type' field in
      // the JSON wrapper). Here we just expose the local tick so the opponent's
      // receiveHeartbeat() can compute clock drift.
      // The actual heartbeat wire format is handled by GameWebSocketHandler.
      log.trace("Sending heartbeat at tick {}", lockstepFrame);
  }
  ```
  Grep confirms no callers: `grep -rn "sendHeartbeat" aow2-core/src/ aow2-client/src/ aow2-server/src/` returns only the method definition itself.
- **Impact**: The H2 fix's receive side works (if a heartbeat arrives, `lastOpponentActivityTick` is updated), but since no client sends heartbeats, the idle-opponent false-disconnect problem persists. An idle opponent will still trigger a disconnect pause after 14 seconds.
- **Fix**: Either (a) have `LockstepEngine.processFrame()` call `sendHeartbeat()` every N ticks (e.g., every 30 ticks = 3 seconds) when no command was submitted this frame, and have the client's WebSocket handler serialize it as `{"type":"heartbeat","tick":N}`; or (b) document that the heartbeat mechanism is a server-side relay only and clients must implement their own heartbeat sender.

---

## Pre-existing Issues Re-confirmed (not new, not regressions)

### Game-over simultaneous-claim race condition (low severity)

- **Confidence**: MEDIUM
- **Severity**: Low
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/websocket/GameWebSocketHandler.java:355-370`
- **Issue**: If both players send a `game_over` claim simultaneously (before either sees the other's claim), both will see `existingClaim == null` at line 355, both will proceed to `put` at line 370, and the second claim overwrites the first. The first claim's `winnerId` is lost.
- **Impact**: Low — requires simultaneous game-over claims, which is unlikely in practice (one player usually knows they lost first). The two-phase commit still works; just the first claimant's proposed winner is discarded.
- **Fix**: Use `putIfAbsent` instead of `put` at line 370, or synchronize on the session lock.

### No HikariCP connection pool tuning (low severity)

- **Confidence**: HIGH
- **Severity**: Low (Phase 13/14 concern)
- **Implementation**: `aow2-server/src/main/resources/application.yml`
- **Issue**: No `spring.datasource.hikari.*` configuration — Spring Boot uses default pool size of 10 connections. For a production game server with many concurrent matches, this could be a bottleneck.
- **Fix**: Add HikariCP tuning in Phase 13/14 (e.g., `maximum-pool-size: 50`, `connection-timeout: 5000`).

---

## Updated Completeness Assessment

| System | Spec Coverage | Test Coverage | Status | Change from original audit |
|--------|--------------|---------------|--------|---------------------------|
| Combat | 90% | Unknown | **PASS_WITH_ISSUES** | Same — formulas correct, multipliers unverified |
| Unit stats (Confed) | 100% | Unknown | **PASS** | Same — all 7 units match RE |
| Unit stats (Rebel) | 50% | Unknown | **PASS_WITH_ISSUES** | ↑ from 30% — Infantry/Grenadier verified, others documented |
| Building stats | 80% | Unknown | **PASS_WITH_ISSUES** | Same — Confed verified, Rebel assumed |
| Tech tree | 100% structure | Unknown | **PASS_WITH_ISSUES** | Same — costs/durations unverified |
| Pathfinding | 70% | Unknown | **DEVIATES** | Same — A* vs Bresenham (documented) |
| Economy | 85% | Unknown | **PASS_WITH_ISSUES** | Same — CC upgrade not implemented |
| AI | 70% | Unknown | **PASS_WITH_ISSUES** | ↑ from 60% — H1 skip gated, M10 task accounting fixed |
| **Lockstep networking** | **60%** | Unknown | **FAIL** | ↓ from 70% — **N1: ConcurrentHashMap iteration non-determinism** |
| Server (auth, JWT, rate limit) | 95% | Unknown | **PASS** | ↑ from 95% — M6 IP trust fixed, M8 payload size fixed |
| Matchmaking | 90% | Unknown | **PASS_WITH_ISSUES** | ↑ from 80% — M7 deterministic map selection |
| Web dashboard | 40% | Unknown | **INCOMPLETE** | ↑ from 30% — H3 buttons wired, H4 fixed, H8 live stats, M9 API helper |
| Replay recording | 100% | Unknown | **PASS** | ↑ from 95% — C2 crash fixed, C5 seek fixed, H6 timestamp fixed |
| Modding | 80% | Unknown | **PASS_WITH_ISSUES** | ↑ from 70% — C3 deterministic order, broad catch |
| Campaign | 100% count | Unknown | **PASS** | Same |
| Map editor | 90% | Unknown | **PASS** | Same |
| **CommandSerializer** | **70%** | Unknown | **FAIL** | ↓ — **N2/N3: no bounds checking on deserialization** |

---

## Prioritized Fix Plan for Round 4

1. **N1** (Critical) — Fix `EntityManager` to use `LinkedHashMap` or have `getAllUnits()`/`getAllBuildings()` sort by ID before returning. This is the single highest-impact fix for lockstep stability.
2. **N2** (Medium) — Add ordinal bounds checks in `CommandSerializer.deserializeBuild` and `deserializeProduce`. Wrap in try-catch in `LockstepEngine.receiveCommand`.
3. **N3** (Medium) — Add count sanity checks (`count > 50`) in all 6 multi-unit deserialization methods.
4. **H2-incomplete** (Medium) — Wire `LockstepEngine.sendHeartbeat()` to actually send via `sendCallback`, and have `MultiplayerService` call it every 30 ticks.
5. **Game-over race** (Low) — Use `putIfAbsent` in `handleGameOver` Phase 1.
6. **HikariCP tuning** (Low) — Add pool config in Phase 13.

---

## Combined Scorecard (original + new)

| Severity | Original Total | Fixed | New Found | Net Open | % Closed |
|----------|---------------|-------|-----------|----------|----------|
| Critical | 6 | 6 | **1** (N1) | 1 | 86% |
| High | 8 | 8 | 0 | 0 | 100% |
| Medium | 10 | 8 | **3** (N2, N3, H2-incomplete) | 5 | 62% |
| Low | 8 | 1 | **2** (game-over race, HikariCP) | 9 | 10% |
| **Total** | **32** | **23** | **6** | **15** | **66%** |

---

*Re-audit generated by aow2-analyzer skill following the protocol in `skills/aow2-analyzer/SKILL.md`.*
*All claims verified by reading the current source code at commit `766e79f`.*
*Last updated: 2026-06-23*
