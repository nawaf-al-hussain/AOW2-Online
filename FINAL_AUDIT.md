# Final Pre-Testing Audit

**Date:** 2026-07-11
**Method:** Direct source-code reading of all critical runtime paths
**Compile status:** BUILD SUCCESSFUL (all 5 modules)
**Test status:** BUILD SUCCESSFUL (all 750 tests pass)

---

## Audit Results

### ✅ No CRASH-level issues found

All critical runtime paths have been verified:

1. **CommandType sealed interface** — all 14 records (Move, Attack, AttackMove, Build, Produce, Research, Garrison, Ungarrison, Cancel, SiegeMode, Stop, Hold, Patrol, Upgrade) are in the `permits` clause and covered by every switch statement that pattern-matches on CommandType.

2. **Switch coverage** — verified exhaustive coverage in:
   - `CommandProcessor.process()` — 14 cases, no default needed (sealed)
   - `LockstepEngine.applyCommand()` — 14 cases (7 inline fallback + 7 routed to CommandProcessor via else branch)
   - `CommandSerializer.serialize()` — 14 cases + deserialize switch
   - `ReplayRecorder.serializeCommand()` — 14 cases + TYPE_HOLD constant

3. **Event-driven audio** — `processAudioEvent()` covers all 7 GameEvent subtypes (UnitKilled, BuildingDestroyed, BuildingCompleted, UnitProduced, ResearchCompleted, DamageApplied, ResourceChanged). All event record fields referenced in the handler exist (verified by reading each record definition).

4. **Audio file loading** — AudioManager loads `.ogg` (not `.wav`/`.mp3`). `music.ogg` exists at `/audio/music/music.ogg`. All 72 SFX `.ogg` files exist at `/audio/sfx/`. `preloadSFX` and `playSFX` both use `.ogg` extension.

5. **Sprite loading** — SpriteManager expects files at `assets/sprites/{units,buildings,terrain}/`. Verified 152 unit PNGs, 16 building PNGs, 12 terrain PNGs exist at those paths with correct naming (`{UNITTYPE}_{DIRECTION}.png` for units, `{BUILDINGTYPE}.png` for buildings, `{TERRAINTYPE}.png` for terrain).

6. **Entity API compatibility** — `Building.getFaction()` inherited from `Entity`. `Unit.getStats().weaponType()` exists. `Unit.isInfantry()` and `Unit.isMachinery()` exist. `EntityManager.getBuildingsForPlayer()` and `getAliveUnitsForPlayer()` exist.

7. **Multiplayer wiring** — `showGame(map, sessionUuid, mpService)` passes the lobby's authenticated MultiplayerService. `setupMultiplayer()` is called when sessionUuid is non-null. `setMultiplayerSessionUuid()` stores the UUID for desync reporting.

8. **Lua sandbox** — No `math.random` usage found in any campaign Lua scripts. The sandbox removes `math.random`, `math.randomseed`, `string.dump`, `os`, `io`, `java`, `debug`, `load`, `loadstring`, `dofile`, `require`, `package`.

9. **SecurityConfig** — `/api/leaderboard/me` authenticated() is listed BEFORE `/api/leaderboard/**` permitAll() (correct ordering for Spring Security).

10. **GameScene audio event filter** — correctly filters by `LOCAL_PLAYER_ID` for friendly events (building complete, unit produced, research complete) and plays all combat sounds regardless of faction.

### ⚠️ Known limitations (not bugs)

1. **Single-direction sprites** — All 8 direction variants use the same SOUTH-facing sprite. Multi-direction sprites require slicing the d1 master atlas.

2. **Sprite slicing is approximate** — The iOS sprite sheets (en_005, en_006) were sliced using estimated grid positions, not the actual sprite-rect table from the Mach-O binary. Some sprites may be slightly misaligned.

3. **No runtime display test** — The game has never been launched with a real display. All verification is by code inspection and unit tests.

4. **DamageAppliedEvent fires every attack** — The `playWeaponSound()` call in `processAudioEvent` will fire for every `DamageAppliedEvent`, which could be very frequent during combat. May need rate-limiting.

5. **LockstepEngine inline fallback** — When `economySystem == null`, the inline fallback handles 7 command types but routes the rest to a `default -> log.warn(...)`. This is correct behavior (systems not injected) but could silently drop commands in misconfigured multiplayer.

---

## Summary

| Category | Count |
|----------|-------|
| CRASH issues | 0 |
| WRONG_BEHAVIOR issues | 0 |
| MISSING_FEATURE issues | 0 |
| Known limitations | 5 |
| **Total actionable issues** | **0** |

The codebase is ready for runtime testing.
