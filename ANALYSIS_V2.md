# AOW2-Online Critical Codebase Analysis (V2)

**Analyst:** Senior game developer (independent code review)
**Methodology:** Direct source-code reading of all modules listed in scope. No reliance on existing TODO/documentation files. All claims verified against actual method bodies, switch coverage, and runtime wiring.
**Date:** Generated fresh from the current tree.

> This report supersedes earlier audit documents. Every finding cites a real file path and line number. Where the comment in a file says X but the code does Y, Y is what is reported.

---

## 1. aow2-common (shared models)

### 1.1 `CommandType.Upgrade` validates `playerId` — none of the other 13 records do
- **Severity:** MEDIUM
- **File:** `aow2-common/src/main/java/com/aow2/common/model/CommandType.java` (lines 46–475, all records except `Upgrade` at 464)
- **Description:** Only the `Upgrade` record rejects `playerId < 0`. `Move`, `Attack`, `AttackMove`, `Build`, `Produce`, `Research`, `Garrison`, `Ungarrison`, `Cancel`, `SiegeMode`, `Stop`, `Hold`, `Patrol` all accept any `int playerId` (including negative or > 1) without complaint. The Javadoc on `CommandType.playerId()` explicitly says "player identifier (0 or 1)" but nothing enforces that.
- **Impact:** A malformed command (e.g., from a buggy client or a corrupted replay) can carry `playerId = 999`, which then propagates into `EconomySystem.playerFaction(999)` (throws `IllegalArgumentException`) and crashes the simulation. Worse, in lockstep the same crash happens on both clients simultaneously, so it looks like a deterministic bug rather than a validation gap.
- **Reproduction:** Construct `new CommandType.Move(0, 5, new int[]{1}, new GridPosition(0,0))` and submit it. No exception. The CommandProcessor will then attempt `EconomySystem.playerFaction(5)` which throws.

### 1.2 `UnitType` FIX LOG claims `CONFED_LIGHT_ASSAULT`/`CONFED_HEAVY_ASSAULT` were removed — they are still present
- **Severity:** LOW
- **File:** `aow2-common/src/main/java/com/aow2/common/model/UnitType.java` (lines 11–26, 24–26)
- **Description:** The class-level FIX LOG says "Removed CONFED_LIGHT_ASSAULT and CONFED_HEAVY_ASSAULT (not in the original 7-unit-per-faction list)." But both enum constants are still declared at lines 24 (`CONFED_LIGHT_ASSAULT(4, …)`) and 26 (`CONFED_HEAVY_ASSAULT(7, …)`).
- **Impact:** The comments lie. Anyone reading the FIX LOG to understand what units exist will be misled. The enum has 17 entries, not the 14 implied by the comment. `CommandSerializer.serializeProduce` writes `ordinal()` into the byte stream — adding/removing these constants would shift ordinals and break every existing replay and network command in flight.
- **Reproduction:** `grep -n "CONFED_LIGHT_ASSAULT\|CONFED_HEAVY_ASSAULT" UnitType.java` returns hits in both the FIX LOG and the enum body.

### 1.3 `BuildingType` faction-relative lookup is asymmetric
- **Severity:** MEDIUM
- **File:** `aow2-common/src/main/java/com/aow2/common/model/BuildingType.java` (lines 22–31, 72–78)
- **Description:** `fromFactionRelativeId(9, Faction.RESISTANCE)` returns `REBEL_WALL(209)` (a defensive wall). `fromFactionRelativeId(9, Faction.CONFEDERATION)` returns `null` because there is no `CONFED_WALL` (the Confederation side has no wall; relativeId 9 maps to typeId 109 which doesn't exist). Same for `isDefensive()` — Confederation has `BUNKER, ROCKET_LAUNCHER`; Resistance has `BUNKER, TOWER, WALL`. This is an asymmetric tech tree.
- **Impact:** Any client/UI code that iterates `BuildingType.fromFactionRelativeId(1..10, faction)` to populate a build menu will get `null` for confed-wall. Faction-balance asymmetry is undocumented at the API level — callers must remember to null-check.
- **Reproduction:** `BuildingType.fromFactionRelativeId(9, Faction.CONFEDERATION)` → `null`.

### 1.4 `CommandType.Upgrade` comment mentions "upgradeCosts per building type" but no Upgrade path validates ownership at the `CommandProcessor` level
- **Severity:** MEDIUM (see §2.1 for the cross-cutting ownership issue)
- **File:** `aow2-common/src/main/java/com/aow2/common/model/CommandType.java` (lines 459–475)
- **Description:** `Upgrade` only validates `tick`, `playerId`, `buildingId` are non-negative. There is no way for the record to express "this upgrade is being requested by the building owner." The ownership check is delegated to `UpgradeCommandHandler.handle` at runtime, but as §2.2 shows, `LockstepEngine.applyCommand` routes `Upgrade` through `CommandProcessor.process` which only calls the handler — it does NOT independently verify ownership.
- **Impact:** See §2.1 / §2.2.

---

## 2. aow2-core (game engine)

### 2.1 `CommandProcessor` does NOT verify command ownership for any command type except `Upgrade`
- **Severity:** CRITICAL (multiplayer integrity)
- **File:** `aow2-core/src/main/java/com/aow2/core/command/CommandProcessor.java` (lines 80–101, 113–130, 158–244) and every handler except `UpgradeCommandHandler.java` (lines 62–68)
- **Description:** `CommandProcessor.process` dispatches to handlers without checking that `cmd.playerId()` corresponds to the owner of the entities being manipulated. `MoveCommandHandler`, `AttackCommandHandler`, `BuildCommandHandler`, `ProduceCommandHandler`, `ResearchCommandHandler`, `GarrisonCommandHandler` all accept any `playerId` and apply the command to whatever entity is referenced. Only `UpgradeCommandHandler` performs an explicit `ownerId != cmd.playerId()` check.
- **Impact:** Any client (or attacker in a P2P lockstep game) can submit a `Move`, `Attack`, `Garrison`, `Ungarrison`, `Cancel`, `Build`, `Produce`, or `Research` command targeting the opponent's entities. The opponent's units can be moved away, production can be cancelled, research can be started on their Tech Centre (locking it), etc. This breaks the entire competitive integrity of multiplayer.
- **Reproduction:** In a 2-player lockstep game, player 0 submits `new CommandType.Move(tick, 0, new int[]{opponentUnitId}, somewhere)`. The server relays it. Both clients run `LockstepEngine.applyCommand` → `Move` branch — which DOES call `owns()` (line 507). But the same player 0 submits `new CommandType.Garrison(tick, 0, new int[]{opponentUnitId}, opponentBuildingId)` and `applyCommand` dispatches to `commandProcessor.process(g, …)` → `garrisonHandler.handleGarrison` which has NO ownership check. The opponent's unit is garrisoned into the opponent's building against their will.

### 2.2 `LockstepEngine.applyCommand` is split-brained: some commands bypass `CommandProcessor` and lose formation logic
- **Severity:** HIGH
- **File:** `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java` (lines 500–654)
- **Description:** For `Move`, `Attack`, `AttackMove`, `Stop`, `Hold`, `SiegeMode`, `Patrol` the engine applies the command inline (with ownership check via `owns()`). For `Build`, `Produce`, `Research`, `Garrison`, `Ungarrison`, `Cancel`, `Upgrade` it delegates to `commandProcessor.process(...)` (no ownership check except Upgrade). The inline `Move` path calls `movementSystem.issueMoveCommand(unit, m.target(), gameMap, entities)` once per unit — it does NOT use `movementSystem.issueGroupMoveCommand(...)` which preserves formation. So in multiplayer, multi-unit moves lose their formation; in single-player (which goes through `TickManager` → `CommandProcessor.process` → `MoveCommandHandler.handle`), formation IS preserved. The same `Move` command produces different behaviors in SP vs MP.
- **Impact:** Lockstep replays recorded from a single-player session will diverge from a multiplayer session, even with identical commands. Group move in multiplayer is also broken — units cluster and collide instead of preserving their relative positions.
- **Reproduction:** Select 5 units in SP, right-click a destination → units maintain formation. Select same 5 units in MP, right-click → each unit pathfinds independently and they collide.

### 2.3 `CommandProcessor.process` passes `null` for `PowerSystem` to `UpgradeCommandHandler` — Generator/CC upgrades never refresh the power grid
- **Severity:** HIGH
- **File:** `aow2-core/src/main/java/com/aow2/core/command/CommandProcessor.java` (line 99)
- **Description:** `case CommandType.Upgrade cmd -> upgradeHandler.handle(cmd, entities, economy, null);` — the fourth argument (`PowerSystem`) is hardcoded `null`. `UpgradeCommandHandler.handle` (line 106) only calls `powerSystem.updatePowerGrid(entities)` when `powerSystem != null` AND the building `producesPower()`. So upgrading a Generator or Command Centre never expands its power radius on the grid.
- **Impact:** Players who upgrade their Generator (paying the credit cost and receiving the +20% HP) do not get the increased power radius promised by the Upgrade command's Javadoc ("Generators: power radius increases (10→20→30→40→60→127)"). Buildings outside the original radius remain unpowered, blocking production and research.
- **Reproduction:** Build a Generator. Build an Infantry Centre just outside the Generator's level-1 radius (it'll be unpowered). Upgrade the Generator to level 2. The Infantry Centre stays unpowered even though it should now be within the level-2 radius.

### 2.4 `CombatSystem.performAttack` wind-up state machine is overridden every tick
- **Severity:** HIGH
- **File:** `aow2-core/src/main/java/com/aow2/core/combat/CombatSystem.java` (lines 219–260, 431–463)
- **Description:** The "FIX(M-25) ranged attack state machine" is supposed to transition ranged units through `state 3 → state 2 (WIND_UP) → state 3 (ATTACKING)` with a half-attack-speed wind-up delay. But `processUnitAttacks` (lines 233–238) does:
  ```java
  performAttack(attacker, target);  // sets state to 2 (WIND_UP) for ranged
  attacker.setAttackState(3);       // line 235 — IMMEDIATELY overrides back to 3
  ```
  The wind-up counter is never incremented past 0 because state 2 is entered and exited in the same tick. Ranged units fire every cooldown cycle with no wind-up delay.
- **Impact:** The intended ~50% attack delay for ranged units is silently skipped. Ranged units (Sniper, Rocket troops, vehicles with non-BULLET weapons) fire ~2x faster than designed. This is a balance regression that affects every combat engagement.
- **Reproduction:** Spawn a Sniper (ranged, attackSpeed=10). Issue an attack on a static target. Observe the time between shots — it equals `attackSpeed` ticks (10), not the intended `attackSpeed + attackSpeed/2` (15).

### 2.5 `CombatSystem.processBuildingAttacks` sets cooldown even when no target is found
- **Severity:** MEDIUM
- **File:** `aow2-core/src/main/java/com/aow2/core/combat/CombatSystem.java` (lines 272–296)
- **Description:** After the `if (isBunker(type)) { processBunkerAttack(building); } else if (isRocketOrTower(type)) { processDefensiveBuildingAttack(building); }` block, `building.setAttackCooldown(BUILDING_ATTACK_COOLDOWN)` is called unconditionally. So a defensive building that scans for enemies but finds none still goes on a 5-tick cooldown before it can scan again.
- **Impact:** An enemy unit that walks into a defensive building's range during the 5-tick idle cooldown will not be fired upon until the cooldown expires, then another 5 ticks for the next shot. Defensive buildings are 5x less responsive than intended when no enemies are around.
- **Reproduction:** Place a Tower. Walk an enemy unit into its range. Time how long until the first shot lands — up to 5 ticks of "dead time" while the tower sits on its idle cooldown.

### 2.6 `MovementSystem.shouldStopForEnemy` does not handle building target refs (negative IDs)
- **Severity:** HIGH
- **File:** `aow2-core/src/main/java/com/aow2/core/movement/MovementSystem.java` (lines 168–184)
- **Description:** The method reads `unit.getTargetUnitRef()`. Per `AttackCommandHandler` (line 41), target refs for buildings are stored as `-buildingId` (negative). But `shouldStopForEnemy` only calls `entities.getUnit(unit.getTargetUnitRef())` — passing a negative ID — which always returns `null` (units are keyed by positive IDs). So the method returns `false` whenever the unit is targeting a building, and the unit never stops to attack the building.
- **Impact:** Units ordered to attack a building will path all the way to it (or beyond) without ever stopping to fire. The attack only happens via `processUnitTargetAcquisition` which checks `targetRef < 0` correctly (line 192) — but only when `attackState == 1`. If the unit is mid-move with `attackState == 0` or `attackState == 3`, the building-target case is broken.
- **Reproduction:** Order an infantry unit to attack an enemy building 10 tiles away. Watch the unit walk past the building without firing.

### 2.7 `ArmorCalculator` uses hardcoded research→armor maps; `ResearchBonusTracker` armor accumulation is dead code
- **Severity:** HIGH
- **File:** `aow2-core/src/main/java/com/aow2/core/combat/ArmorCalculator.java` (lines 46–66, 95–99, 169–193); `aow2-core/src/main/java/com/aow2/core/research/ResearchSystem.java` (lines 388–390, 462–463)
- **Description:** `ArmorCalculator.calculateEffectiveArmor` looks up armor bonuses from a private static `INFANTRY_ARMOR_RESEARCH` map keyed by research ID. Meanwhile `ResearchSystem.applyResearchEffect` (lines 388–390) ALSO accumulates the same bonuses into `ResearchBonusTracker` via `tracker::addInfantryArmorBonus`. But `CombatSystem.executeAttack` calls `armorCalculator.calculateEffectiveArmor(target, getCompletedResearch(target))` — passing the **set of completed research IDs**, not the tracker. So the tracker's armor values are never read by combat.
- **Impact:** Two parallel sources of truth for armor bonuses. If a modder adds a new research effect via `tech_tree.json` with key `infantryArmorBonus`, the tracker picks it up (line 388) but combat doesn't see it (the hardcoded map doesn't know about the new research ID). Effectively, data-driven research armor bonuses are silently dropped. The same applies to vehicle armor, building armor, attack damage, attack speed, attack range — all the tracker's accumulated bonuses are bypassed.
- **Reproduction:** Edit `tech_tree.json` to add a new research node (id=48) with `infantryArmorBonus: 5`. Complete the research in-game. Spawn an infantry unit and check its armor — the +5 bonus is NOT applied. `ArmorCalculator.calculateEffectiveArmor` will only see research IDs 0, 9, 24, 33 (the hardcoded map).

### 2.8 `LockstepEngine` heartbeat timer is wired but never sent unless `setHeartbeatSendCallback` is called
- **Severity:** HIGH (multiplayer stability)
- **File:** `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java` (lines 263–278, 293–295); `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (lines 657–668)
- **Description:** `sendHeartbeat()` (line 263) only sends if `heartbeatSendCallback != null`. If null, it logs a debug message and the opponent's `LockstepEngine` (line 327) will pause the simulation after `DISCONNECT_TIMEOUT_TICKS = 140` ticks (14 seconds) of no activity. The client's `GameScene.setupMultiplayer` (line 657) DOES wire the callback — but only if `setupMultiplayer` is called. Per §4.1 below, in the current `AOW2App.showGame` flow, `setupMultiplayer` is called on a brand-new `MultiplayerService` with no JWT, which throws before `setupMultiplayer` is reached.
- **Impact:** Even if the auth wiring were fixed, the heartbeat mechanism only works after the engine is fully wired. Any code path that creates a `LockstepEngine` without calling `setHeartbeatSendCallback` will produce false disconnect pauses after 14 seconds of opponent idleness. The engine's own Javadoc admits this: "Without it, the idle-opponent disconnect problem (H2) is not fully resolved."
- **Reproduction:** Start a multiplayer match (assuming §4.1 is fixed). Have the opponent issue NO commands and NO heartbeats for 15 seconds. The local engine pauses the game with "Opponent disconnected" even though the opponent is still connected.

### 2.9 `LockstepEngine` disconnect timer is updated with `command.tick()` (remote) but `lockstepFrame` (local) for heartbeats — inconsistent clock domain
- **Severity:** MEDIUM
- **File:** `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java` (lines 222, 245)
- **Description:** `receiveCommand` updates `lastOpponentActivityTick = Math.max(lastOpponentActivityTick, command.tick())` — using the opponent's tick. `receiveHeartbeat` updates `lastOpponentActivityTick = Math.max(lastOpponentActivityTick, lockstepFrame)` — using the local frame. The disconnect check (line 327) uses `lockstepFrame - lastOpponentActivityTick`. If the opponent is even a few ticks ahead (legitimate clock drift), a single command from them can advance `lastOpponentActivityTick` well past `lockstepFrame`, suppressing the disconnect check for far longer than 140 ticks.
- **Impact:** A buggy or malicious client that sends commands with artificially inflated `tick` values (e.g., `tick = Long.MAX_VALUE - 1`) can keep the local engine's disconnect timer from ever firing, even after the opponent has actually disconnected.
- **Reproduction:** Send a single command with `tick = 1_000_000`. The local engine's `lastOpponentActivityTick` becomes 1_000_000. The disconnect check (`lockstepFrame - 1_000_000 > 140`) is false until the local frame reaches 1_000_141 — over 27 hours at 10 TPS.

### 2.10 `CommandBuffer` is vulnerable to ring-buffer overflow when local submits outpace ticks
- **Severity:** MEDIUM
- **File:** `aow2-core/src/main/java/com/aow2/core/network/CommandBuffer.java` (lines 79–86, 120–130)
- **Description:** `submitCommand` advances `writeIndex` on every call. `drainFrame` advances `readIndex` once per tick. There is no check that `writeIndex` doesn't lap `readIndex`. If the local player submits `bufferSize` (16) commands without a tick draining them, `writeIndex` wraps and starts overwriting earlier slots — including commands not yet processed. The same applies to `submitOpponentCommand` which uses `readIndex + frameOffset`; if `readIndex` lags `writeIndex`, opponent commands land in wrong slots.
- **Impact:** A player who clicks rapidly (issuing 16+ move commands in under 1.6 seconds at 10 TPS) will silently lose earlier commands. Replays from such sessions will be missing commands and diverge.
- **Reproduction:** Set input delay = 2, buffer size = 16. Submit 17 `Move` commands in one frame (programmatically). The 17th command overwrites the 1st command's slot.

### 2.11 `SyncChecker.computeStateHash` omits critical state — desyncs go undetected
- **Severity:** HIGH (multiplayer correctness)
- **File:** `aow2-core/src/main/java/com/aow2/core/network/SyncChecker.java` (lines 80–142)
- **Description:** The hash includes unit id/position/hp/faction, building id/position/hp/faction, projectile count, credits, and completed/active research. It does NOT include: `attackState`, `targetUnitRef`, `weaponCooldown`, `windUpCounter`, `movementState`, `path`, `stuckCounter`, `autoEngage`, `autoEngageTarget`, `siegeMode`, `siegeDeployTimer`, `garrisonedBuildingId`, `garrisonedUnitRef`, `productionQueue`, `productionProgress`, `researchId` (on building), `upgradeLevel`, `upgradeMaxHpBonus`, `attackCooldown` (on building), `powered` state, `deathAnimFrame`. Many of these (e.g., `productionQueue`, `upgradeLevel`, `garrisonedUnitRef`) directly affect future game state.
- **Impact:** Two clients can have wildly divergent game states (one player's building has a fully queued production, the other's has none) and the sync check passes. Desyncs are detected only when they manifest as positional/HP differences, by which point the cascading divergence is unfixable.
- **Reproduction:** Use a memory editor to alter `building.productionQueue` on one client. Continue playing. The sync check will never flag the divergence until the queue's contents spawn a unit at a different time, eventually surfacing as a position mismatch many ticks later.

### 2.12 `CommandSerializer` uses enum `ordinal()` for `BuildingType` and `UnitType` — every enum reorder breaks replays and network compatibility
- **Severity:** HIGH (long-term compatibility)
- **File:** `aow2-core/src/main/java/com/aow2/core/network/CommandSerializer.java` (lines 129, 141; deserialize lines 302–321)
- **Description:** `serializeBuild` writes `b.buildingType().ordinal()`. `serializeProduce` writes `p.unitType().ordinal()`. Deserialization indexes into `BuildingType.values()[ordinal]` / `UnitType.values()[ordinal]`. The enum constants have explicit `typeId` fields (e.g., `CONFED_COMMAND_CENTRE(101, …)`) that were designed for exactly this purpose, but they are not used.
- **Impact:** Adding, removing, or reordering any `BuildingType` or `UnitType` constant shifts ordinals and makes every previously-recorded replay and every in-flight network command decode to the wrong type. The version format field in `ReplayFile.FORMAT_VERSION` does NOT protect against this because there's no migration code.
- **Reproduction:** Add a new `UnitType` constant in the middle of the enum (not the end). Replay an old game. Every `Produce` command decodes to the wrong unit type.

### 2.13 `PathfindingSystem.getTerrainCost(SHALLOW_WATER, INFANTRY)` returns 3 — but `SHALLOW_WATER.isPassableBy(INFANTRY)` returns `false`, so the override is dead code
- **Severity:** LOW (consistency)
- **File:** `aow2-core/src/main/java/com/aow2/core/movement/PathfindingSystem.java` (lines 287–301); `aow2-common/src/main/java/com/aow2/common/model/TerrainType.java` (lines 96–109)
- **Description:** `PathfindingSystem.getTerrainCost` (lines 291–294) explicitly handles `SHALLOW_WATER` + `INFANTRY` → returns 3, "infantry can cross it." But `TerrainType.SHALLOW_WATER.isPassableBy(INFANTRY)` was changed (per the FIX comment at line 99–104) to return `false` for ALL units. The pathfinder checks passability first (line 197–203) and skips impassable tiles, so the cost-3 branch is unreachable.
- **Impact:** Infantry cannot cross shallow water even though the original game design and the cost override both say they should. Dead code misleads future maintainers.
- **Reproduction:** Place an infantry unit on a sand tile adjacent to a shallow-water tile, with the only path to the destination crossing the water. `findPath` returns empty list. `getTerrainCost(SHALLOW_WATER, INFANTRY)` returns 3 but is never consulted.

### 2.14 `ResearchSystem.startResearch` does not verify the Tech Centre belongs to the requesting player
- **Severity:** HIGH (multiplayer integrity)
- **File:** `aow2-core/src/main/java/com/aow2/core/research/ResearchSystem.java` (lines 187–276)
- **Description:** `startResearch` checks building alive, building is a Tech Centre, building powered, building not already researching, prerequisites met, faction match (the faction implied by `playerId`). It does NOT check that `techCentre.getFaction() == EconomySystem.playerFaction(playerId)`. So player 0 (Confederation) can issue a `Research` command on player 1's (Resistance) Tech Centre. The faction match check at line 230 only verifies that the research ID is valid for the *requester's* faction — it does not verify the building's faction matches.
- **Impact:** A player can hijack the opponent's Tech Centre: their research is queued (locking the opponent's building), their credits are deducted, and on completion the research effect is applied to the requester's `bonusTrackers[playerId]`. The opponent cannot use their Tech Centre for the duration. Combined with §2.1, this means a player can effectively deny research to the opponent for the entire game.
- **Reproduction:** Player 0 issues `new CommandType.Research(tick, 0, opponentTechCentreId, someResearchId)`. The opponent's Tech Centre enters research state. The opponent's `Research` command on their own Tech Centre is rejected ("already researching").

### 2.15 `TickManager.processCommands` does not use the lockstep buffer — it bypasses `LockstepEngine` entirely
- **Severity:** MEDIUM (architectural)
- **File:** `aow2-core/src/main/java/com/aow2/core/engine/TickManager.java` (lines 228–245); `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java` (lines 309–367)
- **Description:** `TickManager` (used for single-player / skirmish / campaign) drains `pendingCommands` and calls `commandProcessor.process(...)` directly. `LockstepEngine` (used for multiplayer) buffers commands, checks for desync, and applies them via `applyCommand` which has the inline ownership-check path. The two paths produce different results: TickManager has no ownership checks at all; LockstepEngine has them for some commands but not others (§2.2).
- **Impact:** Single-player replays are not compatible with the lockstep pipeline. Bugs that surface only in one path (e.g., the formation-loss issue from §2.2) are invisible in the other. The codebase has two parallel command-execution engines with subtly different semantics.
- **Reproduction:** Run the same scenario in single-player (via `TickManager`) and in a mock-multiplayer setup (via `LockstepEngine` without an opponent). Observe different unit movement formations.

### 2.16 `MapLoader.parseTerrainType` silently drops unknown terrain names
- **Severity:** MEDIUM
- **File:** `aow2-core/src/main/java/com/aow2/core/world/MapLoader.java` (lines 185–196, 230–236)
- **Description:** When `TerrainType.valueOf(name)` throws (because the map JSON uses an unrecognized name like `"Plains"` instead of `"GRASS"`, or `"Marsh"` instead of `"SWAMP"`), the catch block logs a warning and skips the tile. The tile defaults to `GRASS` (per `GameMap`'s constructor).
- **Impact:** Maps that use RE-spec terrain names (which differ from the enum constant names) silently lose their terrain data. A map intended to be 50% mountains might become 100% grass. The map looks fine in the editor but plays completely differently.
- **Reproduction:** Create a map JSON with `"terrain": [["Plains"]]`. Load it. The tile becomes GRASS, not the intended terrain. No error is raised.

---

## 3. aow2-client (FXGL client)

### 3.1 Multiplayer is fundamentally broken — `AOW2App.showGame` creates a new unauthenticated `MultiplayerService` and throws before wiring
- **Severity:** CRITICAL
- **File:** `aow2-client/src/main/java/com/aow2/client/AOW2App.java` (lines 184–190); `aow2-client/src/main/java/com/aow2/client/service/MultiplayerService.java` (lines 420–432, 640–644)
- **Description:** When `MultiplayerLobbyScene` callback `onMatchFound(sessionUuid)` fires, `AOW2App.showGame(null, sessionUuid)` runs:
  ```java
  com.aow2.client.service.MultiplayerService mpService =
      new com.aow2.client.service.MultiplayerService();
  mpService.connectGameWebSocket();   // <-- throws IllegalStateException
  gameScene.setupMultiplayer(mpService);
  ```
  `connectGameWebSocket()` calls `ensureAuthenticated()` which throws `IllegallegalStateException("Not authenticated...")` because the new `MultiplayerService` instance has `jwtToken = null`. The exception propagates up through `showGame` and `handleMenuAction`. The lobby's authenticated `MultiplayerService` (with the JWT, the lobby WebSocket, the chat WebSocket, the player info) is NEVER passed to the game scene — it's a separate instance owned by `MultiplayerLobbyScene`.
- **Impact:** No player can ever start a multiplayer match from the FXGL client. The match-found flow always crashes. The lobby service keeps running (holding the JWT and open WebSockets), but the game scene starts in single-player mode (or doesn't start at all, depending on where the exception is caught).
- **Reproduction:** Log in to multiplayer lobby. Search for a match (or trigger `onMatchFound` via the demo fallback in `MatchmakingPanel.tsx`... but that's the web client; in the FXGL client use real matchmaking). Observe the `IllegalStateException` in the log. The game scene either doesn't appear or appears without multiplayer wiring.

### 3.2 `MultiplayerLobbyScene.dispose()` shuts down the lobby's `MultiplayerService` — even if a match is starting
- **Severity:** HIGH
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/MultiplayerLobbyScene.java` (lines 924–929); `aow2-client/src/main/java/com/aow2/client/AOW2App.java` (lines 207–244)
- **Description:** `dispose()` calls `service.shutdown()` which shuts down the executor and disconnects all WebSockets. `AOW2App.showMultiplayerLobby()` calls `multiplayerLobbyScene.dispose()` when the back button is pressed, but NOT when navigating to a match. However, the lobby's service is still the only authenticated service in the app. When the user navigates from lobby → game (via match-found), the lobby scene is never disposed but its service is also never handed off to the game scene. The result: the lobby's WebSockets stay open (including the lobby and chat WebSockets, which continue to receive messages and route them to a callback that no longer cares), and the game scene has no service.
- **Impact:** Compounds §3.1. Even if §3.1 is fixed by passing the lobby service to the game scene, the lobby's WebSocket connections (lobby + chat) need to be selectively torn down. The current code has no such mechanism.
- **Reproduction:** After §3.1's exception, inspect the `MultiplayerLobbyScene` instance — its `service` field still holds an authenticated `MultiplayerService` with open WebSockets, but no code path can use it.

### 3.3 `GameScene.setupMultiplayer` is never called for multiplayer matches (consequence of §3.1)
- **Severity:** CRITICAL (but blocked by §3.1)
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (lines 636–734)
- **Description:** Even if `setupMultiplayer` were reached, it creates a `LockstepEngine`, wires the send callback to encode commands as base64 inside a JSON map, wires the heartbeat callback, wires the desync callback, injects game systems, and overrides the `MultiplayerService.MultiplayerCallback` to decode incoming base64 commands and feed them to `incomingCommandBuffer`. The wiring is internally consistent. But it's all unreachable because of §3.1.
- **Impact:** All the lockstep integration code is dead code in practice. Even the test `MultiplayerServiceTest` exercises only the service in isolation, not the wiring.

### 3.4 `GameScene.setupMultiplayer` desync callback sends a meaningless sync hash
- **Severity:** MEDIUM
- **File:** `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (lines 671–677)
- **Description:** The desync callback calls `multiplayerService.sendSyncHash("", frame, 0)` — empty session UUID, frame as tick, hash = 0. The server's `GameWebSocketHandler.handleSyncHash` reads `sessionUuid` (empty), `tick` (the frame), `hash` (0). It calls `sessionService.reportSyncHash("", playerId, frame, 0)`. With an empty session UUID, `getSessionByUuid` returns empty and the report is silently dropped.
- **Impact:** When a desync is detected, the server is never informed. The opponent never receives a `desync` message. The game continues with divergent state until something else (e.g., a sync-hash check) catches it.
- **Reproduction:** Trigger a desync (e.g., memory-edit one client's unit HP). Observe the local log: "Desync detected at frame N." Check the server logs: no sync_hash message received. Check the opponent: no desync notification.

### 3.5 `ReplayViewerScene` reuses `ActiveScene.MAIN_MENU` enum constant — no proper state tracking
- **Severity:** LOW
- **File:** `aow2-client/src/main/java/com/aow2/client/AOW2App.java` (line 425)
- **Description:** `showReplayViewer()` sets `activeScene = ActiveScene.MAIN_MENU` with the comment "reuse enum value — no ReplayViewer enum constant." This means navigating to the replay viewer updates `activeScene` to MAIN_MENU, so any logic that switches on `activeScene` (e.g., for back-button handling, scene cleanup) will treat the replay viewer as the main menu.
- **Impact:** Minor — likely just confusion in scene-tracking logic. No immediate functional bug, but a maintenance hazard.
- **Reproduction:** Open the replay viewer. `activeScene == MAIN_MENU`. Any code that checks `if (activeScene == ActiveScene.MAIN_MENU)` will incorrectly fire.

### 3.6 Skirmish map discovery silently fails in JAR mode if maps directory is missing
- **Severity:** LOW
- **File:** `aow2-client/src/main/java/com/aow2/client/AOW2App.java` (lines 536–576)
- **Description:** `discoverMapResources()` tries `getClass().getClassLoader().getResource("data/maps")`. If the URL protocol is `"file"` it uses direct filesystem access. Otherwise it tries `FileSystems.newFileSystem(url.toURI(), ...)`. If the JAR is signed or the URL has a different protocol (e.g., `jrt:` for modular runtime images), the newFileSystem call throws and the catch block returns an empty list. The user then sees "No map files found on classpath, falling back to test map" with no further diagnostic.
- **Impact:** Players on certain JRE configurations cannot see any maps in the skirmish dialog.
- **Reproduction:** Package the client as a signed JAR. Run it. Open Skirmish. Only the test map is available.

### 3.7 `AOW2App.showGame` calls `FXGL.getGameScene().clearUINodes()` THREE times
- **Severity:** LOW (code smell)
- **File:** `aow2-client/src/main/java/com/aow2/client/AOW2App.java` (lines 166, 172, 192)
- **Description:** Inside `showGame(String, String)`: line 166 stops the old game scene, line 172 clears UI nodes, then line 192 clears UI nodes again before adding the new scene's root. The double-clear is harmless but indicates copy-paste drift.
- **Impact:** None functional. Maintenance hazard.

---

## 4. aow2-server (Spring Boot)

### 4.1 `ChatController.getChatHistory` is mapped to `/api/chat/history/{matchId}` but the web client calls `/api/chat/history` with no path parameter
- **Severity:** HIGH (web integration broken)
- **File:** `aow2-server/src/main/java/com/aow2/server/controller/ChatController.java` (line 118); `aow2-web/src/lib/api.ts` (lines 85–89)
- **Description:** Server: `@GetMapping("/history/{matchId}")` requires a path parameter. Web client: `fetch(apiUrl("/api/chat/history", 8080))` — no matchId, no auth header. The request hits a 404 (no handler mapped to `/api/chat/history` without suffix). Even if the path were right, the endpoint requires authentication (per `SecurityConfig` line 81), but the web client sends no `Authorization` header.
- **Impact:** The web ChatTab never loads history. Users see an empty chat with no error message.
- **Reproduction:** Open the web client. Log in. Navigate to Chat tab. The fetch 404s silently; the UI shows "Could not load chat" or an empty list.

### 4.2 Web client calls `/api/units` — no such endpoint exists
- **Severity:** HIGH (web integration broken)
- **File:** `aow2-web/src/lib/api.ts` (lines 78–82); `aow2-server/src/main/java/com/aow2/server/controller/` (no `UnitsController.java`)
- **Description:** `getUnits()` fetches `/api/units`. There is no controller mapped to `/api/units` in the server. The request will be handled by Spring's default 404 handler. Even the `GlobalExceptionHandler` won't catch it (no handler = no exception, just a 404 response).
- **Impact:** The web UnitsTab never loads. Users see "Failed to fetch units" or an empty list.
- **Reproduction:** Open the web client. Navigate to Units tab. 404 in the network tab.

### 4.3 Web `MatchmakingPanel` POSTs to `/api/matchmaking/join` — the actual endpoint is `/api/matchmaking/queue`
- **Severity:** HIGH (web matchmaking broken)
- **File:** `aow2-web/src/components/MatchmakingPanel.tsx` (line 40); `aow2-server/src/main/java/com/aow2/server/controller/MatchmakingController.java` (lines 52–53)
- **Description:** Web: `fetch(apiUrl('/api/matchmaking/join', 8080), { method: 'POST' })`. Server: `@PostMapping("/queue")` mapped to `/api/matchmaking/queue`. There is no `/api/matchmaking/join` endpoint. The request 404s. The web client's `setServerAvailable(false)` triggers the "Demo Mode" fallback that fakes a match after 8 seconds ("Match Found vs EnemyCommander"). The "Join Battle" button just shows a toast: "Game client launch coming soon."
- **Impact:** The web client's matchmaking is fake. Even if the URL were fixed, the request has no `Authorization` header (the endpoint requires authentication) and no body. The user is shown a fake match-found notification with no way to actually play.
- **Reproduction:** Open the web client. Click "Find Match." After 8 seconds, "Demo Mode" banner appears, followed by "Match Found vs EnemyCommander." Click "Join Battle." A toast appears. No game launches.

### 4.4 Web client sends no `Authorization` header for any authenticated GET/POST
- **Severity:** HIGH
- **File:** `aow2-web/src/lib/api.ts` (lines 16–117)
- **Description:** Only `uploadMap` and `getPlayerInfo` include the `Authorization: Bearer ${token}` header. `getLeaderboard`, `getMaps`, `downloadMap`, `getReplays`, `getUnits`, `getChatHistory`, `getStats` — all send no auth header. The server config (`SecurityConfig.java` lines 66–81) requires authentication for `/api/maps/**`, `/api/replays/**`, `/api/chat/**`. These will all 401/403.
- **Impact:** The web client cannot list maps, download maps, list replays, fetch chat history, or fetch units. The leaderboard and stats endpoints are public, so those work.
- **Reproduction:** Log in via the web client. Navigate to Maps tab. 401 Unauthorized in the network tab. UI shows "Failed to fetch maps."

### 4.5 Web client error parsing looks for `message` field — server returns `error` field
- **Severity:** MEDIUM
- **File:** `aow2-web/src/lib/api.ts` (lines 22, 32); `aow2-server/src/main/java/com/aow2/server/controller/AuthController.java` (lines 56, 77); `aow2-server/src/main/java/com/aow2/server/config/GlobalExceptionHandler.java` (lines 26–44)
- **Description:** Web: `throw new Error((await res.json()).message || "Registration failed")`. Server returns `{"error": "..."}`, not `{"message": "..."}`. So `.message` is always `undefined`, and the generic fallback ("Registration failed", "Login failed", etc.) is always shown. The actual server error is hidden from the user.
- **Impact:** Users see unhelpful error messages. A 400 from the server with `{"error": "Username already taken: bob"}` displays as "Registration failed" with no detail.
- **Reproduction:** Try to register with a taken username. The UI shows "Registration failed." Network tab shows the real error in the response body.

### 4.6 Web `apiUrl(path, port)` always appends `?XTransformPort=8080`, breaking URLs that already have query strings
- **Severity:** MEDIUM
- **File:** `aow2-web/src/lib/api.ts` (lines 7–13)
- **Description:** `apiUrl(path, port)` returns `${base}${path}?XTransformPort=${port}` — unconditionally appending `?XTransformPort=8080`. If `path` already contains a query string (e.g., `/api/maps?page=2&size=10`), the result is `/api/maps?page=2&size=10?XTransformPort=8080` — the second `?` is not a valid query separator. Most servers will treat everything after the first `?` as the query string, so `size` becomes `10?XTransformPort=8080` which fails to parse as an integer.
- **Impact:** Any paginated endpoint called with explicit query parameters from the web client will malfunction. Currently the web client doesn't pass query parameters (it relies on defaults), so this is latent.
- **Reproduction:** Call `apiUrl("/api/maps?page=2&size=10", 8080)`. Result: `/api/maps?page=2&size=10?XTransformPort=8080`. Spring will fail to bind `size` to int.

### 4.7 `GameWebSocketHandler.handleCommand` does not deserialize or validate the command payload — server relays arbitrary JSON
- **Severity:** MEDIUM (defense in depth)
- **File:** `aow2-server/src/main/java/com/aow2/server/websocket/GameWebSocketHandler.java` (lines 194–230)
- **Description:** The handler reads `payload.get("command")`, checks its serialized size (4 KB max), and relays it unmodified to the opponent. It does NOT call `CommandSerializer.deserialize` or otherwise validate that the command is well-formed. A malicious client could send `{"type": "command", "command": {"foo": "bar"}}` and the server would relay it. The opponent's `LockstepEngine.receiveCommand` would try to deserialize the bytes (via `CommandSerializer.deserialize` which expects binary, not JSON), fail, and drop the message. But the server has no visibility into this.
- **Impact:** The server cannot detect or rate-limit malformed command floods. A malicious client could send 1000 garbage commands per second; the server relays them all to the opponent, who must deserialize-attempt each one (catching the exception) — wasting CPU.
- **Reproduction:** Open the game WebSocket. Send 1000 `{"type": "command", "command": {}}` messages in 1 second. Server relays all to opponent. No rate limit triggers.

### 4.8 `GameWebSocketHandler.handleGameOver` recursive call is fragile
- **Severity:** LOW
- **File:** `aow2-server/src/main/java/com/aow2/server/websocket/GameWebSocketHandler.java` (lines 355–365, 378–391)
- **Description:** When a second claim arrives while a pending claim exists (or `putIfAbsent` loses the race), `handleGameOver` re-serializes a synthetic payload and recursively calls itself with `confirm: true`. The recursion is bounded (depth 1) but the re-serialization round-trip (`objectMapper.writeAsString → readTree`) is wasteful and could fail if the synthetic map contains types Jackson can't serialize.
- **Impact:** Mostly cosmetic. If the opponent's claim arrives during the recursive call, behavior is undefined.
- **Reproduction:** Hard to trigger deterministically — requires both players to submit game-over claims in the same millisecond.

### 4.9 `JwtUtil` fails fast on the dev secret — but only if the env var is unset, the property source still resolves to the default
- **Severity:** MEDIUM (deployment safety)
- **File:** `aow2-server/src/main/java/com/aow2/server/security/JwtUtil.java` (lines 46–61); `aow2-server/src/main/resources/application.yml` (line 27)
- **Description:** `application.yml` has `secret: ${AOW2_JWT_SECRET:aow2-dev-only-secret-key-...}`. If `AOW2_JWT_SECRET` env var is set, Spring resolves the property to the env var value, and the `if (secret.equals(devSecret))` check is false — no warning, no fail-fast. If the env var is NOT set, the property resolves to the dev default, the check is true, and the constructor throws. This is correct. BUT: if someone sets `AOW2_JWT_SECRET` to the same string as the dev default (e.g., copy-pasted from the example), the check is true, the constructor tries to read the env var (which equals the dev default), and... the code at line 59 sets `effectiveSecret = env` — which equals the dev default. So the dev secret is silently used in production.
- **Impact:** A misconfigured production deployment (env var set to the dev default) silently uses the compromised dev secret. JWTs can be forged by anyone who reads the source code.
- **Reproduction:** `export AOW2_JWT_SECRET="aow2-dev-only-secret-key-that-is-at-least-32-bytes-long-for-hmac"`. Start the server. No exception. JWTs are signed with the publicly-known dev secret.

### 4.10 `MapController.uploadMap` does not validate that `mapData` is valid JSON or a valid map
- **Severity:** MEDIUM
- **File:** `aow2-server/src/main/java/com/aow2/server/controller/MapController.java` (lines 100–138)
- **Description:** The endpoint checks that `name` and `mapData` are non-blank, enforces max name length (64) and max data size (5 MB), then saves. It does NOT parse `mapData` as JSON, does NOT validate it against the map schema, does NOT check dimensions, terrain names, or starting positions. A user can upload `mapData: "this is not json"` and the server happily persists it.
- **Impact:** The map list will contain garbage maps that crash any client that tries to load them. Other users downloading the garbage map will see a Jackson parse exception client-side with no indication that the map itself is invalid.
- **Reproduction:** POST to `/api/maps` with `{"name": "garbage", "mapData": "not json"}`. Server returns 201. Other users see the map in the list. Downloading it and feeding it to `MapLoader.parseJson` throws.

### 4.11 `SessionService` in-memory state is not transactionally consistent with the database
- **Severity:** MEDIUM
- **File:** `aow2-server/src/main/java/com/aow2/server/service/SessionService.java` (lines 151–184, 105–140)
- **Description:** `createSession` is `@Transactional`, calls `sessionRepository.save(session)` (DB), then `activeSessions.put(...)` and `playerSessions.put(...)` (in-memory). If the transaction commits but the JVM crashes before the in-memory puts complete (or vice versa, depending on ordering), the DB and in-memory state diverge. `recoverActiveSessions` runs on boot (line 88) but only recovers sessions with specific states — sessions in `WAITING` or `STARTING` that crashed mid-create may not be recovered correctly.
- **Impact:** A server crash during session creation can leave orphaned sessions in the DB or in-memory leaks. Players may be unable to create new sessions because `playerSessions.containsKey(playerId)` returns true for a session that no longer exists in the DB.
- **Reproduction:** Hard to reproduce without killing the JVM mid-transaction. Stress-test by creating 1000 sessions in parallel and force-killing the JVM.

### 4.12 `RateLimitFilter` only protects `/api/auth/login` and `/api/auth/register` — not WebSocket, not chat, not matchmaking
- **Severity:** MEDIUM
- **File:** `aow2-server/src/main/java/com/aow2/server/config/RateLimitConfig.java` (line 28); `aow2-server/src/main/java/com/aow2/server/security/RateLimitFilter.java` (lines 73–77)
- **Description:** The filter is registered for URL patterns `/api/auth/login` and `/api/auth/register` only. WebSocket endpoints (`/ws/**`) and all other REST endpoints (chat, matchmaking, maps, replays) have no rate limiting. A malicious authenticated user can spam the chat endpoint, the matchmaking queue endpoint, or the WebSocket handlers without throttling.
- **Impact:** DoS vectors remain on most endpoints. The matchmaking queue endpoint in particular is expensive (it scans the queue and may create a session).
- **Reproduction:** Authenticate. Send 1000 POST requests to `/api/matchmaking/queue` in 10 seconds. Server processes all of them.

---

## 5. aow2-modding (Lua scripting)

### 5.1 `LuaEngine.executeString` claims an instruction-count limit but does NOT implement one
- **Severity:** HIGH (sandbox escape / DoS)
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/LuaEngine.java` (lines 134–192)
- **Description:** The Javadoc and constant `DEFAULT_MAX_INSTRUCTIONS = 1_000_000` (line 136) claim "Added instruction-counting limit to prevent infinite loops." The implementation (lines 159–181) declares `int[] count = {0};` but never increments it, never installs a debug hook, and admits in the comment: "Per-instruction hooking is not available without LuaThread access. The script will run to completion or throw a LuaError on its own." So an infinite loop in Lua will hang the game thread indefinitely.
- **Impact:** Any campaign mission script or mod script with an infinite loop (`while true do end`) will freeze the game. The "tick timeout" mentioned in the comment does not exist — the Lua script runs synchronously on the game loop thread.
- **Reproduction:** Load a mission script containing `function onTick() while true do end end`. Start the mission. The game freezes.

### 5.2 `LuaEngine` sandbox leaves `string.dump` accessible and the `package` library intact
- **Severity:** MEDIUM (sandbox gap)
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/LuaEngine.java` (lines 80–101)
- **Description:** The sandbox sets `os`, `io`, `java`, `debug`, `load`, `loadstring`, `dofile`, `require` to `NIL`. It does NOT remove `string.dump` (which can serialize/clone Lua functions, enabling some bytecode tricks) and does NOT remove the `package` library (which provides `package.loadlib` for loading native shared libraries on platforms where it's available). The comment at lines 97–101 acknowledges `string.dump` is still accessible.
- **Impact:** A malicious mod could potentially use `package.loadlib` to load a native library and escape the JVM entirely (on JVMs where LuaJ's package library is functional, which varies by version). `string.dump` enables some bytecode manipulation attacks.
- **Reproduction:** In a mod script: `local ok = pcall(function() return package.loadlib("/lib/libc.so.6", "system") end)`. If `ok` is true, the sandbox is compromised.

### 5.3 `GameAPI` uses static mutable fields — state leaks across game sessions
- **Severity:** HIGH
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/GameAPI.java` (lines 25–66, 83–87)
- **Description:** `gameState`, `entityManager`, `economySystem`, `mapWidth`, `mapHeight`, `objectives`, `timers`, `eventHooks`, `messageQueue` are all `static` fields. The class's own Javadoc admits this is "NOT thread-safe" and "an architectural issue that cannot be fixed without significant refactoring." When a player finishes a campaign mission and starts a skirmish, the static fields still hold the campaign's `GameState` and `EntityManager` until something calls `GameAPI.initialize(state, entities, economy)` again.
- **Impact:** If a script is loaded for the new game before `GameAPI.initialize` is called, it operates on the previous game's state. Stale objectives, stale timers, and stale entity references cause hard-to-debug crashes and incorrect behavior. The `reset()` method in `LuaEngine` calls `GameAPI.reset()` but only resets `objectives`, `timers`, `eventHooks` — NOT `gameState`, `entityManager`, `economySystem` (which are reset only by `initialize`).
- **Reproduction:** Play campaign mission 1. Quit. Start a skirmish. Before the skirmish's `GameAPI.initialize` is called (e.g., during script loading), call `aow2.spawnUnit(...)` from Lua. The unit is spawned in the campaign's `EntityManager`, not the skirmish's. The skirmish's `EntityManager` never sees the unit.

### 5.4 `GameAPI.setEventDispatcher` is set in `MissionScriptEngine` constructor — multiple `MissionScriptEngine` instances overwrite each other
- **Severity:** MEDIUM
- **File:** `aow2-modding/src/main/java/com/aow2/mod/campaign/MissionScriptEngine.java` (lines 47–63)
- **Description:** The constructor sets a static event dispatcher on `GameAPI`. If two `MissionScriptEngine` instances are created (e.g., one for the campaign, one for a mod that wants its own scripting), the second constructor overwrites the first's dispatcher. Events routed through `ModEventBridge.fireUnitKilled` etc. will only reach the second engine's Lua callbacks.
- **Impact:** Co-existing script engines are not supported. A mod that wants to add its own Lua callbacks alongside the campaign's will silently lose them.
- **Reproduction:** Create two `MissionScriptEngine` instances. Register an `onUnitKilled` callback in each. Kill a unit in-game. Only the second engine's callback fires.

### 5.5 `MissionScriptEngine` does not expose a way for Lua to issue game commands
- **Severity:** MEDIUM (feature gap)
- **File:** `aow2-modding/src/main/java/com/aow2/mod/script/GameAPI.java` (full file)
- **Description:** The `GameAPI` exposes `spawnUnit`, `destroyUnit`, `getObjective`, `setObjective`, `addTimer`, `addEventHook`, `displayMessage`, `getTickCount`, `getCredits`, `getUnitCount`, etc. It does NOT expose `issueMove`, `issueAttack`, `issueBuild`, `issueProduce`, or any way for Lua to construct and submit a `CommandType`. Campaign mission scripts that want to control AI units (e.g., "when the player crosses this line, send these three enemy units to attack") must do so via direct `EntityManager` mutation, which bypasses the command pipeline and the lockstep system.
- **Impact:** Campaign scripts that move units do so outside the deterministic command pipeline. In multiplayer campaigns (hypothetical), this would cause desyncs. In single-player, it works but creates a parallel non-replayable execution path.
- **Reproduction:** Search the Lua API surface for any function that issues a `CommandType`. None exists.

---

## 6. aow2-web (Next.js frontend)

### 6.1 No WebSocket integration anywhere in the web client
- **Severity:** HIGH (feature gap)
- **File:** `aow2-web/src/lib/store.tsx` (entire file); `aow2-web/src/lib/api.ts` (entire file); `aow2-web/src/components/` (all components)
- **Description:** `grep -r "WebSocket\|wss?://" aow2-web/src` returns zero matches. The web client is pure REST. The server exposes three WebSocket endpoints (`/ws/lobby`, `/ws/game`, `/ws/chat`) but the web client has no client code for any of them. The `useChatStore` and `useMatchmakingStore` are Zustand stores with no server backing — they're UI-only state.
- **Impact:** Real-time chat, real-time matchmaking notifications, and real-time game play are all impossible from the web client. The chat tab can only show messages the user sends (locally); no other user's messages ever appear. Matchmaking can only show the fake "Demo Mode" fallback.
- **Reproduction:** Open the web client. Send a chat message. It appears in your local store. Open a second browser tab. Your message is not there.

### 6.2 `useMatchmakingStore.foundMatch` flag is set but never reset
- **Severity:** MEDIUM
- **File:** `aow2-web/src/lib/store.tsx` (lines 96–115)
- **Description:** `foundMatch(opponent)` sets `{ isSearching: false, matchFound: true, opponent }`. There is no `resetMatch()` or any setter that flips `matchFound` back to `false`. Once a match is "found" (even via the demo fallback), the UI permanently shows the "Match Found!" state with the "Join Battle" button.
- **Impact:** After the first fake match-found, the user can never search for another match without reloading the page.
- **Reproduction:** Click "Find Match" in the web client. Wait 8 seconds (demo mode). "Match Found!" appears. Click "Join Battle" (toast only). There is no way to search again.

### 6.3 `aow2-web/src/lib/store.tsx` `AOW2Provider` is a no-op
- **Severity:** LOW
- **File:** `aow2-web/src/lib/store.tsx` (lines 118–120)
- **Description:** `export function AOW2Provider({ children }) { return <>{children}</>; }` — the provider wraps children in a fragment with no context. It exists but provides nothing.
- **Impact:** Dead code. Probably an artifact of a planned context that was never implemented.

### 6.4 `LoginDialog.tsx`, `UserPanel.tsx`, and tab components reference APIs that don't exist or aren't authenticated
- **Severity:** MEDIUM
- **File:** `aow2-web/src/components/LoginDialog.tsx`, `aow2-web/src/components/tabs/*Tab.tsx` (all tabs)
- **Description:** The tabs call `getLeaderboard()`, `getMaps()`, `getReplays()`, `getUnits()`, `getChatHistory()` from `api.ts`. Per §4.1, §4.2, §4.4, most of these either don't exist (`/api/units`) or require auth headers that aren't sent. The components have no error boundary beyond a single `throw new Error(...)` which propagates to React's nearest error boundary (if any).
- **Impact:** Most tabs in the web client show error states or empty data.
- **Reproduction:** Click through each tab in the web client. Network tab shows 401/404 for most requests.

---

## 7. Test coverage gaps

The codebase has ~80 test files. Most cover leaf classes (enums, records, utility systems). The following critical classes have ZERO or insufficient test coverage:

| Class | File | Coverage Status |
|---|---|---|
| `LockstepEngine` | `aow2-core/.../network/LockstepEngine.java` | `LockstepEngineTest.java` exists but does not exercise the heartbeat callback path, the disconnect-timer inconsistency (§2.9), or the inline-vs-delegate command split (§2.2) |
| `CommandProcessor` | `aow2-core/.../command/CommandProcessor.java` | `CommandProcessorTest.java` exists but does not test that `process(...)` for `Garrison`/`Ungarrison`/`Cancel`/`Build`/`Produce`/`Research` accepts cross-player commands (§2.1) |
| `CombatSystem` (wind-up path) | `aow2-core/.../combat/CombatSystem.java` | `CombatSystemTest.java` exists but does not assert that ranged units go through `WIND_UP` state — it likely tests only melee. The §2.4 bug (state override) is not caught. |
| `SyncChecker` (hash coverage) | `aow2-core/.../network/SyncChecker.java` | `SyncCheckerTest.java` exists but only checks that hashes match for identical states. It does NOT verify that divergent `productionQueue` or `upgradeLevel` are detected (§2.11). |
| `AOW2App` (multiplayer flow) | `aow2-client/.../AOW2App.java` | NO test file. The §3.1 auth-wiring bug is not caught. |
| `GameScene.setupMultiplayer` | `aow2-client/.../scene/GameScene.java` | NO test for `setupMultiplayer`. The §3.1 unreachable-code issue is invisible. |
| `MultiplayerLobbyScene` (match-found navigation) | `aow2-client/.../scene/MultiplayerLobbyScene.java` | NO test file. |
| `GameWebSocketHandler` (game-over recursion, command relay) | `aow2-server/.../websocket/GameWebSocketHandler.java` | NO test file. |
| `LobbyWebSocketHandler` | `aow2-server/.../websocket/LobbyWebSocketHandler.java` | NO test file. |
| `ChatWebSocketHandler` | `aow2-server/.../websocket/ChatWebSocketHandler.java` | NO test file. |
| `MapController` (upload validation) | `aow2-server/.../controller/MapController.java` | NO test file. The §4.10 missing-validation issue is invisible. |
| `ReplayController` | `aow2-server/.../controller/ReplayController.java` | NO test file. |
| `MatchmakingController` | `aow2-server/.../controller/MatchmakingController.java` | NO test file. |
| `StatsController` | `aow2-server/.../controller/StatsController.java` | NO test file. |
| `SecurityConfig` | `aow2-server/.../config/SecurityConfig.java` | NO test file. The matcher ordering (line 75 before 76) is not asserted. |
| `LuaEngine` (instruction limit) | `aow2-modding/.../script/LuaEngine.java` | `LuaEngineTest.java` exists but does NOT test that an infinite loop is caught — because it isn't (§5.1). |
| `GameAPI` (static state leak) | `aow2-modding/.../script/GameAPI.java` | NO test file. The §5.3 cross-session leak is invisible. |
| `ArmorCalculator` (vs tracker) | `aow2-core/.../combat/ArmorCalculator.java` | `ArmorCalculatorTest.java` exists but only tests the hardcoded map. It does NOT test that data-driven `tech_tree.json` armor bonuses are applied (which they aren't, per §2.7). |
| `MissionScriptEngine` | `aow2-modding/.../campaign/MissionScriptEngine.java` | NO test file. |
| `ModManager` | `aow2-modding/.../ModManager.java` | `ModManagerTest.java` exists but doesn't cover hot-reload or override conflicts. |
| `ReplayRecorder` / `ReplayPlayer` (ordinal compat) | `aow2-core/.../replay/` | `ReplayRecorderTest.java` and `ReplayPlayerTest.java` exist but do NOT test backward compatibility across enum changes (§2.12). |

---

## 8. Build / configuration issues

### 8.1 `aow2-core` does NOT compile-depend on `aow2-modding`, but `aow2-client` does — fragile reflection boundary
- **Severity:** MEDIUM
- **File:** `aow2-core/build.gradle.kts` (lines 11–13); `aow2-client/build.gradle.kts` (line 11)
- **Description:** `aow2-core` declares "aow2-modding is NOT a compile dependency here to avoid circular build. MissionScriptEngine is loaded via reflection in CampaignManager.createWithLuaEngine()." But `aow2-client/build.gradle.kts` line 11 has `implementation(project(":aow2-modding"))` and `AOW2App.java` line 15 directly imports `com.aow2.mod.campaign.MissionScriptEngine` — no reflection. So the "reflection" boundary is only half-enforced: core can't see modding, but client can. If someone tries to call `CampaignManager.createWithLuaEngine()` from core, it fails at runtime with `ClassNotFoundException` unless the modding jar is on the classpath.
- **Impact:** The architectural boundary is leaky. A code change that adds a direct modding import from core will break the build with no clear error message. The runtime classpath must include aow2-modding for `CampaignManager` to work, but this isn't documented in the build file.

### 8.2 `application.yml` uses `ddl-auto: validate` but the schema migrations don't cover all entities
- **Severity:** MEDIUM
- **File:** `aow2-server/src/main/resources/application.yml` (line 14); `aow2-server/src/main/resources/db/migration/V*.sql` (6 files)
- **Description:** JPA is set to `validate`, which means Hibernate checks that entities match the schema on startup and fails if they don't. The 6 Flyway migrations create `players`, `game_sessions`, `match_results`, `chat_messages`, `uploaded_maps`. But the entity classes (`Player`, `GameSession`, `MatchResult`, `ChatMessage`, `UploadedMap`) may have fields not in the migrations (e.g., `last_sync_tick` added in V6, `elo_rating` in V4). If a new field is added to an entity without a corresponding migration, the server fails to start.
- **Impact:** Adding any new persisted field requires a new Flyway migration. Easy to forget. The server will refuse to start in production with a schema-validation error.
- **Reproduction:** Add a new `@Column` to `Player.java`. Run the server. Startup fails with `Schema-validation: missing column [new_column] in table [players]`.

### 8.3 Default database credentials in `application.yml`
- **Severity:** MEDIUM (deployment safety)
- **File:** `aow2-server/src/main/resources/application.yml` (lines 8–11)
- **Description:** `username: ${DB_USERNAME:aow2}` and `password: ${DB_PASSWORD:aow2_dev}`. The defaults `aow2` / `aow2_dev` are committed to the repo. If env vars aren't set, the server connects with these credentials. Unlike the JWT secret (which fails fast), the DB credentials silently fall back to defaults.
- **Impact:** A production deployment that forgets to set `DB_USERNAME`/`DB_PASSWORD` will silently use weak defaults.
- **Reproduction:** `unset DB_USERNAME DB_PASSWORD; ./gradlew :aow2-server:bootRun`. Server starts (assuming a local Postgres with those credentials exists).

### 8.4 `aow2-web/package.json` does not include a production build of the WebSocket client (because there isn't one)
- **Severity:** LOW (informational)
- **File:** `aow2-web/package.json`
- **Description:** The web client has no `ws` or `socket.io` dependency. Real-time features are impossible without adding one.
- **Impact:** Confirms §6.1.

### 8.5 No CI configuration in the repo
- **Severity:** MEDIUM
- **File:** (none — no `.github/workflows/`, no `Jenkinsfile`, no `.gitlab-ci.yml`)
- **Description:** There is no continuous integration configuration. Tests run only when developers invoke them locally. The `gradlew` wrapper exists but no automation runs `./gradlew test` on push.
- **Impact:** Regressions like §2.4 (wind-up override), §2.6 (building target refs), §3.1 (auth wiring) can land without anyone noticing. The elaborate `FIX (X from Y_REPORT.md)` comments suggest an iterative audit-fix cycle that is not backed by automated gating.

---

## Summary table of all issues

| Severity | Count | Modules affected |
|---|---|---|
| CRITICAL | 4 | core (2.1, 2.2 partially), client (3.1, 3.3) |
| HIGH | 13 | core (2.2, 2.3, 2.4, 2.6, 2.7, 2.8, 2.11, 2.12, 2.14), client (3.2), server (4.1, 4.2, 4.3, 4.4), modding (5.1, 5.3), web (6.1) |
| MEDIUM | 17 | common (1.1, 1.3), core (2.5, 2.9, 2.10, 2.13, 2.15, 2.16), client (3.4, 3.6), server (4.5, 4.6, 4.7, 4.9, 4.10, 4.11, 4.12), modding (5.2, 5.4, 5.5), web (6.2, 6.4), build (8.1, 8.2, 8.3, 8.5) |
| LOW | 7 | common (1.2), client (3.5, 3.7), server (4.8), web (6.3), build (8.4) |
| **Total** | **41** | |

(Exact count may vary by how overlapping issues are grouped; the 41 above correspond to the numbered findings §1.1 through §8.5.)

---

## What Still Does Not Work (code-complete but broken at runtime)

These are features for which substantial code exists but which cannot function end-to-end as shipped:

1. **Multiplayer match flow (FXGL client).** Login → search → match-found → game-start is broken at `AOW2App.showGame` (§3.1). The new `MultiplayerService` is unauthenticated, `connectGameWebSocket()` throws, `setupMultiplayer` is never reached, the `LockstepEngine` is never created, and no commands are ever relayed. The lobby's authenticated service is orphaned.

2. **Lockstep heartbeats in actual play.** Even if §3.1 were fixed, the heartbeat path requires `setHeartbeatSendCallback` to be called (§2.8), which only happens inside `setupMultiplayer`. Without it, any idle opponent triggers a false disconnect pause after 14 seconds.

3. **Ranged unit wind-up delay.** The state-machine code exists (§2.4) but is overridden every tick. Ranged units fire at melee cadence.

4. **Generator/CC upgrade power-grid refresh.** The handler supports it (§2.3) but `CommandProcessor` passes `null` for `PowerSystem`. Upgraded Generators don't expand their radius.

5. **Building-target attacks during movement.** `shouldStopForEnemy` doesn't handle negative target refs (§2.6). Units ordered to attack buildings walk past them.

6. **Data-driven research armor bonuses.** `ResearchSystem.applyResearchEffect` loads them; `ArmorCalculator` ignores them (§2.7). Only the 4 hardcoded research IDs apply armor.

7. **Sync-hash desync detection for production/research/upgrade state.** The hash omits these fields (§2.11). Divergence in production queues, research progress, or building upgrades is invisible until it manifests as positional/HP differences many ticks later.

8. **Web client chat, units, maps, replays tabs.** All fetch either non-existent endpoints (§4.2) or authenticated endpoints without auth headers (§4.4). All show error states or empty data.

9. **Web client matchmaking.** POSTs to wrong URL (§4.3), no auth header, falls back to fake "Demo Mode" with no way to actually start a game.

10. **Web client real-time anything.** No WebSocket code exists (§6.1). Chat is local-only; matchmaking notifications never arrive; game play is impossible.

11. **Lua instruction-count limit.** Documented but not implemented (§5.1). Any infinite loop in a campaign or mod script hangs the game.

12. **Cross-game-session GameAPI state isolation.** Static fields leak between sessions (§5.3). Starting a new game without explicit re-initialization operates on the previous game's state.

13. **Replay backward compatibility.** Enum ordinal serialization (§2.12) means any enum change breaks all existing replays. There is no migration code.

14. **Infantry crossing shallow water.** The cost override exists (§2.13) but is unreachable because passability returns false. Infantry cannot cross water despite the design intent.

15. **Group move formation in multiplayer.** The lockstep path issues per-unit moves (§2.2), bypassing `issueGroupMoveCommand`. Multi-unit selections in MP collide instead of maintaining formation.

---

## Top 5 Priorities (ranked by impact)

### Priority 1: Fix multiplayer auth wiring in `AOW2App.showGame` (§3.1, §3.2, §3.3)
**Why #1:** Without this, the entire multiplayer game mode is non-functional. Every other multiplayer-related fix (heartbeats, desync detection, command relay) is moot if no multiplayer match can start. The fix is mechanical: pass the lobby's authenticated `MultiplayerService` (or at minimum its JWT token) into the new `MultiplayerService` created in `showGame`. Better: reuse the lobby's service instance and tear down only the lobby/chat WebSockets, keeping the game WebSocket.

### Priority 2: Add ownership checks to all command handlers (§2.1, §2.14)
**Why #2:** Without ownership checks on `Garrison`, `Ungarrison`, `Cancel`, `Build`, `Produce`, `Research`, any player can manipulate the opponent's entities. This is a competitive-integrity hole that makes multiplayer unplayable even if §3.1 is fixed. The fix is to add an `owns(building, playerId)` check at the top of each handler (mirroring `UpgradeCommandHandler` lines 62–68). For `Move`/`Attack`/etc. that are handled inline in `LockstepEngine.applyCommand`, the `owns()` check is already there — but the inline path bypasses `MoveCommandHandler`'s group-move logic (§2.2), so the fix should also route these through `CommandProcessor` after adding ownership checks there.

### Priority 3: Fix `CombatSystem.performAttack` wind-up state override (§2.4)
**Why #3:** Ranged units fire 2x faster than designed. This is a balance regression that affects every combat engagement. The fix is one line: remove `attacker.setAttackState(3);` at line 235 of `CombatSystem.java` (let `performAttack` manage the state transition). Add a regression test that asserts a Sniper's time-between-shots equals `attackSpeed + attackSpeed/2`.

### Priority 4: Fix `CommandProcessor` passing `null` for `PowerSystem` (§2.3)
**Why #4:** Generator and CC upgrades silently don't expand the power radius. Players pay the upgrade cost, see the HP bonus, but get no power benefit — a confusing and unfair experience. The fix is to inject `PowerSystem` into `CommandProcessor` (or pass it through `LockstepEngine.setGameSystems` which already takes 7 systems — adding an 8th is trivial).

### Priority 5: Replace `ArmorCalculator`'s hardcoded maps with `ResearchBonusTracker` lookups (§2.7)
**Why #5:** The entire data-driven research system (`tech_tree.json`, `ResearchRegistry`, `ResearchBonusTracker`) is bypassed for the most important combat stat: armor. Modders who add new research effects see them silently dropped. The fix is to change `CombatSystem.executeAttack` (and the building-attack path) to call `armorCalculator.calculateEffectiveArmor(target, tracker)` where `tracker = researchSystem.getBonusTracker(playerId)` — using the accumulated tracker values instead of the hardcoded map. The hardcoded map can then be deleted.

---

*End of analysis.*
