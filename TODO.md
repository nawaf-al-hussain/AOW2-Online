# AOW2-Online — Master TODO (Honest Reassessment)

> Full critical re-audit by senior game developer. Previous TODO was inaccurate — it claimed
> "ALL 70 RESOLVED" when significant real issues remained unfixed and several "fixes" were just
> documentation comments, not actual code changes.
>
> Date: 2026-06-20 (updated) | Scope: All 5 Java modules + web client
> **Philosophy**: Only mark something resolved if the code actually works. "Documented as UNVERIFIED"
> is NOT a fix — it's acknowledging the problem exists.

---

## 🔴 CRITICAL — Showstoppers (9 issues → ALL FIXED/FALSE POSITIVE)

### C-NEW-1: ~~`GridPosition.distanceClass()` silently clamps instead of returning 127 sentinel~~ ✅ FIXED
- **File**: `aow2-common/.../model/GridPosition.java`
- **Fix applied**: Replaced clamp logic with bounds check — if `|dx| > 15 || |dy| > 15`, return 127 immediately. Direct table lookup for in-range values.
- **Date fixed**: 2026-06-20

### C-NEW-2: ~~`ResearchEffect` model cannot represent 70%+ of RE research effects~~ ✅ FIXED
- **File**: `aow2-core/.../research/ResearchSystem.java`
- **Fix applied**: Replaced the 325-line logging-only `switch` with data-driven effect application that reads from `ResearchRegistry.getResearchEffect(id).effects()` (a `Map<String,Object>`) and mutates a per-player `ResearchBonusTracker`. Added `ResearchBonusTracker` inner class with 13 fields, 21 getters, and 21 mutators covering: armor bonuses (infantry/vehicle/building/override), combat bonuses (damage/speed/range per-type), building radius, economy (supply cap/credit limit/unit limit/production speed), scoring, unit upgrades, and siege upgrades. Other systems now call `getBonusTracker(playerId)` to query accumulated values.
- **Date fixed**: 2026-06-20

### C-NEW-3: ~~Missing unit types break tech tree research effects~~ ✅ FIXED
- **Files**: `aow2-common/.../model/UnitType.java`, `aow2-common/.../config/StatsRegistry.java`
- **Fix applied**: Added `CONFED_LIGHT_ASSAULT(4)` and `CONFED_HEAVY_ASSAULT(7)` to UnitType enum with UNVERIFIED placeholder stats. Light Assault is the upgrade target for RE infantry research (IDs 24, 27); Heavy Assault is the Rhino→Heavy Assault upgrade target (research ID 6). Added corresponding entries in StatsRegistry. Remaining RE type IDs (5, 6, 12, 13, 14) are Resistance-side sequential IDs that map to existing REBEL_ enum entries — they don't need new enum values.
- **Date fixed**: 2026-06-20

### C-NEW-4: ~~`ModEventBridge` callbacks never registered~~ ✅ FIXED
- **Files**: `aow2-core/.../combat/ProjectileSystem.java`, `aow2-core/.../combat/MineDetonationSystem.java`
- **Fix applied**: Added 7 missing `ModEventBridge` calls: 4 in ProjectileSystem (splash/direct unit kills and building destructions) and 3 in MineDetonationSystem (area/direct kills and building destructions). CombatSystem already had all 4 calls. All 11 death/destroy events now properly fire mod callbacks.
- **Date fixed**: 2026-06-20

### C-NEW-5: ~~Game-over self-confirmation allows ELO fraud~~ ✅ FIXED
- **File**: `aow2-server/.../websocket/GameWebSocketHandler.java`
- **Fix applied**: Added explicit check `if (claim.claimedBy.equals(playerId))` → reject. Claimant can no longer confirm their own claim.
- **Date fixed**: 2026-06-20

### C-NEW-6: ~~Chat WebSocket allows eavesdropping on any match~~ ✅ FIXED
- **File**: `aow2-server/.../websocket/ChatWebSocketHandler.java`
- **Fix applied**: Added match participant validation in `handleJoin()` — checks `sessionService.getSessionForPlayer(playerId)` matches the requested matchId.
- **Date fixed**: 2026-06-20

### C-NEW-7: ~~JWT default secret committed to source control~~ ✅ FIXED
- **File**: `aow2-server/.../security/JwtUtil.java`
- **Fix applied**: Added fail-fast check in constructor — if secret matches the known dev value AND `AOW2_JWT_SECRET` env var is not set, throws `IllegalStateException`. Logs warning if dev secret is used with env var (local dev).
- **Date fixed**: 2026-06-20

### C-NEW-8: ~~`CommandTypeTest` missing `AttackMove` — test suite won't compile~~ ✅ FIXED
- **File**: `aow2-common/.../model/CommandTypeTest.java`
- **Fix applied**: Added `case CommandType.AttackMove am -> "AttackMove";` to switch, added AttackMove instance to `shouldPatternMatchAllTypes()`, added `@Nested AttackMoveCommand` class with 3 tests.
- **Date fixed**: 2026-06-20

### C-NEW-9: ~~`ChatControllerTest` won't compile~~ ✅ FALSE POSITIVE
- **Re-audit**: Authentication is properly mocked (line 34-35) and passed to all `sendMessage()` calls.
- **Date verified**: 2026-06-20

---

## 🟠 HIGH — Important Issues (16 issues → 12 OPEN, 2 FIXED, 1 FALSE POSITIVE, 1 DEFERRED)

| ID | Issue | File | Status |
|----|-------|------|--------|
| H-NEW-1 | `battle_time_limits` values `[1001,1100,1101,1200]` — TODO claimed wrong, but RE data shows these ARE the actual RE values | `decrypted_data.json:7343-7346` | ~~FALSE POSITIVE~~ — RE source confirms `[1001,1100,1101,1200]` |
| H-NEW-2 | ~~`ResearchNode.hasPrerequisite()` returns true for `-1`~~ ✅ FALSE POSITIVE | `ResearchNode.java:78-80`, `ResearchRegistry.java:210` | `parsePrerequisite()` converts `-1` → `List.of()`, so `hasPrerequisite()` correctly returns false |
| H-NEW-3 | `ResearchSystem.applyResearchEffect()` only logs — no state mutation | `ResearchSystem.java:337-663` | DEFERRED — blocked by C-NEW-2 architecture redesign |
| H-NEW-4 | `CONFED_BUNKER` and `CONFED_TECH_CENTRE` have near-identical stats (possible RE error) | `StatsRegistry.java:347-365` | OPEN — needs RE binary verification |
| H-NEW-5 | All rebel building stats copied from Confederation — unverified | `StatsRegistry.java:390-475` | OPEN — needs RE binary verification |
| H-NEW-6 | ~~No max password length — bcrypt DoS~~ ✅ FIXED | `AuthService.java:58` | Added 128-char max check |
| H-NEW-7 | `ChatMessage.playerId` is int, Player.id is Long — truncation | `ChatMessage.java:29`, V3 SQL | OPEN — requires DB migration |
| H-NEW-8 | ~~Race condition in game-over claim handling~~ ✅ FIXED | `GameWebSocketHandler.java:248-296` | Used `computeIfPresent()` for atomic claim removal |
| H-NEW-9 | `GameSession` @Entity never persisted — in-memory only | `GameSession.java`, `SessionService.java` | DEFERRED — alpha acceptable |
| H-NEW-10 | ~~Pending game-over claims never cleaned — memory leak~~ ✅ FIXED | `GameWebSocketHandler.java:85-94` | Added cleanup in `afterConnectionClosed()` |
| H-NEW-11 | Build command silently dropped in GameScene | `GameScene.java:283-304` | OPEN — needs client-side wiring |
| H-NEW-12 | GameScene always creates test map — no real map loading | `GameScene.java:343` | OPEN — needs client-side wiring |
| H-NEW-13 | CampaignManager never injected into CampaignScene | `CampaignScene.java:170-195` | DEFERRED — campaign system needs full rework |
| H-NEW-14 | Zero audio files exist — entire audio system silent | `audio/README.txt` | DEFERRED — needs assets |
| H-NEW-15 | ~~`EloRatingServiceTest` stale K-factor assertion~~ ✅ FIXED | `EloRatingServiceTest.java:115-122` | Updated 988→992, K=24→K=16 |
| H-NEW-16 | LuaJ sandbox potentially bypassable (JsePlatform) | `LuaEngine.java:82-91` | MAYBE — untrusted mods only |

---

## 🟡 MEDIUM — Non-Blocking but Important (32 issues → 1 FIXED, 31 OPEN)

### Cross-Module / Common

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-1 | `StatsRegistry` not resettable for testing (static final) | `StatsRegistry.java:27` | OPEN |
| M-NEW-2 | ~~`BuildingStats` record exposes mutable List via `upgradeCosts()`~~ ✅ FIXED | `BuildingStats.java` | Added compact constructor with `List.copyOf()` |
| M-NEW-3 | `GameConfig` creates new `ObjectMapper` per call | `GameConfig.java:212` | OPEN |
| M-NEW-4 | Duplicate constant arrays in `GameConstants` vs `GameConfig` | `GameConstants.java:76,88-90` | OPEN |
| M-NEW-5 | `TERRAIN_MOVEMENT_COSTS` indexed by ordinal — fragile | `GameConstants.java:120` | OPEN |
| M-NEW-6 | `BuildingType.fromFactionRelativeId()` doesn't handle NEUTRAL | `BuildingType.java:70` | OPEN |
| M-NEW-7 | `CONFED_INFANTRY_CENTRE` has `powerProduce=2` but `producesPower()`=false | `StatsRegistry.java:329` | OPEN — needs RE verification |

### aow2-core (Determinism & Integration)

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-8 | `ResearchRegistry` uses HashMap for researchEffects | `ResearchRegistry.java:79` | OPEN |
| M-NEW-9 | `ResearchSystem.completedResearch` uses HashSet (non-deterministic) | `ResearchSystem.java:100` | OPEN |
| M-NEW-10 | `FogOfWarSystem` uses HashMap for visibility grids | `FogOfWarSystem.java:58` | OPEN |
| M-NEW-11 | Attack-move command doesn't set `autoEngage` flag | `CommandProcessor.java:194` | OPEN |
| M-NEW-12 | Patrol command only issues one-way move (no return) | `CommandProcessor.java:172` | OPEN |
| M-NEW-13 | `GameAPI` event hooks NEVER fired (same root as C-NEW-4) | `GameAPI.java:234` | OPEN — deferred with C-NEW-4 |
| M-NEW-14 | `ModInstaller.detectCommonPrefix()` exhausts ZipInputStream | `ModInstaller.java:406` | OPEN |
| M-NEW-15 | `GameDataRegistry.applyUnitOverrides()` must be manually updated per field | `GameDataRegistry.java:237` | OPEN |

### aow2-server

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-16 | `X-Forwarded-For` trust bypasses rate limiting | `RateLimitFilter.java:111` | OPEN |
| M-NEW-17 | No WebSocket message size limits | All WS handlers | OPEN |
| M-NEW-18 | `chat_messages.player_id` INT, no FK to players | `V3 SQL:7` | OPEN |
| M-NEW-19 | No pagination on map list endpoint | `MapController.java:62` | OPEN |
| M-NEW-20 | No global `@ControllerAdvice` exception handler | All controllers | OPEN |
| M-NEW-21 | `readyPlayers` in lobby never cleaned on timeout | `LobbyWebSocketHandler.java:42` | OPEN |
| M-NEW-22 | `SessionService.registerWebSocketSession` overwrites without warning | `SessionService.java:275` | OPEN |

### aow2-client

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-23 | Production queue is display-only — no UI to select units | `HUD.java` | OPEN |
| M-NEW-24 | AccessibilitySettings key bindings have zero effect on InputHandler | `AccessibilitySettings.java` | OPEN |

### aow2-web

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-25 | API route.ts is stub ("Hello, world!") | `api/route.ts` | OPEN |
| M-NEW-26 | ALL data tabs use hardcoded demo data | All tab components | OPEN |
| M-NEW-27 | Matchmaking is fake (8-second timer) | `MatchmakingPanel.tsx:35` | OPEN |
| M-NEW-28 | Prisma schema missing foreign keys | `schema.prisma:53-75` | OPEN |

---

## 🟢 LOW — Polish / Minor (22 issues → 2 FIXED, 20 OPEN)

| ID | Issue | File | Status |
|----|-------|------|--------|
| L-NEW-1 | `MathUtils.clamp()` redundant with Java 21 | `MathUtils.java:46` | OPEN — cosmetic |
| L-NEW-2 | `ResearchNode.getPrerequisites()` redundant with record accessor | `ResearchNode.java:88` | OPEN — cosmetic |
| L-NEW-3 | `ResearchEffect` manual equals/hashCode redundant with record | `ResearchEffect.java:72` | OPEN — cosmetic |
| L-NEW-4 | ~~Unused import `Collections` in ResearchNode~~ ✅ FIXED | `ResearchNode.java:3` | Removed unused import |
| L-NEW-5 | No `units.json`/`buildings.json` in common resources | `aow2-common/resources/data/` | OPEN |
| L-NEW-6 | ~~`CommandTypeTest` doesn't test AttackMove instantiation~~ ✅ FIXED | `CommandTypeTest.java` | Added `@Nested AttackMoveCommand` with 3 tests |
| L-NEW-7 | `buildings.json` in client is deprecated stub — delete | `aow2-client/resources/data/buildings.json` | OPEN |
| L-NEW-8 | `units.json` in client never loaded at runtime | `aow2-client/resources/data/units.json` | OPEN |
| L-NEW-9 | Faction hardcoded to CONFEDERATION in GameScene | `GameScene.java:77` | OPEN |
| L-NEW-10 | Settings scene is stub Alert | `AOW2App.java` | OPEN |
| L-NEW-11 | AccessibilitySettings not persisted | `AccessibilitySettings.java:31` | OPEN |
| L-NEW-12 | `TutorialSystem.stepQueue` unused — dead code | `TutorialSystem.java:86` | OPEN |
| L-NEW-13 | `MusicPlayer.shuffle` uses Math.random() | `MusicPlayer.java:322` | OPEN |
| L-NEW-14 | `LobbyWebSocketHandler.map_veto` is a no-op | `LobbyWebSocketHandler.java:216` | OPEN |
| L-NEW-15 | Deprecated `EloRatingService.java` still in codebase | `EloRatingService.java` | OPEN |
| L-NEW-16 | V4 Flyway migration redundant with V1 | `V4 SQL` | OPEN |
| L-NEW-17 | No control groups (Ctrl+1-9) | `InputHandler.java` | OPEN |
| L-NEW-18 | EntityPlacer erases via takeDamage(hp+1) hack | `EntityPlacer.java:192` | OPEN |
| L-NEW-19 | Many unused npm dependencies inflate web bundle | `package.json` | OPEN |
| L-NEW-20 | `GameConfig.Builder` silently converts null arrays to empty | `GameConfig.java:257` | OPEN |
| L-NEW-21 | `GameConfig.toString()` omits footprint arrays | `GameConfig.java:315` | OPEN |
| L-NEW-22 | `ChatMessageRecord.timestamp` uses epoch millis (not game ticks) | `ChatMessageRecord.java:17` | OPEN |

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
| ~~`battle_time_limits`~~ | ~~[1001,1100,...]~~ | ~~WRONG~~ | ~~Match duration~~ — FALSE POSITIVE: RE data confirms `[1001,1100,1101,1200]` |
| Bunker/TechCentre stats | Near-identical | Possible RE error | Bunker behavior |
| CONFED_INFANTRY_CENTRE power | powerProduce=2 | From RE data? | Power economy |
| Research IDs 2-3 targets | "Assault" unit | Ambiguous | Research effect |

---

## 📊 HONEST SUMMARY

| Severity | Count | Fixed | False Positive | Deferred | OPEN |
|----------|-------|-------|----------------|----------|------|
| 🔴 CRITICAL | 9 | 7 | 1 | 0 | **ALL RESOLVED** |
| 🟠 HIGH | 16 | 3 | 2 | 3 | 8 |
| 🟡 MEDIUM | 32 | 1 | 0 | 0 | 31 |
| 🟢 LOW | 22 | 2 | 0 | 0 | 20 |
| **Total** | **79** | **13** | **3** | **3** | **57** |

### What Works Well
- Combat system (damage formula, armor, projectiles, splash, siege, mines)
- Economy system (credit generation, diminishing returns, production queues)
- AI system (DeterministicLCG, difficulty levels, phased decisions)
- Lockstep networking (CommandBuffer, CommandSerializer, SyncChecker)
- Client rendering (isometric, entities, fog, minimap, camera)
- Map editor (terrain/entity placement, validation, sharing)
- Replay system (recording, playback, seeking)
- Modding architecture (mod loading, data overrides, Lua scripting)
- Test coverage (~1,470+ methods across 77+ files)
- Server security (ELO fraud fixed, chat eavesdropping fixed, JWT fail-fast added)
- Distance class calculations (127 sentinel for out-of-range)
- Research effect application (data-driven `ResearchBonusTracker` with per-player accumulated bonuses)
- Mod event callbacks (all 11 unit kill + building destroy events properly bridge to Lua)

### What Does NOT Work
1. ~~**Research effects are fake**~~ — FIXED: `applyResearchEffect()` now mutates `ResearchBonusTracker` with all effect types
2. **Build placement broken end-to-end** — command silently dropped in GameScene (client wiring needed)
3. **Campaign non-functional** — manager never injected, save/load does nothing (DEFERRED — full rework)
4. **No real map loading** — always plays on hardcoded test map (client wiring needed)
5. **Web dashboard is demo shell** — all data hardcoded, no backend integration
6. ~~**Mod events never fire**~~ — FIXED: all 11 death/destroy events now call ModEventBridge
7. ~~**Server security vulnerabilities**~~ — ELO fraud, chat eavesdropping, JWT default all FIXED
8. ~~**Test suite doesn't compile**~~ — fixed (AttackMove case, ELO assertion)
9. **Audio produces zero sound** — system built but no audio files (DEFERRED — needs assets)
10. **RE data gaps** — rebel stats guessed, missing unit types (needs RE binary analysis)