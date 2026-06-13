-- H2-compatible schema for testing
CREATE TABLE IF NOT EXISTS players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    elo_rating INT DEFAULT 1000 NOT NULL,
    games_played INT DEFAULT 0 NOT NULL,
    games_won INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS match_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player1_id BIGINT NOT NULL,
    player2_id BIGINT NOT NULL,
    winner_id BIGINT,
    player1_score INT,
    player2_score INT,
    player1_elo_before INT,
    player2_elo_before INT,
    player1_elo_after INT,
    player2_elo_after INT,
    map_name VARCHAR(64),
    duration_seconds INT,
    replay_file_path VARCHAR(255),
    desync_detected BOOLEAN DEFAULT FALSE NOT NULL,
    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS uploaded_maps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uploader_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    map_data CLOB NOT NULL,
    download_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS game_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_uuid VARCHAR(36) UNIQUE NOT NULL,
    player1_id BIGINT NOT NULL,
    player2_id BIGINT NOT NULL,
    map_name VARCHAR(64),
    state VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    winner_id BIGINT,
    duration_seconds INT,
    player1_sync_hash BIGINT,
    player2_sync_hash BIGINT,
    desync_detected BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);
