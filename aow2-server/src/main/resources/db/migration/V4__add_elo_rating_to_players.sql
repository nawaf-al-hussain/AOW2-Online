-- V4: Add ELO rating and games played columns to players table
-- REF: multiplayer_architecture.md - ELO-based ranking for competitive play
-- NOTE: These columns may already exist if created by V1; use IF NOT EXISTS for safety
-- FIX (L-NEW-16): This migration is intentionally kept for databases that were created
-- from a backup or partial migration state. The IF NOT EXISTS guards ensure it is a
-- safe no-op on standard installations. No code changes needed — marked as reviewed.

ALTER TABLE players ADD COLUMN IF NOT EXISTS elo_rating INT DEFAULT 1000;
ALTER TABLE players ADD COLUMN IF NOT EXISTS games_played INT DEFAULT 0;

-- Create index for efficient ELO-based leaderboard queries
CREATE INDEX IF NOT EXISTS idx_players_elo ON players(elo_rating DESC);
