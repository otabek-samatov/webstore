package orderservice.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox")
@Getter
@Setter
public class OutboxProperties {

    private long pollIntervalMs = 5000;

    private long recoveryIntervalMs = 60000;

    private long stuckThresholdMinutes = 5;

    /**
     * Number of days to keep SENT events before cleanup (default: 3).
     */
    private long retentionDays = 3;

    /**
     * Cron expression for the cleanup job (default: 3 AM daily).
     */
    private String cleanupCron = "0 0 3 * * *";
}
