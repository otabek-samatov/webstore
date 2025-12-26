package orderservice.managers;


import lombok.RequiredArgsConstructor;
import orderservice.dto.kafka.OrderStatusKafka;
import orderservice.entities.OrderStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final OrderManager orderManager;


    @Transactional
    @KafkaListener(topics = "${topic.order.status}", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderStatusUpdate(OrderStatusKafka orderStatusKafka) {
        OrderStatus status = null;
        if ("Completed".equalsIgnoreCase(orderStatusKafka.getActionType())) {
            status = OrderStatus.PROCESSING;
        } else if ("Failed".equalsIgnoreCase(orderStatusKafka.getActionType())) {
            status = OrderStatus.PENDING;
        } else if ("Refunded".equalsIgnoreCase(orderStatusKafka.getActionType())) {
            status = OrderStatus.REFUNDED;
        }

        if (status == null) {
            return;
        }

        orderManager.changeOrderStatus(orderStatusKafka.getOrderId(), status);
    }
}
