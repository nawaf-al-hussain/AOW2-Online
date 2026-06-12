-- Player accounts table
CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    elo_rating INT DEFAULT 1000 NOT NULL,
    games_played INT DEFAULT 0 NOT NULL,
    games_won INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Match results table
CREATE TABLE match_results (
    id BIGSERIAL PRIMARY KEY,
    player1_id BIGINT REFERENCES players(id) NOT NULL,
    player2_id BIGINT REFERENCES players(id) NOT NULL,
    winner_id BIGINT REFERENCES players(id),
    map_name VARCHAR(64),
    duration_seconds INT,
    replay_file_path VARCHAR(255),
    played_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Uploaded custom maps table
CREATE TABLE uploaded_maps (
    id BIGSERIAL PRIMARY KEY,
    uploader_id BIGINT REFERENCES players(id) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    map_data JSONB NOT NULL,
    download_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Create indexes
CREATE INDEX idx_match_results_player1 ON match_results(player1_id);
CREATE INDEX idx_match_results_player2 ON match_results(player2_id);
CREATE INDEX idx_uploaded_maps_uploader ON uploaded_maps(uploader_id);
CREATE INDEX idx_players_elo ON players(elo_rating DESC);
