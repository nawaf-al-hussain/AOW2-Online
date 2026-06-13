-- V2: Add game_sessions table for multiplayer session tracking
-- REF: session_lifecycle.md - Session states: WAITING, STARTING, ACTIVE, COMPLETED, DISCONNECTED, DESYNC
-- REF: protocol_specification.md - Type 4 SESSION_INIT, Type 12 MATCH_START

CREATE TABLE game_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_uuid VARCHAR(36) UNIQUE NOT NULL,
    player1_id BIGINT REFERENCES players(id) NOT NULL,
    player2_id BIGINT REFERENCES players(id) NOT NULL,
    map_name VARCHAR(64),
    state VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    winner_id BIGINT REFERENCES players(id),
    duration_seconds INT,
    player1_sync_hash BIGINT,
    player2_sync_hash BIGINT,
    desync_detected BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Add ELO and score columns to match_results
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS player1_score INT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS player2_score INT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS player1_elo_before INT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS player2_elo_before INT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS player1_elo_after INT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS player2_elo_after INT;
ALTER TABLE match_results ADD COLUMN IF NOT EXISTS desync_detected BOOLEAN DEFAULT FALSE NOT NULL;

-- Indexes for game session lookups
CREATE INDEX idx_game_sessions_player1 ON game_sessions(player1_id);
CREATE INDEX idx_game_sessions_player2 ON game_sessions(player2_id);
CREATE INDEX idx_game_sessions_state ON game_sessions(state);
CREATE INDEX idx_game_sessions_uuid ON game_sessions(session_uuid);
