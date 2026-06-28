-- V6: Add last_sync_tick column to game_sessions table
-- REF: FULL_ANALYSIS.md §3 [CRITICAL] Missing last_sync_tick column in database migration
-- REF: GameSession.java line 80-81 — @Column(name = "last_sync_tick") private Long lastSyncTick;
--
-- The GameSession entity declares lastSyncTick but no prior migration added the column.
-- With ddl-auto: validate (application.yml line 14), Hibernate schema validation fails
-- on server startup, preventing production deployment.
--
-- This migration adds the missing column. Nullable because existing rows (if any) have
-- no last_sync_tick value; new rows will populate it via SessionService.reportSyncHash().

ALTER TABLE game_sessions ADD COLUMN last_sync_tick BIGINT;
