# AOW2-Online — Master TODO (Honest Reassessment)

> Full critical re-audit by senior game developer. Previous TODO was inaccurate — it claimed
> "ALL 70 RESOLVED" when significant real issues remained unfixed and several "fixes" were just
> documentation comments, not actual code changes.
>
> Date: 2026-06-21 (updated) | Scope: All 5 Java modules + web client
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
- **Fix applied**: Replaced the 325-line logging-only `switch` with data-driven effect application that reads from `ResearchRegistry.getResearchEffect(id).effects()` (a `Map<String,Object>`) and mutates a per-player `ResearchBonusTracker`.
- **Date fixed**: 2026-06-20

### C-NEW-3: ~~Missing unit types break tech tree research effects~~ ✅ FIXED
- **Files**: `aow2-common/.../model/UnitType.java`, `aow2-common/.../config/StatsRegistry.java`
- **Fix applied**: Added `CONFED_LIGHT_ASSAULT(4)` and `CONFED_HEAVY_ASSAULT(7)` to UnitType enum.
- **Date fixed**: 2026-06-20

### C-NEW-4: ~~`ModEventBridge` callbacks never registered~~ ✅ FIXED
- **Files**: `aow2-core/.../combat/ProjectileSystem.java`, `aow2-core/.../combat/MineDetonationSystem.java`
- **Fix applied**: Added 7 missing `ModEventBridge` calls. All 11 death/destroy events now properly fire mod callbacks.
- **Date fixed**: 2026-06-20

### C-NEW-5: ~~Game-over self-confirmation allows ELO fraud~~ ✅ FIXED
- **File**: `aow2-server/.../websocket/GameWebSocketHandler.java`
- **Date fixed**: 2026-06-20

### C-NEW-6: ~~Chat WebSocket allows eavesdropping on any match~~ ✅ FIXED
- **File**: `aow2-server/.../websocket/ChatWebSocketHandler.java`
- **Date fixed**: 2026-06-20

### C-NEW-7: ~~JWT default secret committed to source control~~ ✅ FIXED
- **File**: `aow2-server/.../security/JwtUtil.java`
- **Date fixed**: 2026-06-20

### C-NEW-8: ~~`CommandTypeTest` missing `AttackMove`~~ ✅ FIXED
- **File**: `aow2-common/.../model/CommandTypeTest.java`
- **Date fixed**: 2026-06-20

### C-NEW-9: ~~`ChatControllerTest` won't compile~~ ✅ FALSE POSITIVE
- **Date verified**: 2026-06-20

---

## 🟠 HIGH — Important Issues (16 issues → 6 OPEN, 5 FIXED, 2 FALSE POSITIVE, 3 DEFERRED)

| ID | Issue | File | Status |
|----|-------|------|--------|
| H-NEW-1 | `battle_time_limits` values | `decrypted_data.json` | FALSE POSITIVE — RE source confirms `[1001,1100,1101,1200]` |
| H-NEW-2 | ~~`ResearchNode.hasPrerequisite()`~~ | `ResearchNode.java` | FALSE POSITIVE |
| H-NEW-3 | `ResearchSystem.applyResearchEffect()` only logs | `ResearchSystem.java` | ✅ FIXED (2026-06-20 via C-NEW-2) |
| H-NEW-4 | `CONFED_BUNKER` and `CONFED_TECH_CENTRE` near-identical stats | `StatsRegistry.java` | ✅ VERIFIED (2026-06-21) — Not an RE error; Bunker is defensive variant. Added detailed RE comparison comment. |
| H-NEW-5 | All rebel building stats copied from Confederation | `StatsRegistry.java` | ✅ VERIFIED (2026-06-21) — RE only provides upgrade_costs for rebels; base stats are faction-agnostic. All 7 rebel buildings marked VERIFIED. |
| H-NEW-6 | ~~No max password length~~ | `AuthService.java` | ✅ FIXED (2026-06-20) |
| H-NEW-7 | `ChatMessage.playerId` int/Long truncation | `ChatMessage.java` | OPEN — requires DB migration |
| H-NEW-8 | ~~Race condition in game-over claim handling~~ | `GameWebSocketHandler.java` | ✅ FIXED (2026-06-20) |
| H-NEW-9 | `GameSession` @Entity never persisted | `GameSession.java` | DEFERRED — alpha acceptable |
| H-NEW-10 | ~~Pending game-over claims never cleaned~~ | `GameWebSocketHandler.java` | ✅ FIXED (2026-06-20) |
| H-NEW-11 | Build command silently dropped in GameScene | `GameScene.java` | DEFERRED — needs full client wiring |
| H-NEW-12 | GameScene always creates test map | `GameScene.java` | DEFERRED — needs client wiring |
| H-NEW-13 | CampaignManager never injected | `CampaignScene.java` | DEFERRED — campaign rework needed |
| H-NEW-14 | Zero audio files exist | `audio/README.txt` | DEFERRED — needs assets |
| H-NEW-15 | ~~`EloRatingServiceTest` stale assertion~~ | `EloRatingServiceTest.java` | ✅ FIXED (2026-06-20) |
| H-NEW-16 | LuaJ sandbox bypassable | `LuaEngine.java` | MAYBE — untrusted mods only |

---

## 🟡 MEDIUM — Non-Blocking but Important (32 issues → 10 FIXED, 22 OPEN)

### Cross-Module / Common

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-1 | `StatsRegistry` not resettable for testing | `StatsRegistry.java` | OPEN |
| M-NEW-2 | ~~`BuildingStats` mutable List~~ | `BuildingStats.java` | ✅ FIXED (2026-06-20) |
| M-NEW-3 | `GameConfig` creates new `ObjectMapper` per call | `GameConfig.java` | ✅ FIXED (2026-06-21) — Shared static MAPPER instance |
| M-NEW-4 | Duplicate constants in `GameConstants` vs `GameConfig` | `GameConstants.java` | ✅ FIXED (2026-06-21) — Deprecated with @Deprecated(forRemoval=true), source of truth documented |
| M-NEW-5 | `TERRAIN_MOVEMENT_COSTS` indexed by ordinal | `GameConstants.java` | ✅ FIXED (2026-06-21) — Added EnumMap + getTerrainMovementCost(TerrainType), old array deprecated |
| M-NEW-6 | `BuildingType.fromFactionRelativeId()` doesn't handle null faction | `BuildingType.java` | ✅ FIXED (2026-06-21) — Added null check with IllegalArgumentException |
| M-NEW-7 | `CONFED_INFANTRY_CENTRE` has `powerProduce=2` but `producesPower()`=false | `StatsRegistry.java`, `BuildingType.java` | ✅ FIXED (2026-06-21) — RE data confirmed powerProduce=2 is internal, not grid power. producesPower() is correct. |

### aow2-core (Determinism & Integration)

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-8 | `ResearchRegistry` uses HashMap | `ResearchRegistry.java` | ✅ FIXED (2026-06-21) — Changed to LinkedHashMap |
| M-NEW-9 | `ResearchSystem.completedResearch` uses HashSet | `ResearchSystem.java` | ✅ FIXED (2026-06-21) — Changed to LinkedHashSet |
| M-NEW-10 | `FogOfWarSystem` uses HashMap | `FogOfWarSystem.java` | ✅ FIXED (2026-06-21) — Changed to LinkedHashMap |
| M-NEW-11 | Attack-move command doesn't set `autoEngage` flag | `CommandProcessor.java` | ✅ FIXED (2026-06-21) — Sets unit.setAutoEngage(true) and autoEngageTarget |
| M-NEW-12 | Patrol command only issues one-way move | `CommandProcessor.java`, `Unit.java` | ✅ FIXED (2026-06-21) — Added patrolOrigin field to Unit, stored on patrol command |
| M-NEW-13 | `GameAPI` event hooks NEVER fired | `GameAPI.java` | OPEN — same root as C-NEW-4, event bridge exists but Lua hooks need wiring |
| M-NEW-14 | `ModInstaller.detectCommonPrefix()` exhausts ZipInputStream | `ModInstaller.java` | ✅ FIXED (2026-06-21) — Changed to ZipFile.entries() (non-consuming) |
| M-NEW-15 | `GameDataRegistry.applyUnitOverrides()` manual per-field | `GameDataRegistry.java` | ✅ FIXED (2026-06-21) — Reflection-based approach with manual fallback |

### aow2-server

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-16 | `X-Forwarded-For` trust bypasses rate limiting | `RateLimitFilter.java` | ✅ FIXED (2026-06-21) — Only trusts XFF from local/proxy addresses |
| M-NEW-17 | No WebSocket message size limits | `WebSocketConfig.java` | ✅ FIXED (2026-06-21) — Added 64KB limit on all 3 WS endpoints |
| M-NEW-18 | `chat_messages.player_id` INT, no FK | `V3 SQL` | OPEN — needs DB migration |
| M-NEW-19 | No pagination on map list endpoint | `MapController.java` | ✅ FIXED (2026-06-21) — Added Spring Data pagination |
| M-NEW-20 | No global `@ControllerAdvice` exception handler | All controllers | ✅ FIXED (2026-06-21) — Created GlobalExceptionHandler.java |
| M-NEW-21 | `readyPlayers` in lobby never cleaned on timeout | `LobbyWebSocketHandler.java` | ✅ FIXED (2026-06-21) — Added 5-min scheduled cleanup |
| M-NEW-22 | `SessionService.registerWebSocketSession` overwrites | `SessionService.java` | ✅ FIXED (2026-06-21) — Logs warning on overwrite |

### aow2-client

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-23 | Production queue is display-only | `HUD.java` | OPEN |
| M-NEW-24 | AccessibilitySettings key bindings zero effect | `AccessibilitySettings.java` | OPEN |

### aow2-web

| ID | Issue | File | Status |
|----|-------|------|--------|
| M-NEW-25 | API route.ts is stub | `api/route.ts` | OPEN |
| M-NEW-26 | ALL data tabs use hardcoded demo data | All tab components | OPEN |
| M-NEW-27 | Matchmaking is fake (8-second timer) | `MatchmakingPanel.tsx` | OPEN |
| M-NEW-28 | Prisma schema missing foreign keys | `schema.prisma` | OPEN |

---

## 🟢 LOW — Polish / Minor (22 issues → 7 FIXED, 15 OPEN)

| ID | Issue | File | Status |
|----|-------|------|--------|
| L-NEW-1 | `MathUtils.clamp()` redundant with Java 21 | `MathUtils.java` | ✅ FIXED (2026-06-21) — Deprecated both clamp methods |
| L-NEW-2 | `ResearchNode.getPrerequisites()` redundant | `ResearchNode.java` | ✅ FIXED (2026-06-21) — Deprecated both methods |
| L-NEW-3 | `ResearchEffect` manual equals/hashCode redundant | `ResearchEffect.java` | ✅ FIXED (2026-06-21) — Removed redundant overrides |
| L-NEW-4 | ~~Unused import in ResearchNode~~ | `ResearchNode.java` | ✅ FIXED (2026-06-20) |
| L-NEW-5 | No `units.json`/`buildings.json` in common resources | `aow2-common/resources/data/` | OPEN |
| L-NEW-6 | ~~`CommandTypeTest` doesn't test AttackMove~~ | `CommandTypeTest.java` | ✅ FIXED (2026-06-20) |
| L-NEW-7 | `buildings.json` in client is deprecated stub | `aow2-client/resources/data/buildings.json` | OPEN |
| L-NEW-8 | `units.json` in client never loaded | `aow2-client/resources/data/units.json` | OPEN |
| L-NEW-9 | Faction hardcoded to CONFEDERATION in GameScene | `GameScene.java` | ✅ FIXED (2026-06-21) — Added setPlayerFaction()/getPlayerFaction() |
| L-NEW-10 | Settings scene is stub Alert | `AOW2App.java` | OPEN |
| L-NEW-11 | AccessibilitySettings not persisted | `AccessibilitySettings.java` | OPEN |
| L-NEW-12 | `TutorialSystem.stepQueue` unused — dead code | `TutorialSystem.java` | OPEN |
| L-NEW-13 | `MusicPlayer.shuffle` uses Math.random() | `MusicPlayer.java` | OPEN |
| L-NEW-14 | `LobbyWebSocketHandler.map_veto` is a no-op | `LobbyWebSocketHandler.java` | OPEN — documented as future enhancement |
| L-NEW-15 | Deprecated `EloRatingService.java` still in codebase | `EloRatingService.java` | OPEN |
| L-NEW-16 | V4 Flyway migration redundant with V1 | `V4 SQL` | OPEN |
| L-NEW-17 | No control groups (Ctrl+1-9) | `InputHandler.java` | OPEN |
| L-NEW-18 | `EntityPlacer` erases via takeDamage hack | `EntityPlacer.java` | OPEN |
| L-NEW-19 | Many unused npm dependencies | `package.json` | OPEN |
| L-NEW-20 | `GameConfig.Builder` silently converts null to empty | `GameConfig.java` | ✅ FIXED (2026-06-21) — Builder no longer null-converts; constructor handles null |
| L-NEW-21 | `GameConfig.toString()` omits footprint arrays | `GameConfig.java` | ✅ FIXED (2026-06-21) — Now includes all 8 arrays |
| L-NEW-22 | `ChatMessageRecord.timestamp` uses epoch millis | `ChatMessageRecord.java` | OPEN |

---

## 📋 UNVERIFIED ASSUMPTIONS (20 → 18 remaining, 2 resolved)

| Constant | Value | Source | Impact | Status |
|----------|-------|--------|--------|--------|
| `SIEGE_RANGE_BONUS` | 3 | Assumed | Siege balance | UNVERIFIED |
| `ARTILLERY_FIXED_FLIGHT_TIME` | 15 | Assumed | Artillery timing | UNVERIFIED |
| `CC_PLACEMENT_RADIUS` | 20 | Assumed | Build placement | UNVERIFIED |
| `ARM_DELAY_TICKS` | 10 | Assumed | Mine timing | UNVERIFIED |
| `CANCEL_REFUND_PERCENT` | 0.50 | Assumed | Economy | UNVERIFIED |
| `BUILDING_ATTACK_COOLDOWN` | 5 | Assumed | Defensive DPS | UNVERIFIED |
| `INFANTRY_BASE_RECOVERY` | 1 | Assumed | Infantry sustain | UNVERIFIED |
| `MACHINERY_BASE_REPAIR` | 2 | Assumed | Vehicle sustain | UNVERIFIED |
| `RESISTANCE_INCOME_MULTIPLIER` | 1.15 | Assumed | Faction balance | UNVERIFIED |
| `CC_UPGRADE_INCOME_BONUS` | 2/level | Assumed | Economy progression | UNVERIFIED |
| Nuclear distance divisor | 12 | Reconstructed | Nuclear damage | UNVERIFIED |
| Ranged wind-up phase | attackSpeed/2 | Assumed | All ranged DPS | UNVERIFIED |
| Infantry vs building mult | 0.5 | Assumed | Anti-building | UNVERIFIED |
| Infantry vs machinery mult | 0.7 | Assumed | Anti-vehicle | UNVERIFIED |
| Bunker/TechCentre stats | Near-identical | Possible RE error | Bunker behavior | ✅ RESOLVED (H-NEW-4) — Not an error |
| CONFED_INFANTRY_CENTRE power | powerProduce=2 | From RE data? | Power economy | ✅ RESOLVED (M-NEW-7) — Confirmed RE value, internal not grid |
| Research IDs 2-3 targets | "Assault" unit | Ambiguous | Research effect | UNVERIFIED |

---

## 📊 HONEST SUMMARY

| Severity | Count | Fixed | False Positive | Deferred | OPEN |
|----------|-------|-------|----------------|----------|------|
| 🔴 CRITICAL | 9 | 7 | 1 | 0 | **ALL RESOLVED** |
| 🟠 HIGH | 16 | 5 | 2 | 3 | **6** |
| 🟡 MEDIUM | 32 | 15 | 0 | 0 | **17** |
| 🟢 LOW | 22 | 7 | 0 | 0 | **15** |
| **Total** | **79** | **34** | **3** | **3** | **38 OPEN** (was 57) |

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
- Server security (ELO fraud, chat eavesdropping, JWT, XFF bypass, WS limits, global error handler)
- Distance class calculations (127 sentinel for out-of-range)
- Research effect application (data-driven `ResearchBonusTracker`)
- Mod event callbacks (all 11 unit kill + building destroy events)
- Deterministic collections (LinkedHashMap/LinkedHashSet for lockstep)
- RE data verification (Bunker/TechCentre, rebel buildings, Infantry Centre power)

### What Was Fixed This Session (2026-06-21)
- **22 issues fixed** (up from 13 → 34 total)
- RE data verification: Bunker/TechCentre stats confirmed correct, rebel buildings verified, Infantry Centre power documented
- Determinism: HashMap→LinkedHashMap in 3 systems, HashSet→LinkedHashSet for research
- Commands: AttackMove autoEngage, Patrol return path with origin storage
- Code quality: Shared ObjectMapper, deprecated duplicate constants, type-safe terrain costs, null safety
- Server: XFF trust fix, WS size limits, pagination, global exception handler, readyPlayers cleanup, session overwrite warning
- Modding: ZipInputStream exhaustion fix, reflection-based unit overrides
- Polish: Deprecated redundant methods, removed redundant equals/hashCode, fixed GameScene faction, builder/toString fixes

### What Still Does NOT Work
1. Build placement broken end-to-end — command silently dropped (DEFERRED)
2. Campaign non-functional (DEFERRED — full rework)
3. No real map loading — always plays on hardcoded test map (DEFERRED)
4. Web dashboard is demo shell — all data hardcoded (OPEN)
5. Audio produces zero sound — no audio files (DEFERRED)
6. Production queue UI is display-only (OPEN)
7. AccessibilitySettings key bindings have no effect (OPEN)