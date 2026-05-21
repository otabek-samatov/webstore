package orderservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Modifying
    @Query("""
            UPDATE OutboxEvent e
               SET e.status = 'PROCESSING'
             WHERE e.id = :id
               AND e.status = 'PENDING'
            """)
    int claimEvent(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query("""
            UPDATE OutboxEvent e
               SET e.status = 'SENT',
                   e.processedAt = :now
             WHERE e.id = :id
            """)
    void markSent(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE OutboxEvent e
               SET e.status = 'PENDING'
             WHERE e.id = :id
               AND e.status IN ('PROCESSING', 'FAILED')
            """)
    void markPendingForRetry(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query("""
            UPDATE OutboxEvent e
               SET e.status = 'PENDING'
             WHERE e.status = 'PROCESSING'
               AND e.createdAt < :threshold
            """)
    int recoverStuckEvents(@Param("threshold") Instant threshold);

    /**
     * Deletes a batch of SENT events older than the given threshold.
     * Uses native query with LIMIT to avoid loading entities into memory.
     */
    @Modifying
    @Query(value = """
            DELETE FROM outbox_events
             WHERE id IN (
                 SELECT id FROM outbox_events
                  WHERE status = 'SENT'
                    AND processed_at < :threshold
                  LIMIT :batchSize
             )
            """, nativeQuery = true)
    int deleteSentBefore(@Param("threshold") Instant threshold,
                         @Param("batchSize") int batchSize);
}
