package orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.configs.OutboxProperties;
import orderservice.repositories.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleaner {

    private static final int BATCH_SIZE = 1000;
    private final OutboxEventRepository repository;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * Runs once a day at 3 AM. Deletes SENT events older than
     * the configured retention period in batches.
     */
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 3 * * *}")
    public void cleanup() {
        Instant threshold = Instant.now().minus(
                properties.getRetentionDays(), ChronoUnit.DAYS);

        int totalDeleted = 0;
        Integer deleted;

        do {
            deleted = transactionTemplate.execute(status ->
                    repository.deleteSentBefore(threshold, BATCH_SIZE)
            );

            deleted = Optional.ofNullable(deleted).orElse(0);

            totalDeleted += deleted;

        } while (deleted == BATCH_SIZE);

        if (totalDeleted > 0) {
            log.info("Outbox cleanup: deleted {} old events", totalDeleted);
        }
    }
}
