package orderservice.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, String> {

    boolean existsByMessageId(String messageId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE InboxMessage m
               SET m.status = 'PROCESSED',
                   m.processedAt = :now
             WHERE m.messageId = :messageId
            """)
    int markProcessed(@Param("messageId") String messageId, @Param("now") Instant now);

    /**
     * Deletes a batch of PROCESSED messages older than the given threshold.
     * Native query with LIMIT to avoid loading entities into memory.
     */
    @Modifying
    @Query(value = """
            DELETE FROM inbox_messages
             WHERE message_id IN (
                 SELECT message_id FROM inbox_messages
                  WHERE status = 'PROCESSED'
                    AND processed_at < :threshold
                  LIMIT :batchSize
             )
            """, nativeQuery = true)
    int deleteProcessedBefore(@Param("threshold") Instant threshold,
                              @Param("batchSize") int batchSize);
}
