package productservice.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InboxCleaner {

    private static final int BATCH_SIZE = 1000;

    private final InboxMessageRepository repository;
    private final InboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * Deletes PROCESSED inbox messages older than the configured retention
     * period in batches. Schedule staggered after the outbox cleanup.
     */
    @Scheduled(cron = "${inbox.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        Instant threshold = Instant.now().minus(
                properties.getRetentionDays(), ChronoUnit.DAYS);

        int totalDeleted = 0;
        Integer deleted;

        do {
            deleted = transactionTemplate.execute(status ->
                    repository.deleteProcessedBefore(threshold, BATCH_SIZE)
            );

            deleted = Optional.ofNullable(deleted).orElse(0);

            totalDeleted += deleted;

        } while (deleted == BATCH_SIZE);

        if (totalDeleted > 0) {
            log.info("Inbox cleanup: deleted {} old messages", totalDeleted);
        }
    }
}
