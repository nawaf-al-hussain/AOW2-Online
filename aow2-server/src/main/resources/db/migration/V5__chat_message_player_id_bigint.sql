-- FIX (M-NEW-18, H-NEW-7): Change player_id from INT to BIGINT to match Long type in JPA entity
ALTER TABLE chat_messages ALTER COLUMN player_id TYPE BIGINT;
-- NOTE: Foreign key to players(id) deferred — chat_messages can reference players from different DB shards in future