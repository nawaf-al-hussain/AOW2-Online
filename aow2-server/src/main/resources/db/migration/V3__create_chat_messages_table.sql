-- V3: Create chat_messages table for lobby and in-game chat persistence
-- REF: protocol_specification.md - Chat messages between players

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    match_id VARCHAR(36),
    player_id INT NOT NULL,
    message VARCHAR(500) NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW() NOT NULL
);

-- Index for efficient chat history retrieval by match
CREATE INDEX idx_chat_messages_match_id ON chat_messages(match_id);
CREATE INDEX idx_chat_messages_timestamp ON chat_messages(timestamp);
