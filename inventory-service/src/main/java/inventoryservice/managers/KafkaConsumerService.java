package inventoryservice.managers;


import inventoryservice.dto.kafka.StockStatusKafka;
import inventoryservice.inbox.InboxMessage;
import inventoryservice.inbox.InboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    /**
     * Producers should stamp this header with a stable idempotency key.
     * If absent we fall back to a derived business key — see
     * {@link #idempotencyKey}.
     */
    private static final String MESSAGE_ID_HEADER = "X-Message-Id";

    private final InventoryManager inventoryManager;
    private final InboxProcessor inboxProcessor;


    @Transactional
    @KafkaListener(topics = "${topic.stock.status}", containerFactory = "kafkaListenerContainerFactory")
    public void handleStockStatusUpdate(
            ConsumerRecord<String, StockStatusKafka> record,
            @Header(name = MESSAGE_ID_HEADER, required = false) String messageIdHeader) {

        StockStatusKafka event = record.value();

        log.info("Received stock-status event orderId={} actionType={} topic={} partition={} offset={}",
                event.getOrderId(), event.getActionType(),
                record.topic(), record.partition(), record.offset());

        if (event.getOrderId() == null) {
            log.warn("Ignoring event with null orderId actionType={}", event.getActionType());
            return;
        }

        String messageId = idempotencyKey(messageIdHeader, event);

        InboxMessage inboxMessage = inboxProcessor.fromKafkaRecord(
                messageId,
                "Order",
                String.valueOf(event.getOrderId()),
                event.getActionType(),
                record,
                event);

        Runnable r = null;
        if ("release".equalsIgnoreCase(event.getActionType())) {
            r = () -> {
                event.getStockLevels().forEach(stockLevel -> inventoryManager.revertStock(stockLevel.toInventoryDto()));
            };
        } else if ("commit".equalsIgnoreCase(event.getActionType())) {
            r = () -> {
                event.getStockLevels().forEach(stockLevel -> inventoryManager.commitStock(stockLevel.toInventoryDto()));
            };
        }

        if (r == null) {
            log.error("Ignoring event with unknown actionType={}", event.getActionType());
            return;
        }

        boolean processed = inboxProcessor.processOnce(inboxMessage, r);

        if (!processed) {
            log.info("Duplicate order-status event skipped: messageId={} orderId={} actionType={}",
                    messageId, event.getOrderId(), event.getActionType());
        }
    }

    /**
     * Prefer the producer-supplied {@value #MESSAGE_ID_HEADER} header. If
     * absent, fall back to a stable business key. We deliberately do NOT use
     * {@code topic-partition-offset} as the fallback — a producer retry can
     * land the same logical event at a different offset, which would defeat
     * deduplication.
     */
    private String idempotencyKey(String headerValue, StockStatusKafka event) {
        if (StringUtils.hasText(headerValue)) {
            return headerValue;
        }

        return "stock-status:" + event.getOrderId() + ":" + event.getActionType();
    }
}
