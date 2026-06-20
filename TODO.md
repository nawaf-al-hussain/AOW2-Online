# AOW2-Online — Master TODO (Honest Reassessment)

> Full critical re-audit by senior game developer. Previous TODO was inaccurate — it claimed
> "ALL 70 RESOLVED" when significant real issues remained unfixed and several "fixes" were just
> documentation comments, not actual code changes.
>
> Date: 2026-06-20 | Scope: All 5 Java modules + web client
> **Philosophy**: Only mark something resolved if the code actually works. "Documented as UNVERIFIED"
> is NOT a fix — it's acknowledging the problem exists.

---

## 🔴 CRITICAL — Showstoppers (9 issues)

### C-NEW-1: `GridPosition.distanceClass()` silently clamps instead of returning 127 sentinel
- **File**: `aow2-common/.../model/GridPosition.java` lines 51-58
- **Problem**: RE code returns 127 (out-of-range sentinel) when `|dx| > 15 || |dy| > 15`. Current code clamps dx/dy to [-15,15] and returns 15. Units 20+ tiles away are treated as 15 tiles away for ALL range checks, fog of war, and target acquisition.
- **Impact**: Every system using `distanceClass()` produces wrong results at long range.
- **Fix**: Add bounds check — if `|dx| > 15 || |dy| > 15`, return 127 immediately before table lookup.
- **Should fix?** YES — breaks core gameplay at range.

### C-NEW-2: `ResearchEffect` model cannot represent 70%+ of RE research effects
- **File**: `aow2-common/.../model/ResearchEffect.java`
- **Problem**: Model assumes `StatType + int value + Set<UnitType>`. RE tech tree has division ops (range/3), overrides (building armour = 9), unit type upgrades (Rhino → Heavy Assault), multi-stat effects, config value sets (supply cap, credit limit), and production speed overrides. ~30-35 of 48 effects cannot be modeled.
- **Impact**: Research system is architecturally blocked. `applyResearchEffect()` only logs — effects are never actually applied to game state.
- **Fix**: Redesign ResearchEffect to support: division operations, override semantics, unit type transformations, multi-stat per-effect, config value sets. Then implement actual effect application.
- **Should fix?** YES — research is a core game mechanic.

### C-NEW-3: Missing unit types break tech tree research effects
- **File**: `aow2-common/.../model/UnitType.java`
- **Problem**: Tech tree references unit type IDs 4 (Light Assault), 7 (Heavy Assault), 12, 13, 14. None exist in the enum. Research targeting these types cannot be applied.
- **Impact**: Research IDs 6, 7, 9, 11, 24, 27, 31, 32, 33, 35 reference non-existent types.
- **Fix**: Add missing unit types with placeholder stats.
- **Should fix?** YES — upgrade research chain is broken.

### C-NEW-4: `ModEventBridge` callbacks never registered — Lua events never fire
- **File**: `aow2-core/.../mod/ModEventBridge.java` lines 43-54
- **Problem**: `registerUnitKilledCallback()` / `registerBuildingDestroyedCallback()` defined but NEVER called anywhere. Grep confirms zero callers.
- **Impact**: `aow2.onUnitKilled()` and `aow2.onBuildingDestroyed()` in ALL Lua campaign scripts will never trigger.
- **Fix**: Call registration during mod/campaign initialization.
- **Should fix?** YES — campaign scripting depends on this.

### C-NEW-5: Game-over self-confirmation allows ELO fraud
- **File**: `aow2-server/.../websocket/GameWebSocketHandler.java` lines 256-261
- **Problem**: Player can claim game_over then immediately confirm their own claim. Guard clause `!claim.claimedBy.equals(playerId)` evaluates false for the claimant.
- **Fix**: Change to `if (claim.claimedBy.equals(playerId)) { reject; return; }`.
- **Should fix?** YES — security exploit.

### C-NEW-6: Chat WebSocket allows eavesdropping on any match
- **File**: `aow2-server/.../websocket/ChatWebSocketHandler.java` lines 142-169
- **Problem**: Any authenticated player can join any match chat by sending matchId. No participant validation.
- **Fix**: Validate player is a match participant before allowing join.
- **Should fix?** YES — security vulnerability.

### C-NEW-7: JWT default secret committed to source control
- **File**: `aow2-server/.../resources/application.yml` line 27
- **Fix**: Fail fast if AOW2_JWT_SECRET not set or matches known dev value.
- **Should fix?** YES — standard security practice.

### C-NEW-8: `CommandTypeTest` missing `AttackMove` — test suite won't compile
- **File**: `aow2-common/.../model/CommandTypeTest.java` line 352
- **Fix**: Add `case CommandType.AttackMove am -> "AttackMove";` to switch.
- **Should fix?** YES — broken build.

### C-NEW-9: `ChatControllerTest` won't compile — missing Authentication parameter
- **File**: `aow2-server/.../controller/ChatControllerTest.java` line 119
- **Fix**: Add mocked Authentication parameter.
- **Should fix?** YES — broken build.

---

## 🟠 HIGH — Important Issues (16 issues)

| ID | Issue | File | Should Fix? |
|----|-------|------|-------------|
| H-NEW-1 | `game_config.json` battle_time_limits wrong: `[1001,1100,...]` should be `[1000,1000,...]` | `game_config.json:9` | YES — contradicts RE |
| H-NEW-2 | `ResearchNode.hasPrerequisite()` returns true for `-1` (no prereq) | `ResearchNode.java:78-80` | YES — blocks starter research |
| H-NEW-3 | `ResearchSystem.applyResearchEffect()` only logs — no state mutation | `ResearchSystem.java:337-663` | YES — research does nothing |
| H-NEW-4 | `CONFED_BUNKER` and `CONFED_TECH_CENTRE` have near-identical stats (likely RE error) | `StatsRegistry.java:347-365` | YES — bunker with queue slots |
| H-NEW-5 | All rebel building stats copied from Confederation — unverified | `StatsRegistry.java:390-475` | VERIFY from RE binary |
| H-NEW-6 | No max password length — bcrypt DoS | `AuthService.java:58` | YES — security fix |
| H-NEW-7 | `ChatMessage.playerId` is int, Player.id is Long — truncation | `ChatMessage.java:29`, V3 SQL | YES — data integrity |
| H-NEW-8 | Race condition in game-over claim handling | `GameWebSocketHandler.java:248-296` | YES — use compute() |
| H-NEW-9 | `GameSession` @Entity never persisted — in-memory only | `GameSession.java`, `SessionService.java` | LOW — alpha acceptable |
| H-NEW-10 | Pending game-over claims never cleaned — memory leak | `GameWebSocketHandler.java:39` | YES — cleanup on disconnect |
| H-NEW-11 | Build command silently dropped in GameScene | `GameScene.java:283-304` | YES — fundamental RTS |
| H-NEW-12 | GameScene always creates test map — no real map loading | `GameScene.java:343` | YES — can't play real maps |
| H-NEW-13 | CampaignManager never injected into CampaignScene | `CampaignScene.java:170-195` | YES — campaign non-functional |
| H-NEW-14 | Zero audio files exist — entire audio system silent | `audio/README.txt` | DEFERRED — needs assets |
| H-NEW-15 | `EloRatingServiceTest` stale K-factor assertion (expects 988, actual 992) | `EloRatingServiceTest.java:122` | YES — test will fail |
| H-NEW-16 | LuaJ sandbox potentially bypassable (JsePlatform) | `LuaEngine.java:82-91` | MAYBE — untrusted mods only |

---

## 🟡 MEDIUM — Non-Blocking but Important (32 issues)

### Cross-Module / Common

| ID | Issue | File |
|----|-------|------|
| M-NEW-1 | `StatsRegistry` not resettable for testing (static final) | `StatsRegistry.java:27` |
| M-NEW-2 | `BuildingStats` record exposes mutable List via `upgradeCosts()` | `BuildingStats.java:37` |
| M-NEW-3 | `GameConfig` creates new `ObjectMapper` per call | `GameConfig.java:212` |
| M-NEW-4 | Duplicate constant arrays in `GameConstants` vs `GameConfig` | `GameConstants.java:76,88-90` |
| M-NEW-5 | `TERRAIN_MOVEMENT_COSTS` indexed by ordinal — fragile | `GameConstants.java:120` |
| M-NEW-6 | `BuildingType.fromFactionRelativeId()` doesn't handle NEUTRAL | `BuildingType.java:70` |
| M-NEW-7 | `CONFED_INFANTRY_CENTRE` has `powerProduce=2` but `producesPower()`=false | `StatsRegistry.java:329` |

### aow2-core (Determinism & Integration)

| ID | Issue | File |
|----|-------|------|
| M-NEW-8 | `ResearchRegistry` uses HashMap for researchEffects | `ResearchRegistry.java:79` |
| M-NEW-9 | `ResearchSystem.completedResearch` uses HashSet (non-deterministic iteration in SyncChecker) | `ResearchSystem.java:100` |
| M-NEW-10 | `FogOfWarSystem` uses HashMap for visibility grids | `FogOfWarSystem.java:58` |
| M-NEW-11 | Attack-move command doesn't set `autoEngage` flag | `CommandProcessor.java:194` |
| M-NEW-12 | Patrol command only issues one-way move (no return) | `CommandProcessor.java:172` |
| M-NEW-13 | `GameAPI` event hooks NEVER fired (same root as C-NEW-4) | `GameAPI.java:234` |
| M-NEW-14 | `ModInstaller.detectCommonPrefix()` exhausts ZipInputStream | `ModInstaller.java:406` |
| M-NEW-15 | `GameDataRegistry.applyUnitOverrides()` must be manually updated per field | `GameDataRegistry.java:237` |

### aow2-server

| ID | Issue | File |
|----|-------|------|
| M-NEW-16 | `X-Forwarded-For` trust bypasses rate limiting | `RateLimitFilter.java:111` |
| M-NEW-17 | No WebSocket message size limits | All WS handlers |
| M-NEW-18 | `chat_messages.player_id` INT, no FK to players | `V3 SQL:7` |
| M-NEW-19 | No pagination on map list endpoint | `MapController.java:62` |
| M-NEW-20 | No global `@ControllerAdvice` exception handler | All controllers |
| M-NEW-21 | `readyPlayers` in lobby never cleaned on timeout | `LobbyWebSocketHandler.java:42` |
| M-NEW-22 | `SessionService.registerWebSocketSession` overwrites without warning | `SessionService.java:275` |

### aow2-client

| ID | Issue | File |
|----|-------|------|
| M-NEW-23 | Production queue is display-only — no UI to select units | `HUD.java` |
| M-NEW-24 | AccessibilitySettings key bindings have zero effect on InputHandler | `AccessibilitySettings.java` |

### aow2-web

| ID | Issue | File |
|----|-------|------|
| M-NEW-25 | API route.ts is stub ("Hello, world!") | `api/route.ts` |
| M-NEW-26 | ALL data tabs use hardcoded demo data | All tab components |
| M-NEW-27 | Matchmaking is fake (8-second timer) | `MatchmakingPanel.tsx:35` |
| M-NEW-28 | Prisma schema missing foreign keys | `schema.prisma:53-75` |

---

## 🟢 LOW — Polish / Minor (22 issues)

| ID | Issue | File |
|----|-------|------|
| L-NEW-1 | `MathUtils.clamp()` redundant with Java 21 | `MathUtils.java:46` |
| L-NEW-2 | `ResearchNode.getPrerequisites()` redundant with record accessor | `ResearchNode.java:88` |
| L-NEW-3 | `ResearchEffect` manual equals/hashCode redundant with record | `ResearchEffect.java:72` |
| L-NEW-4 | Unused import `Collections` in ResearchNode | `ResearchNode.java:3` |
| L-NEW-5 | No `units.json`/`buildings.json` in common resources | `aow2-common/resources/data/` |
| L-NEW-6 | `CommandTypeTest` doesn't test AttackMove instantiation | `CommandTypeTest.java` |
| L-NEW-7 | `buildings.json` in client is deprecated stub — delete | `aow2-client/resources/data/buildings.json` |
| L-NEW-8 | `units.json` in client never loaded at runtime | `aow2-client/resources/data/units.json` |
| L-NEW-9 | Faction hardcoded to CONFEDERATION in GameScene | `GameScene.java:77` |
| L-NEW-10 | Settings scene is stub Alert | `AOW2App.java` |
| L-NEW-11 | AccessibilitySettings not persisted | `AccessibilitySettings.java:31` |
| L-NEW-12 | `TutorialSystem.stepQueue` unused — dead code | `TutorialSystem.java:86` |
| L-NEW-13 | `MusicPlayer.shuffle` uses Math.random() | `MusicPlayer.java:322` |
| L-NEW-14 | `LobbyWebSocketHandler.map_veto` is a no-op | `LobbyWebSocketHandler.java:216` |
| L-NEW-15 | Deprecated `EloRatingService.java` still in codebase | `EloRatingService.java` |
| L-NEW-16 | V4 Flyway migration redundant with V1 | `V4 SQL` |
| L-NEW-17 | No control groups (Ctrl+1-9) | `InputHandler.java` |
| L-NEW-18 | EntityPlacer erases via takeDamage(hp+1) hack | `EntityPlacer.java:192` |
| L-NEW-19 | Many unused npm dependencies inflate web bundle | `package.json` |
| L-NEW-20 | `GameConfig.Builder` silently converts null arrays to empty | `GameConfig.java:257` |
| L-NEW-21 | `GameConfig.toString()` omits footprint arrays | `GameConfig.java:315` |
| L-NEW-22 | `ChatMessageRecord.timestamp` uses epoch millis (not game ticks) | `ChatMessageRecord.java:17` |

---

## 📋 UNVERIFIED ASSUMPTIONS (20 assumptions — NOT fixed, just documented)

| Constant | Value | Source | Impact |
|----------|-------|--------|--------|
| `SIEGE_RANGE_BONUS` | 3 | Assumed | Siege balance |
| `ARTILLERY_FIXED_FLIGHT_TIME` | 15 | Assumed | Artillery timing |
| `CC_PLACEMENT_RADIUS` | 20 | Assumed | Build placement |
| `ARM_DELAY_TICKS` | 10 | Assumed | Mine timing |
| `CANCEL_REFUND_PERCENT` | 0.50 | Assumed | Economy |
| `BUILDING_ATTACK_COOLDOWN` | 5 | Assumed | Defensive DPS |
| `INFANTRY_BASE_RECOVERY` | 1 | Assumed | Infantry sustain |
| `MACHINERY_BASE_REPAIR` | 2 | Assumed | Vehicle sustain |
| `RESISTANCE_INCOME_MULTIPLIER` | 1.15 | Assumed | Faction balance |
| `CC_UPGRADE_INCOME_BONUS` | 2/level | Assumed | Economy progression |
| Rebel building stats (6) | Copied from Confed | No RE data | Faction asymmetry |
| `REBEL_WALL` stats (all) | Guessed | No RE data | Wall effectiveness |
| Nuclear distance divisor | 12 | Reconstructed | Nuclear damage |
| Ranged wind-up phase | attackSpeed/2 | Assumed | All ranged DPS |
| Infantry vs building mult | 0.5 | Assumed | Anti-building |
| Infantry vs machinery mult | 0.7 | Assumed | Anti-vehicle |
| `battle_time_limits` | [1001,1100,...] | WRONG | Match duration |
| Bunker/TechCentre stats | Near-identical | Possible RE error | Bunker behavior |
| CONFED_INFANTRY_CENTRE power | powerProduce=2 | From RE data? | Power economy |
| Research IDs 2-3 targets | "Assault" unit | Ambiguous | Research effect |

---

## 📊 HONEST SUMMARY

| Severity | Count | Actually Fixed | Status |
|----------|-------|----------------|--------|
| 🔴 CRITICAL | 9 | 0 | ❌ ALL OPEN |
| 🟠 HIGH | 16 | 0 | ❌ ALL OPEN |
| 🟡 MEDIUM | 32 | ~5 | ⚠️ MOSTLY OPEN |
| 🟢 LOW | 22 | ~8 | ⚠️ PARTIALLY OPEN |
| **Total NEW** | **79** | **~13** | **❌ 66 OPEN** |

### What Works Well
- Combat system (damage formula, armor, projectiles, splash, siege, mines)
- Economy system (credit generation, diminishing returns, production queues)
- AI system (DeterministicLCG, difficulty levels, phased decisions)
- Lockstep networking (CommandBuffer, CommandSerializer, SyncChecker)
- Client rendering (isometric, entities, fog, minimap, camera)
- Map editor (terrain/entity placement, validation, sharing)
- Replay system (recording, playback, seeking)
- Modding architecture (mod loading, data overrides, Lua scripting)
- Test coverage (~1,463 methods across 76 files)

### What Does NOT Work
1. **Research effects are fake** — completing research only logs, doesn't change gameplay
2. **Build placement broken end-to-end** — command silently dropped in GameScene
3. **Campaign non-functional** — manager never injected, save/load does nothing
4. **No real map loading** — always plays on hardcoded test map
5. **Web dashboard is demo shell** — all data hardcoded, no backend integration
6. **Mod events never fire** — Lua campaign scripts can't react to kills/destructions
7. **Server security vulnerabilities** — ELO fraud, chat eavesdropping, JWT default
8. **Test suite doesn't compile** — missing AttackMove case blocks execution
9. **Audio produces zero sound** — system built but no audio files
10. **RE data mismatches** — battle_time_limits wrong, rebel stats guessed, missing unit types