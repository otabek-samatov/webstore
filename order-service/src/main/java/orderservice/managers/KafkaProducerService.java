package orderservice.managers;

import lombok.RequiredArgsConstructor;
import orderservice.dto.OrderDto;
import orderservice.dto.kafka.StockStatusKafka;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class KafkaProducerService {
    private final KafkaTemplate<String, StockStatusKafka> kafkaTemplate;
    @Value("${stock.status.topic}")
    private String stockStatusTopic;

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, OrderDto orderDto) {


        StockStatusKafka event = new StockStatusKafka();
        event.setActionType(actionType);
        kafkaTemplate.send(stockStatusTopic, "order-service-" + orderDto.getId(), event);
    }
}
