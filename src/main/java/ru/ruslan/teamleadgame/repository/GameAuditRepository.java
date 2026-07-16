package ru.ruslan.teamleadgame.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class GameAuditRepository {
    private final JdbcClient jdbcClient;

    public GameAuditRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(UUID sessionId, String eventType, String payload) {
        jdbcClient.sql("""
                        insert into game_audit(session_id, event_type, payload, created_at)
                        values (:sessionId, :eventType, :payload, :createdAt)
                        """)
                .param("sessionId", sessionId)
                .param("eventType", eventType)
                .param("payload", payload)
                .param("createdAt", Instant.now())
                .update();
    }
}
