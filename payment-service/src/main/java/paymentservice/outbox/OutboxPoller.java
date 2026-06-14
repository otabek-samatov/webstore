package paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository repository;
    private final OutboxEventProcessor eventProcessor;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void poll() {
        List<OutboxEvent> pending = repository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Outbox poller found {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            eventProcessor.processEvent(event);  // cross-bean call — proxy works
        }
    }

    @Scheduled(fixedDelayString = "${outbox.recovery-interval-ms:60000}")
    @Transactional
    public void recoverStuckEvents() {
        Instant threshold = Instant.now().minus(
                properties.getStuckThresholdMinutes(), ChronoUnit.MINUTES);

        int recovered = repository.recoverStuckEvents(threshold);

        if (recovered > 0) {
            log.warn("Recovered {} stuck outbox events", recovered);
        }
    }
}
