package orderservice.managers;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.kafka.OrderStatusKafka;
import orderservice.entities.OrderStatus;
import orderservice.inbox.InboxMessage;
import orderservice.inbox.InboxProcessor;
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

    private final OrderManager orderManager;
    private final InboxProcessor inboxProcessor;


    @Transactional
    @KafkaListener(topics = "${topic.order.status}", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderStatusUpdate(
            ConsumerRecord<String, OrderStatusKafka> record,
            @Header(name = MESSAGE_ID_HEADER, required = false) String messageIdHeader) {

        OrderStatusKafka event = record.value();

        log.info("Received order-status event orderId={} actionType={} topic={} partition={} offset={}",
                event.getOrderId(), event.getActionType(),
                record.topic(), record.partition(), record.offset());

        if (event.getOrderId() == null) {
            log.warn("Ignoring event with null orderId actionType={}", event.getActionType());
            return;
        }

        OrderStatus status = mapStatus(event.getActionType());
        if (status == null) {
            log.warn("Ignoring unknown actionType={} for orderId={}",
                    event.getActionType(), event.getOrderId());
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

        boolean processed = inboxProcessor.processOnce(inboxMessage, () ->
                orderManager.changeOrderStatus(event.getOrderId(), status));

        if (!processed) {
            log.info("Duplicate order-status event skipped: messageId={} orderId={} actionType={}",
                    messageId, event.getOrderId(), event.getActionType());
        }
    }

    private OrderStatus mapStatus(String actionType) {
        if ("Completed".equalsIgnoreCase(actionType)) {
            return OrderStatus.COMPLETED;
        }
        if ("Refunded".equalsIgnoreCase(actionType)) {
            return OrderStatus.REFUNDED;
        }
        return null;
    }

    /**
     * Prefer the producer-supplied {@value #MESSAGE_ID_HEADER} header. If
     * absent, fall back to a stable business key. We deliberately do NOT use
     * {@code topic-partition-offset} as the fallback — a producer retry can
     * land the same logical event at a different offset, which would defeat
     * deduplication.
     */
    private String idempotencyKey(String headerValue, OrderStatusKafka event) {
        if (StringUtils.hasText(headerValue)) {
            return headerValue;
        }

        return "order-status:" + event.getOrderId() + ":" + event.getActionType();
    }
}
