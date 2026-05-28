package orderservice.managers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.entities.OrderStatus;
import orderservice.repositories.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled reaper that cancels orders stuck in {@code PAYMENT_FAILED} for longer
 * than {@code order.payment-failed.retention-hours}.
 * <p>
 * A {@code PAYMENT_FAILED} order keeps its reserved stock so the customer can retry
 * payment ({@link OrderManager#retryPayment}). If they never do, the stock would be
 * held indefinitely — so this job transitions stale ones to {@code CANCELLED}, which
 * (via the existing {@link OrderManager#changeOrderStatus} logic) publishes a
 * {@code "release"} outbox event and frees the inventory.
 * <p>
 * Multi-instance safe: {@code changeOrderStatus} takes a pessimistic lock and is a
 * no-op once the order is already {@code CANCELLED}, so concurrent reapers can't
 * double-release.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedReaper {

    private final OrderRepository orderRepository;
    private final OrderManager orderManager;
    private final PaymentFailedReaperProperties properties;

    @Scheduled(cron = "${order.payment-failed.cleanup-cron:0 0 * * * *}")
    public void cancelStalePaymentFailedOrders() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.getRetentionHours()));

        List<Long> staleIds = orderRepository.findIdsByStatusAndCreatedBefore(
                OrderStatus.PAYMENT_FAILED, cutoff);

        if (staleIds.isEmpty()) {
            return;
        }

        log.info("Reaping {} stale PAYMENT_FAILED orders older than {}h",
                staleIds.size(), properties.getRetentionHours());

        int cancelled = 0;
        for (Long orderId : staleIds) {
            try {
                orderManager.changeOrderStatus(orderId, OrderStatus.CANCELLED);
                cancelled++;
            } catch (Exception e) {
                // Another instance may have already moved it, or it changed since the
                // query — log and continue so one bad row can't stall the batch.
                log.warn("Could not cancel stale PAYMENT_FAILED order id={} reason={}",
                        orderId, e.getMessage());
            }
        }

        log.info("Reaped {} of {} stale PAYMENT_FAILED orders", cancelled, staleIds.size());
    }
}
