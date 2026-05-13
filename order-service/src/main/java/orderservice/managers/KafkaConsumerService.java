package orderservice.managers;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.kafka.OrderStatusKafka;
import orderservice.entities.OrderStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final OrderManager orderManager;


    @Transactional
    @KafkaListener(topics = "${topic.order.status}", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderStatusUpdate(OrderStatusKafka orderStatusKafka) {
        log.info("Received order-status event orderId={} actionType={}",
                orderStatusKafka.getOrderId(), orderStatusKafka.getActionType());

        OrderStatus status = null;
        if ("Completed".equalsIgnoreCase(orderStatusKafka.getActionType())) {
            status = OrderStatus.COMPLETED;
        } else if ("Refunded".equalsIgnoreCase(orderStatusKafka.getActionType())) {
            status = OrderStatus.REFUNDED;
        }

        if (status == null) {
            log.warn("Ignoring unknown actionType={} for orderId={}",
                    orderStatusKafka.getActionType(), orderStatusKafka.getOrderId());
            return;
        }

        orderManager.changeOrderStatus(orderStatusKafka.getOrderId(), status);
    }
}
