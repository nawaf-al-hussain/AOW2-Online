# Loop Log

Append-only. One entry per iteration. Never edited.


---
## Iteration 1 — 2026-06-29T20:10:00+08:00

**Item:** F-01 — Missing `last_sync_tick` DB column → add V6 migration
**Action taken:**
- Created `aow2-server/src/main/resources/db/migration/V6__add_last_sync_tick.sql` with `ALTER TABLE game_sessions ADD COLUMN last_sync_tick BIGINT;`
- Confirmed `GameSession.java` line 80-81 declares `@Column(name = "last_sync_tick") private Long lastSyncTick;`
- Confirmed V2 migration creates 14 columns; entity has 15 fields (14 @Column + 1 @Id). V6 adds the missing 15th column.
- Ran `./gradlew :aow2-server:test` — BUILD SUCCESSFUL, all server tests pass with V6 migration in place.
**Gate result:** PASS
- `grep "last_sync_tick" V6...sql` returns `ALTER TABLE game_sessions ADD COLUMN last_sync_tick BIGINT;` ✅
- DB columns (14 + 1 = 15) = entity fields (15) ✅
- All column names match between entity @Column annotations and DB columns (V2 + V6) ✅
**New status:** VERIFIED
**Next item:** F-02 — `/api/leaderboard/me` NPE on unauthenticated access
