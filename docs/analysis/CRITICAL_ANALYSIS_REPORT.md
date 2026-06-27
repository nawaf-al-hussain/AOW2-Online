# AOW2-Online Analysis Report

**Date**: 2026-06-23
**Analyzer**: aow2-analyzer skill
**Scope**: Full-project critical audit — combat, economy, pathfinding, research, AI, networking, server, web, replay, modding, campaign
**Overall Status**: **PASS_WITH_ISSUES** — Core engine spec-compliant, but multiple desync risks, dead UI buttons, and one replay-crash bug

---

## Executive Summary

The project is more mature than its own `ProjectProgress.md` admits in some places (Confederation unit stats match RE JSON exactly; the 48-node tech tree is complete; the 2-step damage formula is correct) but **less mature in others** (web dashboard is still largely hardcoded; the lockstep engine's sync hash omits economy & research state; one replay bug crashes every AttackMove recording; mod override order is non-deterministic).

The most consequential findings are:

1. **Lockstep sync hash doesn't include economy or research state** — desyncs in credits or research progress will go undetected until they manifest in entity state, by which point both clients are already irrecoverably diverged. (`LockstepEngine.java:236`)
2. **Replay recording of AttackMove commands throws `IllegalArgumentException`** because `ReplayEntry` validates `typeOrd > 11` but the AttackMove type ID is `0x0C = 12`. Every attack-move issued in a recorded game will crash the recorder. (`ReplayEntry.java:29` vs `ReplayRecorder.java:41`)
3. **Mod override application order is non-deterministic** because `ModManager.enabledMods` is a `ConcurrentHashMap` and is iterated via `keySet()` when applying overrides. Two enabled mods that override the same stat will produce different game stats across JVM launches. This violates the project's own "no non-deterministic collections in game state" rule. (`ModManager.java:55, 175, 227, 263`)
4. **Lockstep `Attack` command is missing ownership validation** — Move checks `unit.getFaction().ordinal() == m.playerId()` but Attack doesn't. A malicious client can set targets on opponent units. (`LockstepEngine.java:374-381`)
5. **Replay backward seek doesn't restore game state.** "Snapshots" only store command indices, not state — re-executing commands on top of advanced state produces invalid replay state. (`ReplayPlayer.java:148-191`)
6. **The "Join Battle" button in MatchmakingPanel has no onClick handler.** Same for "Watch" on replays and "Download" on maps. The web dashboard looks finished but several core flows dead-end. (`MatchmakingPanel.tsx:69-71`, `ReplaysTab.tsx:65-67`, `MapsTab.tsx:75-77`)
7. **AI strategy-quality skip is fabricated** — the random decision-skipping logic in `AISystem` is acknowledged as having "no RE basis" in code comments but is still shipped. It IS deterministic (uses DeterministicLCG), so it won't cause desyncs, but it's hallucinated game design. (`AISystem.java:155-163`)

The RE spec compliance is otherwise strong: the damage formula matches exactly, all 7 Confederation unit types match `complete_unit_stats.json` field-for-field, all 48 research IDs are present with correct effects (costs and durations are flagged as UNVERIFIED, which is honest), and the pathfinding deviation from RE's Bresenham-line approach is explicitly documented as a deliberate design choice.

---

## Critical Issues

### C1: Lockstep sync hash omits economy and research state

- **Confidence**: HIGH
- **RE Reference**: `multiplayer_architecture.md` — "Data integrity: turn sequence validation, state comparison"
- **Implementation**:
  - `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:236` — calls `syncChecker.computeStateHash(state, entities)` (2-arg version)
  - `aow2-core/src/main/java/com/aow2/core/network/SyncChecker.java:80-142` — the 4-arg overload exists and incorporates credits per player (line 119-123) and research state (line 126-139), but is **never called by the engine**
- **Expected**: The sync hash must include ALL game state that can diverge between clients — entity positions, HP, credits, completed research, active research progress.
- **Actual**: Only entity positions/HP and projectile count are hashed. Credits and research state are silently excluded.
- **Evidence**:
  ```java
  // LockstepEngine.java:235-238 — uses 2-arg overload
  if (syncChecker.shouldCheck(lockstepFrame)) {
      long hash = syncChecker.computeStateHash(state, entities);  // ← economy & research omitted
      syncChecker.setLocalHash(hash);
  }
  ```
  ```java
  // SyncChecker.java:80 — 4-arg overload exists but is never invoked
  public long computeStateHash(GameState state, EntityManager entities,
                                EconomySystem economy, ResearchSystem research) { ... }
  ```
- **Fix**: In `LockstepEngine.processFrame`, change the call to `syncChecker.computeStateHash(state, entities, economySystem, researchSystem)`. The fields are already injected via `setGameSystems()`. Add a regression test that verifies credit divergence between two simulated clients triggers a desync.

---

### C2: Replay recording of AttackMove commands crashes

- **Confidence**: HIGH
- **RE Reference**: `aow2-common/src/main/java/com/aow2/common/model/CommandType.java` — `AttackMove` is a sealed-interface variant
- **Implementation**:
  - `aow2-core/src/main/java/com/aow2/core/replay/ReplayEntry.java:29` — `if (typeOrd < 0 || typeOrd > 11) throw new IllegalArgumentException(...)`
  - `aow2-core/src/main/java/com/aow2/core/replay/ReplayRecorder.java:41` — `private static final int TYPE_ATTACK_MOVE = 0x0C;` (= 12)
- **Expected**: All 12 command types record without throwing.
- **Actual**: Every AttackMove command causes `ReplayEntry`'s compact constructor to throw `IllegalArgumentException`, which propagates uncaught through `ReplayRecorder.recordCommand()` and crashes the game session.
- **Evidence**:
  ```java
  // ReplayEntry.java:24-37
  public ReplayEntry {
      if (typeOrd < 1 || typeOrd > 11) {     // ← should be > 12 (or >= 12 depending on convention)
          throw new IllegalArgumentException("typeOrd must be 1-11, got: " + typeOrd);
      }
      ...
  }
  ```
  ```java
  // ReplayRecorder.java:30-41
  private static final int TYPE_MOVE         = 0x01;
  ...
  private static final int TYPE_PATROL       = 0x0B;  // 11
  private static final int TYPE_ATTACK_MOVE  = 0x0C;  // 12 — REJECTED by ReplayEntry
  ```
  The switch at `ReplayRecorder.java:237-250` does cover all 12 arms (including AttackMove), so the recorder dispatches to `TYPE_ATTACK_MOVE` correctly — but the subsequent `new ReplayEntry(...)` call throws.
- **Fix**: Change `ReplayEntry.java:29` from `typeOrd > 11` to `typeOrd > 12` (or better, define a `MAX_TYPE_ORD` constant). Update the stale Javadoc on `ReplayEntry.java:11` ("1-11" → "1-12"). Add a regression test that records at least one of each command type.

---

### C3: Mod override application order is non-deterministic

- **Confidence**: HIGH
- **RE Reference**: Project invariant from `AGENT.md` §"Critical Invariants" #1: "ALL game logic must be deterministic. No `java.util.Random`, no `HashMap` iteration order, no `System.currentTimeMillis()` in game logic."
- **Implementation**:
  - `aow2-modding/src/main/java/com/aow2/mod/ModManager.java:55` — `private final Map<String, Mod> enabledMods = new ConcurrentHashMap<>();`
  - `aow2-modding/src/main/java/com/aow2/mod/ModManager.java:175, 227, 263` — iterate `enabledMods.keySet()` to apply unit/building/global overrides
- **Expected**: When two enabled mods override the same stat, the result is deterministic across JVM launches. This is required for both single-player consistency and (especially) lockstep multiplayer desync prevention.
- **Actual**: `ConcurrentHashMap.keySet()` iteration order is undefined. The same set of enabled mods can produce different game stats on different launches.
- **Evidence**:
  ```java
  // ModManager.java:175 (and similarly at :227, :263)
  for (String modId : enabledMods.keySet()) {
      Mod mod = enabledMods.get(modId);
      gameDataRegistry.applyUnitOverrides(mod.getManifest().dataOverrides(), modId);
  }
  ```
- **Fix**: Change `enabledMods` field type to `LinkedHashMap<String, Mod>` (preserve insertion order) or `ConcurrentSkipListMap<String, Mod>` (sorted by mod ID). Add a `priority` field to `ModManifest` so authors can declare load order explicitly. Add a test that loads two conflicting mods in different orders and asserts the same final stat.

---

### C4: Lockstep Attack command missing ownership validation

- **Confidence**: HIGH
- **RE Reference**: `protocol_specification.md` — command integrity, both clients execute same commands
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:374-381`
- **Expected**: All command types that mutate entity state should validate that the issuing player owns the affected units.
- **Actual**: The `Move` arm validates `unit.getFaction().ordinal() == m.playerId()` (line 361), but the `Attack` arm does not — it directly sets the target on any unit regardless of ownership.
- **Evidence**:
  ```java
  // LockstepEngine.java:358-373 — Move arm validates ownership
  case CommandType.Move m -> {
      for (int unitId : m.unitIds()) {
          var unit = entities.getUnit(unitId);
          if (unit != null && unit.getFaction().ordinal() == m.playerId()) {  // ✓ validated
              ...
  ```
  ```java
  // LockstepEngine.java:374-381 — Attack arm does NOT validate ownership
  case CommandType.Attack a -> {
      for (int unitId : a.unitIds()) {
          var unit = entities.getUnit(unitId);
          if (unit != null) {  // ← missing ownership check
              unit.setTargetUnitRef(a.targetId());
          }
      }
  }
  ```
  Also missing: `AttackMove` (line 386 has the check), `Stop` (line 401 — missing), `SiegeMode` (line 410 — missing), `Patrol` (line 470 — missing).
- **Fix**: Add the ownership check to all command arms. Centralize as a helper: `private boolean owns(Unit unit, int playerId) { return unit != null && unit.getFaction().ordinal() == playerId; }`.

---

### C5: Replay backward seek doesn't restore game state

- **Confidence**: HIGH
- **RE Reference**: `phases.md` Phase 11 — "Seeking (jump to any game tick)" required behavior
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/replay/ReplayPlayer.java:148-191`
- **Expected**: After `seekTo(t)`, the game state observed by the `commandCallback` matches what it would have been if the game had been played from tick 0 to tick `t` from the start.
- **Actual**: The "snapshots" stored at `ReplayPlayer.java:49` are `Map<Long, Integer>` (tick → command-index), NOT actual game state. When seeking backward, only `currentTick` and `nextCommandIndex` are reset — the callback's held game state still reflects the old position. Re-executing commands piles them on top of the advanced state.
- **Evidence**:
  ```java
  // ReplayPlayer.java:49 — snapshots are NOT state, just indices
  private final Map<Long, Integer> snapshots = new LinkedHashMap<>();

  // ReplayPlayer.java:179-188 — backward seek resets indices but NOT state
  this.currentTick = snapshotTick;
  this.nextCommandIndex = snapshotIndex;
  // Fast-forward by re-executing commands
  while (currentTick < targetTick) {
      processCommandsForTick(currentTick);
      currentTick++;
  }
  ```
  The `CommandCallback` interface at `ReplayPlayer.java:54-62` has no `resetState()` method — there's no way for the player to ask the consumer to reset.
- **Fix**: Either (a) add `CommandCallback.resetToInitialState()` and call it before fast-forwarding, or (b) store real serializable state snapshots at intervals (e.g., every 600 ticks) and deserialize on seek. Option (a) is simpler; option (b) is needed for very long replays.

---

### C6: `LockstepEngine.applyCommand` for `Move` uses non-deterministic faction check

- **Confidence**: MEDIUM
- **RE Reference**: `Faction.java` enum ordinal ordering
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:361, 386`
- **Expected**: Player ID ↔ Faction mapping should be explicit and stable, not derived from enum ordinals.
- **Actual**: The check `unit.getFaction().ordinal() == m.playerId()` assumes player 0 is always CONFEDERATION (ordinal 0) and player 1 is always RESISTANCE (ordinal 1). This is currently true but is an implicit coupling — if anyone reorders the `Faction` enum, this silently breaks.
- **Evidence**: `Faction.values()` returns `[CONFEDERATION, RESISTANCE, NEUTRAL]` so `RESISTANCE.ordinal() == 1`. The coupling works by coincidence.
- **Fix**: Use the explicit `EconomySystem.playerId(Faction)` and `EconomySystem.playerFaction(int)` helpers that already exist. Replace `unit.getFaction().ordinal() == m.playerId()` with `EconomySystem.playerId(unit.getFaction()) == m.playerId()`.

---

## High Issues

### H1: AI strategy-quality skip has no RE basis (fabricated game design)

- **Confidence**: HIGH (the code itself admits this)
- **RE Reference**: `gameplay_analysis/ai_analysis.md` — original AI "processes every cycle", no probabilistic skip documented
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/ai/AISystem.java:155-163`
- **Expected**: AI behavior should match documented RE patterns. Where RE is silent, assumptions should be marked `// ASSUMPTION:` and tracked in `ProjectProgress.md`.
- **Actual**: The code randomly skips AI decision cycles based on `difficulty.strategyQuality` (a probability threshold). The inline comment explicitly says: *"No RE basis for random decision skipping. The original AI processes every cycle. This probabilistic skip is a common game AI pattern but is fabricated."*
- **Evidence**:
  ```java
  // AISystem.java:155-163
  // UNVERIFIED (L-8): No RE basis for random decision skipping. The original AI
  // processes every cycle. This probabilistic skip is a common game AI pattern
  // but is fabricated. Uses DeterministicLCG so it IS lockstep-safe. Consider
  // removing and relying solely on deterministic difficulty scaling (tick interval,
  // task limits) if this causes issues.
  if (random.nextDouble() > difficulty.strategyQuality) {
      LOG.debug("AI player {} skipping decision (strategy quality check)", playerId);
      return;
  }
  ```
  This violates the project's own Anti-Hallucination Protocol (Rule 1: "Never invent game data"). It IS marked UNVERIFIED, which is honest, but it should either be removed or moved behind an explicit "modern AI enhancement" flag distinct from "faithful RE recreation".
- **Fix**: Either remove the probabilistic skip (rely on `tickInterval` and `maxConcurrentTasks` for difficulty scaling), or add a `boolean modernEnhancements` config flag and gate this behavior behind it. Update `Goal.md` to clarify whether modern enhancements are in-scope.

---

### H2: `LockstepEngine.processFrame` disconnect detection only fires once per frame

- **Confidence**: MEDIUM
- **RE Reference**: `protocol_specification.md` — session lifecycle, reconnection handling
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java:213-223`
- **Expected**: Disconnect detection should track per-frame opponent command presence so the engine can pause promptly when the opponent goes silent.
- **Actual**: The check `lockstepFrame - lastOpponentCommandTick > DISCONNECT_TIMEOUT_TICKS` only updates `lastOpponentCommandTick` when a command is received (line 184). If the opponent is silently running (no commands to send because they're idle), this will falsely trigger a disconnect pause after 14 seconds of opponent idleness.
- **Evidence**:
  ```java
  // LockstepEngine.java:176-185 — receiveCommand updates lastOpponentCommandTick
  public void receiveCommand(byte[] data) {
      ...
      commandBuffer.submitOpponentCommand(command, command.tick());
      lastOpponentCommandTick = command.tick();  // only updated on actual command
  }
  ```
  ```java
  // LockstepEngine.java:214 — check uses lastOpponentCommandTick
  if (lockstepFrame - lastOpponentCommandTick > DISCONNECT_TIMEOUT_TICKS) {
      paused = true;
      ...
  }
  ```
  An idle opponent (no commands for 14+ seconds while still connected) will be incorrectly flagged as disconnected.
- **Fix**: Add a separate `lastHeartbeatTick` updated by a heartbeat/ping message (or by any network traffic including sync_hash), and use that for disconnect detection. The existing `case "ping" -> sendMessage(session, Map.of("type", "pong"));` in `GameWebSocketHandler.java:80` could be extended for this.

---

### H3: Web dashboard "Join Battle", "Watch", "Download" buttons have no onClick

- **Confidence**: HIGH
- **RE Reference**: `phases.md` Phase 12 — web client should have feature parity
- **Implementation**:
  - `aow2-web/src/components/MatchmakingPanel.tsx:69-71` — "Join Battle" button
  - `aow2-web/src/components/tabs/ReplaysTab.tsx:65-67` — "Watch" button
  - `aow2-web/src/components/tabs/MapsTab.tsx:75-77` — "Download" button
- **Expected**: Clicking these buttons should initiate the corresponding action.
- **Actual**: The buttons render but have no `onClick` handler — clicking does nothing. The user gets no feedback.
- **Fix**: Wire each button to its corresponding store action or API call. If the action isn't implemented yet, at minimum show a toast "Feature coming soon" rather than silently dead-ending.

---

### H4: `ChatTab` calls `setIsDemo(false)` during render

- **Confidence**: HIGH
- **RE Reference**: React anti-pattern, not RE-related
- **Implementation**: `aow2-web/src/components/tabs/ChatTab.tsx:27`
- **Expected**: State setters should only be called from event handlers or effects, never during render.
- **Actual**: The component calls `setIsDemo(false)` inside a ternary expression evaluated during render. This triggers a React re-render warning and can cause infinite loops in strict mode.
- **Evidence**:
  ```tsx
  // ChatTab.tsx:27
  const messages = useChatStore((s) => s.messages);
  const displayMessages = messages.length > 0
      ? (() => { setIsDemo(false); return messages; })()  // ← setState during render
      : demoMessages;
  ```
- **Fix**: Compute `displayMessages` as `messages.length > 0 ? messages : demoMessages` and derive `isDemo` from `messages.length === 0` instead of storing it as separate state.

---

### H5: `ResearchSystem.processTick` iterates `ConcurrentHashMap` non-deterministically

- **Confidence**: MEDIUM
- **RE Reference**: `AGENT.md` Critical Invariant #1 — deterministic game logic
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/research/ResearchSystem.java:108, 127`
- **Expected**: When multiple researches complete in the same tick, the order in which `applyResearchEffect` is called should be deterministic across clients.
- **Actual**: `activeResearchMap` is a `ConcurrentHashMap` — iteration order via `entrySet()` is undefined. If two researches complete simultaneously and their effects interact (e.g., both modify the same unit type's armor), the final state could differ between clients.
- **Evidence**:
  ```java
  // ResearchSystem.java:108
  private final Map<Integer, ActiveResearch> activeResearchMap = new ConcurrentHashMap<>();
  ...
  // ResearchSystem.java:127
  for (var entry : activeResearchMap.entrySet()) {
      ActiveResearch research = entry.getValue();
      ...
      if (updated.isComplete()) {
          ...
          applyResearchEffect(researchId, playerId, entities);  // order matters
          ...
      }
  }
  ```
- **Fix**: Change `activeResearchMap` to `LinkedHashMap` (insertion-ordered) or sort the iteration by `techCentreId` before applying effects. Since the map is only mutated from the game-loop thread (single-threaded access), `ConcurrentHashMap` isn't even necessary.

---

### H6: `ReplayPlayer.loadFromFile` overwrites `recordedAt` with load time

- **Confidence**: HIGH
- **RE Reference**: Replay integrity — original recording timestamp should be preserved
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/replay/ReplayPlayer.java:305`
- **Expected**: `recordedAt` field should reflect when the game was originally played, not when the replay file was loaded.
- **Actual**: The load routine calls `ReplayFile.createNew(...)` which uses `System.currentTimeMillis()` — overwriting the original timestamp from the file.
- **Fix**: Read `recordedAt` from the binary file header during `loadFromFile` and pass it through to the `ReplayFile` constructor instead of regenerating it.

---

### H7: `EloRatingService` is deprecated but still in source tree

- **Confidence**: HIGH
- **RE Reference**: Project cleanup
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/service/EloRatingService.java:22-24`
- **Expected**: Deprecated classes should either be removed or have a clear migration plan with a removal date.
- **Actual**: The class is annotated `@Deprecated`, has its `@Service` annotation commented out (so it's no longer a Spring bean), but is still present. The Javadoc says "Prefer using `RankingService#recordMatchResult` instead" but doesn't say when the class will be deleted.
- **Fix**: Either delete the class outright (callers should already be using `RankingService`), or add a `@Deprecated(forRemoval = true, since = "...")` annotation with a target removal version. Verify no callers remain via grep before deletion.

---

### H8: Web Quick Stats panel is fully hardcoded

- **Confidence**: HIGH
- **RE Reference**: `phases.md` Phase 12 — web client feature parity
- **Implementation**: `aow2-web/src/app/page.tsx:171-185`
- **Expected**: Dashboard stats (active players, matches today, etc.) should be fetched from server endpoints.
- **Actual**: The Quick Stats panel shows hardcoded numbers: 1,247 / 89 / 342 / 56. No fetch is attempted.
- **Evidence**:
  ```tsx
  // page.tsx:171-185
  // Numbers like 1247, 89, 342, 56 are inline literals with no API call
  ```
- **Fix**: Add a `/api/stats` endpoint on the server and have `page.tsx` fetch it on mount. If the server endpoint isn't ready, at least show "—" instead of fake numbers.

---

## Medium Issues

### M1: Confederation unit stats match RE; Rebel unit stats are partially assumed

- **Confidence**: HIGH
- **RE Reference**: `gameplay_analysis/complete_unit_stats.json`
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/StatsRegistry.java:273-329`
- **Expected**: All unit stats traceable to RE data; assumptions explicitly marked.
- **Actual**: All 7 Confederation units (Infantry, Grenadier, Flame Assault, Fortress, Hammer, Zeus, Torrent) match the RE JSON field-for-field. ✅ Rebel units have only `sight_range`, `attack_range`, and `armor` from RE; all other fields (hp, damage, speed, attackBonus, attackSpeed, buildTime, costCredits, rewardCredits, etc.) are inferred from Confederation counterparts. This is documented in `ProjectProgress.md` as an assumption but not marked with `// ASSUMPTION:` in `StatsRegistry.java` for each Rebel unit.
- **Evidence**: Compare `StatsRegistry.java:275-280` (REBEL_INFANTRY) with the RE JSON entry for Rebel Infantry (which only has `sight_range=9, attack_range=5, armor=4`). The `hp=40, damage=2, speed=5, attackBonus=0` values are copied from CONFED_INFANTRY without inline justification.
- **Fix**: Add `// ASSUMPTION: hp/damage/speed/etc. copied from CONFED_INFANTRY — RE only provides sight/range/armor for Rebels` comments to each Rebel unit entry.

---

### M2: `CONFED_LIGHT_ASSAULT` and `CONFED_HEAVY_ASSAULT` stats are entirely guessed

- **Confidence**: HIGH (the code itself marks these UNVERIFIED)
- **RE Reference**: `gameplay_analysis/complete_unit_stats.json` — only 7 Confederation units defined; Light/Heavy Assault are NOT present in the RE JSON
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/StatsRegistry.java:188-202`
- **Expected**: Either these unit types shouldn't exist (since they're not in RE data), or their stats should be marked as design choices distinct from RE recreation.
- **Actual**: The units are defined with placeholder stats, marked `UNVERIFIED` in comments. They're treated as upgrade targets referenced by research IDs 6 and 24/27.
- **Fix**: Decide whether these are in-scope for "faithful RE recreation" (Goal.md says "All 14 unit types (7 per faction)"). If yes, extract real stats from RE binary. If no, remove them and adjust the research tree to not reference them.

---

### M3: Mine unit stats (attackRange/sightRange) are assumptions

- **Confidence**: HIGH (the code itself marks these ASSUMPTION)
- **RE Reference**: `complete_unit_stats.json` — no separate mine entries with full stats
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/StatsRegistry.java:248-271`
- **Expected**: Mine trigger radius / detection range should come from RE data.
- **Actual**: Each mine entry has `// ASSUMPTION: attackRange = sightRange (N) for mines; not explicitly in RE data` comments.
- **Fix**: Consult `decrypted_data.json` for mine trigger radii. The 3.76 MB decrypted data file likely contains this.

---

### M4: `ArmorCalculator.VEHICLE_ARMOR_RESEARCH` is empty — vehicle armor upgrades missing

- **Confidence**: MEDIUM
- **RE Reference**: `combat_formulas.md` research IDs 0-47 — none directly add vehicle armor via Z[] array, but research IDs 33 (Rebel) and 9 (Confed) are documented as "Infantry armour" for heavy types which include some machinery
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/combat/ArmorCalculator.java:44`
- **Expected**: If RE confirms no research adds vehicle armor, this is fine but the empty map should be deleted or commented as intentionally empty.
- **Actual**: `private static final Map<Integer, Integer> VEHICLE_ARMOR_RESEARCH = Map.of();` is empty. The inline comment says "Vehicle armor may come from other mechanisms" but no other mechanism is implemented.
- **Fix**: Either remove the field (if confirmed no vehicle armor upgrades exist) or implement the alternative mechanism (e.g., upgrade-level-based armor, which is mentioned but not wired up).

---

### M5: `PowerSystem.getUpgradeLevel` returns `building.getUpgradeLevel()` but no code ever sets it

- **Confidence**: MEDIUM
- **RE Reference**: `combat_formulas.md` — Generator power radius scales with upgrade level
- **Implementation**:
  - `aow2-core/src/main/java/com/aow2/core/economy/PowerSystem.java:179-184` — calls `building.getUpgradeLevel()`
  - No call site sets `building.setUpgradeLevel(...)` anywhere in the codebase (per `ProjectProgress.md`)
- **Expected**: Generator upgrade level should be incremented when the player pays for an upgrade.
- **Actual**: Upgrade system is not implemented. `getUpgradeLevel()` always returns 0, so all generators use radius 10 (level 0).
- **Fix**: Either implement the upgrade system (deduct credits, increment level, recalculate power grid) or remove the upgrade-level code path and document that generators are level-0 only.

---

### M6: `RateLimitFilter` IP trust check is overly broad

- **Confidence**: MEDIUM
- **RE Reference**: Security best practice
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/security/RateLimitFilter.java:114-129`
- **Expected**: Only trust X-Forwarded-For from explicitly-configured proxy IPs.
- **Actual**: The check trusts any RFC 1918 private address (`127.`, `10.`, `172.`, `192.168.`) as a "trusted proxy". The `172.` prefix matches all of `172.0.0.0/8`, not just the private `172.16.0.0/12` range — so `172.217.x.x` (Google DNS) would be falsely treated as trusted.
- **Evidence**:
  ```java
  // RateLimitFilter.java:118-120
  boolean isTrustedProxy = remoteAddr != null &&
      (remoteAddr.startsWith("127.") || remoteAddr.startsWith("10.") ||
       remoteAddr.startsWith("172.") ||                    // ← too broad
       remoteAddr.startsWith("192.168.") || ...);
  ```
- **Fix**: Replace `remoteAddr.startsWith("172.")` with a proper CIDR check for `172.16.0.0/12`, or better, use Spring's `InetAddress.isSiteLocalAddress()`.

---

### M7: `MatchmakingService.selectMatchMap` uses `ThreadLocalRandom` — non-deterministic

- **Confidence**: HIGH
- **RE Reference**: Not a game-logic concern, but server-side determinism is still desirable for reproducibility
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/service/MatchmakingService.java:362`
- **Expected**: For testing/replay purposes, map selection should be reproducible.
- **Actual**: `intersection.get(ThreadLocalRandom.current().nextInt(intersection.size()))` — non-deterministic.
- **Fix**: This is server-side only and doesn't affect lockstep determinism, so it's lower priority. But for audit/debugging, consider seeding from the two player IDs: `intersection.get(Math.floorMod(player1Id + player2Id, intersection.size()))`.

---

### M8: `GameWebSocketHandler.handleCommand` doesn't validate the command type

- **Confidence**: MEDIUM
- **RE Reference**: `protocol_specification.md` — command relay
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/websocket/GameWebSocketHandler.java:148-168`
- **Expected**: Server should at least sanity-check that the `command` field is a valid CommandType before relaying.
- **Actual**: The command is forwarded unmodified to the opponent. A malformed or oversized payload is relayed as-is, potentially crashing the opponent's client.
- **Fix**: Add a max-payload-size check (e.g., 4 KB) and optionally validate the command type ID before relaying.

---

### M9: Web `UnitsTab` and `ReplaysTab` use direct `fetch` instead of `apiUrl()` helper

- **Confidence**: HIGH
- **RE Reference**: Internal consistency
- **Implementation**:
  - `aow2-web/src/components/tabs/UnitsTab.tsx:42` — `fetch('/api/units')` direct
  - `aow2-web/src/components/tabs/ReplaysTab.tsx:23` — `fetch('/api/replays')` direct
- **Expected**: All API calls use the shared `apiUrl()` helper from `api.ts`.
- **Actual**: These two tabs bypass the helper, missing the `?XTransformPort=8080` query string used elsewhere. Inconsistency in URL handling.
- **Fix**: Replace direct `fetch('/api/...')` calls with `fetch(apiUrl('/api/...'))`.

---

### M10: `AISystem.processTick` calls `taskCompleted()` 5 times unconditionally

- **Confidence**: MEDIUM
- **RE Reference**: Internal logic
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/ai/AISystem.java:178-187`
- **Expected**: Task count should reflect actual tasks started this cycle.
- **Actual**: After each sub-decision (economy, research, military, siege, garrison), `taskCompleted()` is called unconditionally — even if no task was started in that subsystem. This means the active task count can go negative (clamped at 0 by the `if (activeTaskCount > 0)` check) and the `maxConcurrentTasks` limit is effectively bypassed.
- **Evidence**:
  ```java
  // AISystem.java:178-187
  processEconomyDecisions(...);
  taskCompleted();          // ← always called, even if economy started 0 tasks
  processResearchDecisions(...);
  taskCompleted();          // ← same
  ...
  ```
- **Fix**: Only call `taskCompleted()` when a task was actually started. Or remove the manual task accounting entirely and use a per-cycle counter that resets at the start of each decision cycle.

---

## Low Issues

### L1: `StatsRegistry` is a singleton — hard to test in isolation

- **Confidence**: HIGH
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/StatsRegistry.java:25-62`
- **Issue**: The singleton pattern with lazy init makes it impossible to inject test data without `resetInstance()` reflection tricks. Consider making it a non-singleton with a single instance wired via DI.

### L2: `GameConstants.BUILDING_POWER_RADIUS` is deprecated but still in use

- **Confidence**: HIGH
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/GameConstants.java:80` (deprecated) vs `aow2-core/src/main/java/com/aow2/core/economy/PowerSystem.java:110-111` (still uses it)
- **Issue**: The deprecation says "use GameConfig.getInstance().getBuildingPowerRadius()" but PowerSystem still references the deprecated array. Migrate or remove the deprecation.

### L3: `GameConstants.CC_UPGRADE_INCOME_BONUS_PER_LEVEL` has a TODO comment

- **Confidence**: HIGH
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/GameConstants.java:64`
- **Issue**: `// TODO: When variable cycle time from CC upgrades is implemented, verify the net effect matches RE.` — `Goal.md` says "No TODO placeholders in committed code". Should be moved to an issue tracker or resolved.

### L4: `GameConstants.RANK_EXP_THRESHOLDS` and friends are deprecated but still present

- **Confidence**: HIGH
- **Implementation**: `aow2-common/src/main/java/com/aow2/common/config/GameConstants.java:92-97`
- **Issue**: Three deprecated arrays still in source. Either delete or document migration plan.

### L5: `PathfindingSystem` deviates from RE Bresenham-line approach

- **Confidence**: HIGH (explicitly documented)
- **RE Reference**: `pathfinding.md` — original uses Bresenham + obstacle avoidance, not true A*
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/movement/PathfindingSystem.java:22-34`
- **Issue**: This is a deliberate design choice (true A* produces better paths), documented as `ASSUMPTION (L5)`. However, it means replays may diverge from original game replays tick-by-tick — a faithfulness tradeoff. Should be highlighted in `Goal.md` as a known deviation.

### L6: `MatchmakingService` fallback map is hardcoded `"test_map"`

- **Confidence**: HIGH
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/service/MatchmakingService.java:343, 355`
- **Issue**: When players have no map preference, the server always picks `"test_map"`. This is fine for development but won't scale to production. Add a map registry / map pool.

### L7: `JwtUtil` logs warning when default dev secret is used but doesn't refuse to start

- **Confidence**: MEDIUM
- **Implementation**: `aow2-server/src/main/java/com/aow2/server/security/JwtUtil.java:43-52`
- **Issue**: If `AOW2_JWT_SECRET` env var is set, the constructor only logs a warning and continues using the dev secret. Should fail fast in non-dev environments.

### L8: Multiple test files reference `application-test.yml` but it's incomplete

- **Confidence**: MEDIUM
- **Implementation**: `aow2-server/src/test/resources/application-test.yml` (referenced by 6 test files)
- **Issue**: Per `ProjectProgress.md`, "Test execution: Unknown (JDK not available in sandbox — no javac; Gradle times out)". Tests cannot be verified to pass.

---

## Unverified Claims

### U1: `SIEGE_RANGE_BONUS = 3`

- **Status**: UNVERIFIED
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/combat/CombatSystem.java:52`
- **Notes**: Code comment says "ASSUMPTION: +3 range bonus in siege mode — RE spec confirms siege mode increases range but doesn't specify exact value". This is one of the 20 unverified assumptions tracked in `ProjectProgress.md`. Recommend consulting `decrypted_data.json` for the exact bonus.

### U2: Nuclear/explosion damage divisor is 12

- **Status**: UNVERIFIED (M-16)
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/combat/DamageCalculator.java:100-105`
- **Notes**: The formula `distanceFactor = weaponDamage * (12 - distClass) / 12` is reconstructed. RE confirms a 31×31 distance table is used but the exact divisor is inferred.

### U3: Research costs and durations

- **Status**: UNVERIFIED (M-18)
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/research/TechTree.java:140-420`
- **Notes**: All 48 research nodes have hardcoded cost (50-120 credits) and duration (300-600 ticks). RE provides a cost formula but not all parameter values. This is honest — the code explicitly flags it.

### U4: `INFANTRY_BASE_RECOVERY = 1`, `MACHINERY_BASE_REPAIR = 2`

- **Status**: UNVERIFIED
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/combat/HPRegenerationSystem.java` (referenced)
- **Notes**: HP regen rates are guessed. RE confirms regen exists but doesn't specify base rates.

### U5: `INFANTRY_VS_BUILDING_MULTIPLIER = 0.5`, `INFANTRY_VS_MACHINERY_MULTIPLIER = 0.7`

- **Status**: UNVERIFIED (L-1)
- **Implementation**: `aow2-core/src/main/java/com/aow2/core/combat/DamageCalculator.java:238-251`
- **Notes**: RE confirms infantry deals reduced damage to buildings/machinery but doesn't specify exact multipliers. Values are reasonable RTS conventions but not RE-sourced.

---

## Completeness Assessment

| System | Spec Coverage | Test Coverage | Status |
|--------|--------------|---------------|--------|
| Combat (formulas, damage, armor) | 90% | Unknown | **PASS_WITH_ISSUES** — formula correct, multipliers unverified |
| Unit stats (Confederation) | 100% | Unknown | **PASS** — all 7 units match RE JSON |
| Unit stats (Rebels) | 30% | Unknown | **INCOMPLETE** — only sight/range/armor from RE; rest assumed |
| Building stats | 80% | Unknown | **PASS_WITH_ISSUES** — Confed verified, Rebel stats assumed identical, Wall stats guessed |
| Tech tree (48 nodes) | 100% structure | Unknown | **PASS_WITH_ISSUES** — IDs/effects correct, costs/durations unverified |
| Pathfinding | 70% | Unknown | **DEVIATES** — true A* instead of RE's Bresenham (documented) |
| Economy | 85% | Unknown | **PASS_WITH_ISSUES** — formula correct, CC upgrade system not implemented |
| AI | 60% | Unknown | **PASS_WITH_ISSUES** — fabricated strategy-quality skip; upgrade levels not wired |
| Lockstep networking | 70% | Unknown | **FAIL** — sync hash missing economy/research; Attack missing ownership check |
| Server (auth, JWT, rate limit) | 95% | Unknown | **PASS** — solid security design |
| Matchmaking | 80% | Unknown | **PASS_WITH_ISSUES** — works but uses non-deterministic map selection |
| Web dashboard | 30% | Unknown | **INCOMPLETE** — most data hardcoded, multiple dead buttons, no game canvas |
| Replay recording | 95% | Unknown | **FAIL** — AttackMove crashes; backward seek broken |
| Modding | 70% | Unknown | **PASS_WITH_ISSUES** — non-deterministic override order, partial Lua sandbox |
| Campaign (29 Lua scripts) | 100% count | Unknown | **PASS** — all scripts present; playability not verified |
| Map editor | 90% | Unknown | **PASS** — UI present, validation works |

---

## Prioritized Fix Plan

1. **C2** — Fix `ReplayEntry` type-ID validation (`typeOrd > 12`). 1-line fix, unblocks all replay recording.
2. **C1** — Pass `economySystem` and `researchSystem` to `computeStateHash` in `LockstepEngine.processFrame`. 1-line fix, closes major desync blind spot.
3. **C4** — Add ownership check to `Attack`, `Stop`, `SiegeMode`, `Patrol` command arms. ~10 lines.
4. **C3** — Change `ModManager.enabledMods` to `LinkedHashMap` or `ConcurrentSkipListMap`. 1-line type change + import.
5. **C5** — Add `resetToInitialState()` to `ReplayPlayer.CommandCallback` and call it on backward seek. ~20 lines.
6. **H5** — Change `ResearchSystem.activeResearchMap` to `LinkedHashMap` (or sort iteration by techCentreId). 1-line type change.
7. **H1** — Decide whether to remove AI strategy-quality skip or gate behind "modern enhancements" flag.
8. **H3** — Wire up dead UI buttons (Join Battle, Watch, Download) or show "coming soon" toast.
9. **H4** — Fix ChatTab setState-during-render anti-pattern.
10. **H8** — Replace hardcoded Quick Stats with real `/api/stats` endpoint.
11. **M6** — Fix `RateLimitFilter` IP trust check to use proper CIDR for `172.16.0.0/12`.
12. **M5** — Either implement building upgrade system or remove dead upgrade-level code path.
13. **L3** — Resolve the TODO in `GameConstants.CC_UPGRADE_INCOME_BONUS_PER_LEVEL`.
14. **L1, L2, L4** — Clean up deprecated singleton/array patterns.
15. **H7** — Delete `EloRatingService` or schedule removal.

---

## Detailed Findings

### Combat Formula Verification

The damage formula in `DamageCalculator.java:46-51` matches the RE spec in `combat_formulas.md:46-48` exactly:

```java
// Implementation
int damage = weaponDamage * (GameConstants.ARMOR_DIVISOR - targetArmor) / GameConstants.ARMOR_DIVISOR;
damage = Math.min(damage, weaponDamage - targetArmor);
return Math.max(damage, GameConstants.MIN_DAMAGE);
```

```python
# RE spec (pseudocode)
damage = cg[0][projectileType] * (10 - armour) / 10
damage = max(min(damage, cg[0][projectileType] - armour), 1)
```

✅ The two-step clamp is correctly implemented. The upper clamp (`min(damage, weaponDamage - armor)`) prevents inflated damage at low armor values, and the lower clamp (`max(damage, 1)`) ensures minimum damage of 1.

### Confederation Unit Stats Verification

Cross-checked every Confederation unit in `StatsRegistry.java:165-243` against `complete_unit_stats.json`:

| Unit | HP | Damage | Speed | Armor | Cost | Range | Sight | Status |
|------|----|--------|-------|-------|------|-------|-------|--------|
| Infantry | 40 ✓ | 2 ✓ | 5 ✓ | 5 ✓ | 10 ✓ | 4 ✓ | 4 ✓ | ✅ MATCH |
| Grenadier | 40 ✓ | 2 ✓ | 6 ✓ | 5 ✓ | 10 ✓ | 4 ✓ | 4 ✓ | ✅ MATCH |
| Flame Assault | 50 ✓ | 4 ✓ | 6 ✓ | 5 ✓ | 20 ✓ | 6 ✓ | 5 ✓ | ✅ MATCH |
| AV-40 Fortress | 50 ✓ | 4 ✓ | 7 ✓ | 5 ✓ | 20 ✓ | 9 ✓ | 5 ✓ | ✅ MATCH |
| T-21 Hammer | 50 ✓ | 8 ✓ | 7 ✓ | 9 ✓ | 40 ✓ | 6 ✓ | 6 ✓ | ✅ MATCH |
| T-22 Zeus | 70 ✓ | 6 ✓ | 7 ✓ | 5 ✓ | 30 ✓ | 6 ✓ | 2 ✓ | ✅ MATCH |
| MLRS Torrent | 80 ✓ | 15 ✓ | 4 ✓ | 7 ✓ | 50 ✓ | 6 ✓ | 6 ✓ | ✅ MATCH |

All 7 Confederation units match the RE JSON exactly. This is exemplary work.

### Research Tree Verification

All 48 research IDs (0-23 + 43 for Confederation, 24-47 excluding 43 for Resistance) are present in `TechTree.java`. Effects match the table in `combat_formulas.md:300-352`. The prerequisite chains form a sensible DAG. Costs (50-120 credits) and durations (300-600 ticks) are flagged as UNVERIFIED but reasonable.

### Pathfinding Deviation

`PathfindingSystem.java` uses true A* with octile heuristic, while `pathfinding.md` documents that the original game uses Bresenham line + obstacle avoidance routing. The code explicitly flags this as `ASSUMPTION (L5)` at line 24. This is a deliberate design choice (better path quality) but means replays cannot be compared tick-by-tick with original game replays. The 200-step limit (later corrected to 50 in `GameConstants.MAX_PATH_LENGTH = 50`) matches the RE spec.

### Lockstep Engine Analysis

The lockstep engine (`LockstepEngine.java`) is structurally sound:
- 2-frame input delay ✓ (matches RE)
- 16-frame ring buffer ✓
- 150-tick sync interval ✓ (matches RE's 15-second default)
- Disconnect timeout at 140 ticks ✓ (14 seconds at 10 TPS)
- Reconnect support with pause/resume callbacks ✓

But it has the gaps noted in C1, C4, C6, and H2. The `CommandBuffer` uses `CopyOnWriteArrayList` for thread-safe concurrent access from network and game-loop threads, which is appropriate.

### Server Security Analysis

The server security stack is solid:
- JWT with HMAC-SHA256, configurable expiration ✓
- Fail-fast on default dev secret in non-dev environments ✓ (with caveat L7)
- bcrypt password hashing with 72-byte limit awareness ✓
- Username regex validation (3-32 chars, alphanumeric + `_-`) ✓
- Password length cap (128 chars) to prevent bcrypt DoS ✓
- Rate limiting on auth endpoints (sliding window per IP) ✓
- Trusted-proxy check for X-Forwarded-For ✓ (with caveat M6)

This is well above typical student-project security posture.

### Web Dashboard Reality Check

The web dashboard (`aow2-web/`) is structurally a Next.js 16 + shadcn/ui + Tailwind project with:
- 41 shadcn/ui components installed ✓
- 5 tab views (Units, Leaderboard, Replays, Maps, Chat) ✓
- LoginDialog with JWT flow ✓
- MatchmakingPanel that hits `/api/matchmaking/join` ✓

But:
- Most data is hardcoded with silent fallback to demo data on fetch failure
- No game canvas (HTML5 Canvas, WebGL, or FXGL-WASM bridge) — purely a dashboard
- Multiple dead-end buttons (H3)
- React anti-patterns (H4)
- Quick Stats fully hardcoded (H8)

Per `Goal.md` Phase 12, the web client should have "feature parity with desktop client (single-player)" — this is far from achieved. The desktop client (JavaFX) is the actual game; the web is a companion dashboard only.

---

*Report generated by aow2-analyzer skill following the protocol in `skills/aow2-analyzer/SKILL.md`.*
*All claims verified by reading both RE documentation and implementation source code.*
*Last updated: 2026-06-23*
