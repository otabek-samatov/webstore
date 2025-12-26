package paymentservice.managers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import paymentservice.dto.kafka.OrderStatusKafka;

@RequiredArgsConstructor
@Service
public class KafkaService {
    private final KafkaTemplate<String, OrderStatusKafka> kafkaTemplate;
    @Value("${stock.status.topic}")
    private String stockStatusTopic;

    @Transactional("kafkaTransactionManager")
    public void sendOrderStatus(long userID, String actionType, Long orderId) {

        if (orderId == null || orderId <= 0) {
            return;
        }

        OrderStatusKafka event = new OrderStatusKafka();
        event.setOrderId(orderId);
        event.setActionType(actionType);
        kafkaTemplate.send(stockStatusTopic, String.valueOf(userID), event);
    }
}
