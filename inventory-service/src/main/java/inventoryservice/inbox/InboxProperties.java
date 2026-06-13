package inventoryservice.inbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inbox")
@Getter
@Setter
public class InboxProperties {

    /**
     * Number of days to keep PROCESSED messages before cleanup (default: 7).
     * Should be longer than the producer's outbox retention so duplicate
     * redeliveries are still recognized.
     */
    private long retentionDays = 7;

    /**
     * Cron expression for the cleanup job (default: 3:30 AM daily,
     * staggered after the outbox cleanup at 3:00 AM).
     */
    private String cleanupCron = "0 30 3 * * *";
}
