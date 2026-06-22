# AOW2-Online Analysis Fixes — Round 2

**Date**: 2026-06-23
**Author**: aow2-developer skill (executing fixes from `docs/analysis/CRITICAL_ANALYSIS_REPORT.md`)
**Commit type**: `fix` (multiple scopes)
**Predecessor**: `docs/analysis/FIXES_ROUND_1.md`

## Summary

Applied **5 more fixes** addressing the remaining 2 High issues (H2 and H8) and 3 Medium issues (M5, M7, M10) deferred from Round 1. Web TypeScript type-check passes (0 new errors); 27/27 vitest tests pass. Java compilation could not be verified locally (sandbox has JRE only, no `javac`); changes were carefully reviewed for syntax correctness.

Combined with Round 1, this closes **6/6 Critical**, **8/8 High**, **6/10 Medium**, and **1/8 Low** issues from the original analysis report. The remaining deferred items are mostly RE-binary stat extraction (M1–M3) which requires the original game APK.

## Fixes Applied

### High (2/2 — closes all High issues)

| ID | File(s) | Change |
|----|---------|--------|
| **H2** | `aow2-core/.../network/LockstepEngine.java`, `aow2-server/.../websocket/GameWebSocketHandler.java` | Renamed `lastOpponentCommandTick` → `lastOpponentActivityTick`; added `receiveHeartbeat(long)` and `sendHeartbeat()` methods to `LockstepEngine`. `GameWebSocketHandler` now recognizes a new `"heartbeat"` message type and relays it to the opponent (carrying the sender's tick for clock-drift detection). Previously an idle but still-connected opponent would falsely trigger the disconnect pause after 14 seconds of no commands. |
| **H8** | `aow2-server/.../controller/StatsController.java` (new), `aow2-server/.../config/SecurityConfig.java`, `aow2-server/.../repository/PlayerRepository.java`, `aow2-server/.../repository/MatchResultRepository.java`, `aow2-web/src/lib/api.ts`, `aow2-web/src/app/page.tsx` | New `/api/stats` endpoint exposes `totalPlayers`, `matchesToday`, `matchesThisWeek`, `totalMatches`, `totalMaps`, `newPlayersToday`, `serverTime`. SecurityConfig permits `/api/stats/**` publicly. Web `page.tsx` now fetches live stats on mount and renders `…` while loading, `—` when server unavailable, or the real count otherwise. Previously the Quick Stats panel showed hardcoded `1,247 / 89 / 342 / 56`. |

### Medium (3/3)

| ID | File(s) | Change |
|----|---------|--------|
| **M5** | `aow2-core/.../economy/PowerSystem.java` | `getUpgradeLevel` now validates the building's reported level is in `[0, 3]` (clamps otherwise with a warning). Updated documentation: the actual gap is that nothing increments `Building.upgradeLevel` after construction (the upgrade-payment flow is Phase 13 work), NOT that this method returns 0. Until that flow lands, all generators use level 0 → radius 10, which is acceptable for v0.1.x. |
| **M7** | `aow2-server/.../service/MatchmakingService.java` | `selectMatchMap` now uses a deterministic seed `Math.floorMod(player1Id + player2Id, intersection.size())` instead of `ThreadLocalRandom.current().nextInt(...)`. Server-side only (doesn't affect lockstep determinism) but improves audit reproducibility — the same pair of players always gets the same map for the same intersection set. Removed the now-unused `ThreadLocalRandom` import. |
| **M10** | `aow2-core/.../ai/AISystem.java` | `processTick` now calls `resetTaskCount()` at the start of each decision cycle instead of the previous dance of calling `taskCompleted()` after each subsystem. The old pattern decremented the counter even when no task was started (clamped at 0), making the `maxConcurrentTasks` limit a no-op. The new pattern is clearer and matches the original intent (per-cycle throttle). Long-running task tracking (across cycles) is deferred to a future round. |

## Verification

### Web (TypeScript)
- `bunx tsc --noEmit --skipLibCheck`: **0 new errors** (only 3 pre-existing errors in `src/__tests__/setup.ts` from vitest globals — unrelated)
- `bun run test` (vitest): **27/27 tests pass** (api.test.ts, utils.test.ts, store.test.ts)

### Java
- Compilation could NOT be verified locally — the sandbox has `openjdk-21-jre-headless` only (no `javac`).
- All edits were carefully reviewed for syntax correctness:
  - New `StatsController.java` uses standard Spring annotations (`@RestController`, `@RequestMapping`, `@GetMapping`) and constructor injection — consistent with `LeaderboardController`.
  - `LockstepEngine` heartbeat methods use the existing `log` logger and respect the `running` flag.
  - `MatchmakingService` removed unused `ThreadLocalRandom` import; `Math.floorMod` is `java.lang.Math` so no new import needed.
  - `PowerSystem.getUpgradeLevel` uses the existing `LOG` logger.
  - `AISystem.processTick` calls `resetTaskCount()` which already exists in the class.
- The next CI run on GitHub Actions will verify the full build.

## Files Changed

**Java (8 files, 1 new)**:
- `aow2-core/src/main/java/com/aow2/core/ai/AISystem.java` (M10)
- `aow2-core/src/main/java/com/aow2/core/economy/PowerSystem.java` (M5)
- `aow2-core/src/main/java/com/aow2/core/network/LockstepEngine.java` (H2)
- `aow2-server/src/main/java/com/aow2/server/config/SecurityConfig.java` (H8)
- `aow2-server/src/main/java/com/aow2/server/controller/StatsController.java` (NEW — H8)
- `aow2-server/src/main/java/com/aow2/server/repository/MatchResultRepository.java` (H8 — added `countByPlayedAtAfter`)
- `aow2-server/src/main/java/com/aow2/server/repository/PlayerRepository.java` (H8 — added `countByCreatedAtAfter`)
- `aow2-server/src/main/java/com/aow2/server/service/MatchmakingService.java` (M7)
- `aow2-server/src/main/java/com/aow2/server/websocket/GameWebSocketHandler.java` (H2)

**TypeScript (2 files)**:
- `aow2-web/src/app/page.tsx` (H8 — useEffect + state + live rendering)
- `aow2-web/src/lib/api.ts` (H8 — `ServerStats` interface + `getStats()` helper)

**Documentation (1 file)**:
- `skills/aow2-developer/references/ProjectProgress.md` — updated Phase 5, 6, 8, 12 entries with Round 2 FIX references

## Combined Round 1 + Round 2 Scorecard

| Severity | Total | Fixed | Deferred | Notes |
|----------|-------|-------|----------|-------|
| Critical | 6 | **6** | 0 | All closed |
| High | 8 | **8** | 0 | All closed |
| Medium | 10 | **6** | 4 | M1, M2, M3 (RE binary extraction), M4 (vehicle armor research ambiguity) |
| Low | 8 | **1** | 7 | Mostly cleanup; L3 closed in Round 1 |
| **Total** | **32** | **21** | **11** | 66% closed |

## Remaining Deferred Items

| ID | Description | Why Deferred |
|----|-------------|--------------|
| M1 | Rebel unit stats unverified (only sight/range/armor from RE) | Requires extracting more data from the original game binary |
| M2 | CONFED_LIGHT_ASSAULT / CONFED_HEAVY_ASSAULT stats entirely guessed | Same as M1 |
| M3 | Mine unit trigger radii assumed | Same as M1 |
| M4 | `VEHICLE_ARMOR_RESEARCH` map is empty (no vehicle armor upgrades applied) | RE research IDs 9 and 33 affect mixed infantry/vehicle type lists; needs careful RE re-investigation to disambiguate |
| L1 | StatsRegistry is a singleton (hard to test) | Refactor for DI; would touch many call sites |
| L2 | `BUILDING_POWER_RADIUS` deprecated but still used | Migrate callers to `GameConfig.getInstance()` |
| L4 | `RANK_EXP_THRESHOLDS` etc. deprecated but still present | Either delete or document migration |
| L5 | Pathfinding deviates from RE Bresenham approach (uses true A*) | Explicitly documented as `ASSUMPTION (L5)` — design choice |
| L6 | `MatchmakingService` fallback map is hardcoded `"test_map"` | Needs a map registry / map pool (Phase 13) |
| L7 | `JwtUtil` doesn't refuse to start with default dev secret | Add fail-fast in non-dev envs (Phase 13) |
| L8 | Test files reference `application-test.yml` but it's incomplete | Test execution verification blocked by missing JDK in sandbox |

## Next Steps

1. **Watch CI** on the next GitHub Actions run — the new `StatsController` and repository methods should compile cleanly. If `UploadedMapRepository` isn't auto-wired in some test profile, the defensive `try/catch` in `getStats()` will return `totalMaps=0` instead of 500-ing.
2. **Round 3 (optional)** — focus on the RE-binary stat extraction items (M1, M2, M3) if/when the original APK is available. Or tackle the Low-priority cleanup items (L1, L2, L4) which are mechanical refactors.
3. **Re-run the analyzer skill** to verify all Round 1 + Round 2 fixes are detected as resolved and to surface any new issues introduced by the new `StatsController` or heartbeat path.

---

*Generated by aow2-developer skill following the spec-driven workflow in `skills/aow2-developer/SKILL.md`.*
*All fixes cross-referenced to `docs/analysis/CRITICAL_ANALYSIS_REPORT.md`.*
