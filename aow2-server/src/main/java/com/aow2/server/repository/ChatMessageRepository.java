package com.aow2.server.repository;

import com.aow2.server.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ChatMessage entity persistence and querying.
 * Provides lookup by match ID for chat history retrieval.
 * REF: protocol_specification.md - Chat messages
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Finds all chat messages for a specific match, ordered by timestamp ascending.
     *
     * @param matchId the match/session UUID
     * @return list of chat messages for the match in chronological order
     */
    List<ChatMessage> findByMatchIdOrderByTimestampAsc(String matchId);

    /**
     * Finds the most recent chat messages for a specific match, limited by count.
     *
     * @param matchId the match/session UUID
     * @return list of recent chat messages ordered by timestamp descending
     */
    List<ChatMessage> findTop50ByMatchIdOrderByTimestampDesc(String matchId);
}
