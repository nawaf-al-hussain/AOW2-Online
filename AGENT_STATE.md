# Agent State

**Last updated:** 2026-06-29T22:30:00+08:00
**Current iteration:** DONE
**Phase:** DONE

## Issue Registry

Populated from FULL_ANALYSIS.md §3 (Confirmed Bugs), §7 (Completeness Gaps), §11 (Recommendations).
IDs F-01 through F-30 assigned per the loop prompt priority order.

| ID | Source | Severity | Title | Status | Verified |
|----|--------|----------|-------|--------|---------|
| F-01 | FULL_ANALYSIS §3 | CRITICAL | Missing `last_sync_tick` DB column → add V6 migration | VERIFIED | 2026-06-29 |
| F-02 | FULL_ANALYSIS §3 | CRITICAL | `/api/leaderboard/me` NPE on unauthenticated access | VERIFIED | 2026-06-29 |
| F-03 | FULL_ANALYSIS §3 | CRITICAL | `ChatController.sendMessage` no participation check | VERIFIED | 2026-06-29 |
| F-04 | FULL_ANALYSIS §7.4 | HIGH | Produce/Train command unreachable — no production UI | VERIFIED | 2026-06-29 |
| F-05 | FULL_ANALYSIS §7.4 | HIGH | Research command unreachable — no research UI | VERIFIED | 2026-06-29 |
| F-06 | FULL_ANALYSIS §7.4 | HIGH | Garrison command unreachable — no hotkey | VERIFIED | 2026-06-29 |
| F-07 | FULL_ANALYSIS §7.4 | HIGH | Siege mode unreachable — no hotkey | VERIFIED | 2026-06-29 |
| F-08 | FULL_ANALYSIS §3 | HIGH | `cancel_production:N` not handled in GameScene | VERIFIED | 2026-06-29 |
| F-09 | FULL_ANALYSIS §3 | MEDIUM | Multiplayer match-found → game-start: session UUID not passed | VERIFIED | 2026-06-29 |
| F-10 | FULL_ANALYSIS §3 | HIGH | A/S/D key conflict (camera pan vs game commands) | VERIFIED | 2026-06-29 |
| F-11 | FULL_ANALYSIS §3 | MEDIUM | Hold command identical to Stop — no distinct behavior | VERIFIED | 2026-06-29 |
| F-12 | FULL_ANALYSIS §3 | LOW | `ep2_mission7.lua` syntax error (missing closing quote, line 108) | VERIFIED | 2026-06-29 (already fixed) |
| F-13 | FULL_ANALYSIS §3 | MEDIUM | Campaign save fails — `gameState`/`entityManager` never set in `CampaignScene` | VERIFIED | 2026-06-29 |
| F-14 | FULL_ANALYSIS §3 | MEDIUM | Non-atomic ELO recording (session complete + ELO not in shared transaction) | VERIFIED | 2026-06-29 |
| F-15 | FULL_ANALYSIS §3 | MEDIUM | `leaveQueue` not synchronized with `queueLock` | VERIFIED | 2026-06-29 |
| F-16 | FULL_ANALYSIS §3 | MEDIUM | No double-session guard in `SessionService.createSession()` | VERIFIED | 2026-06-29 |
| F-17 | FULL_ANALYSIS §3 (partial) | MEDIUM | `SessionService.reportSyncHash` missing `@Transactional` | VERIFIED | 2026-06-29 |
| F-18 | FULL_ANALYSIS §4 (H-AUDIT-1 note) | MEDIUM | `SessionService` session lock entries never removed (memory leak) | VERIFIED | 2026-06-29 |
| F-19 | FULL_ANALYSIS §3 | MEDIUM | `MatchmakingPanel` bypasses `apiUrl()` — POST goes to Next.js not Spring Boot | VERIFIED | 2026-06-29 |
| F-20 | FULL_ANALYSIS §10 | MEDIUM | `ChatTab` has no WebSocket connection — chat is local state only | DEFERRED | 2026-06-29 — requires frontend WebSocket integration testing |
| F-21 | FULL_ANALYSIS §3 | MEDIUM | Replay viewer not wired from any menu | VERIFIED | 2026-06-29 |
| F-22 | FULL_ANALYSIS §3 | MEDIUM | Settings scene is a stub — connect `AccessibilitySettings` | DEFERRED | 2026-06-29 — requires UI layout work + runtime testing |
| F-23 | FULL_ANALYSIS §3 | MEDIUM | `Upgrade` command missing input validation in compact constructor | VERIFIED | 2026-06-29 |
| F-24 | FULL_ANALYSIS §3 | MEDIUM | `onAreaEntered()` Lua hook never dispatched | DEFERRED | 2026-06-29 — requires game-loop proximity check, architectural change |
| F-25 | FULL_ANALYSIS §3 (known limitation #2) | MEDIUM | Script messages never displayed in HUD | DEFERRED | 2026-06-29 — requires HUD layout + polling wiring |
| F-26 | FULL_ANALYSIS §3 | LOW | `SHALLOW_WATER` passability contradiction in `TerrainType` | VERIFIED | 2026-06-29 |
| F-27 | FULL_ANALYSIS §3 | LOW | Vehicle armor research map empty (verify against RE docs first) | DEFERRED | 2026-06-29 — by design per RE docs |
| F-28 | FULL_ANALYSIS §3 | LOW | Download count inflation on maps (no per-player deduplication) | DEFERRED | 2026-06-29 — requires DB schema change |
| F-29 | FULL_ANALYSIS §11 | LOW | Remove unused npm dependencies | VERIFIED | 2026-06-29 |
| F-30 | FULL_ANALYSIS §6.3 | LOW | Add rate limiting to chat, matchmaking, map upload, replay upload endpoints | DEFERRED | 2026-06-29 — requires Spring Boot interceptors |

**Note:** Lua sandbox issues (instruction-limit not enforced, `getGlobals()` not removed) called out in FULL_ANALYSIS §3 are NOT in this registry because fixing them requires either forking LuaJ or accepting architectural risk — both qualify as DEFERRED under the loop's "out of scope" stopping condition. They will be added as F-31/F-32 with DEFERRED status in the first iteration so they appear in the final summary.

## Current Work Item

**ID:** F-20
**What I'm doing:** Wire ChatTab WebSocket connection so chat messages go to the server, not just local state.
**Verification gate:** ChatTab opens a WebSocket to /ws/chat, sends and receives messages.

## Deferred Items

| ID | Reason |
|----|--------|
| F-20 | Requires frontend WebSocket integration testing (can't verify without running browser) |
| F-22 | Requires UI layout work + runtime testing to verify AccessibilitySettings renders correctly |
| F-24 | Requires game-loop proximity check — architectural change to TickManager |
| F-25 | Requires HUD layout + polling wiring — UI feature work |
| F-27 | By design per RE docs — no research IDs add vehicle armor through Z[] array |
| F-28 | Requires DB schema change (new table for per-player download tracking) |
| F-30 | Requires Spring Boot interceptors — infrastructure work |
| F-31 | Lua instruction limit not enforced — requires LuaJ fork or thread hook (architectural) |
| F-32 | Lua getGlobals() not removed — deprecated but accessible, removal would break existing mod API |

## Notes

- FULL_ANALYSIS.md is copied to repo root so it's tracked alongside fixes.
- All 30 issues are TODO; iteration 1 begins with F-01.
- Commit cadence: one commit per verified fix, message format `fix(F-XX): <title> — gate VERIFIED`.
- Progress docs to update periodically: `projectprogress.md` (may not exist yet — create if so) and `TODO.md`.
