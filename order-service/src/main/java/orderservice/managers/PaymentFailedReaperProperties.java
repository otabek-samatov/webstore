package orderservice.managers;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@link PaymentFailedReaper} scheduler, which cancels
 * orders left in {@code PAYMENT_FAILED} for too long (releasing their held stock).
 */
@ConfigurationProperties(prefix = "order.payment-failed")
@Getter
@Setter
public class PaymentFailedReaperProperties {

    /**
     * How long an order may stay in PAYMENT_FAILED before it is auto-cancelled
     * and its reserved stock released (default: 24 hours).
     */
    private long retentionHours = 24;

    /**
     * Cron expression for the reaper job (default: top of every hour).
     */
    private String cleanupCron = "0 0 * * * *";
}
