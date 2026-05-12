package orderservice.managers;

import lombok.RequiredArgsConstructor;
import orderservice.dto.OrderItemDto;
import orderservice.dto.kafka.StockStatusKafka;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class KafkaProducerService {
    private final KafkaTemplate<String, StockStatusKafka> kafkaTemplate;
    @Value("${stock.status.topic}")
    private String stockStatusTopic;

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, List<OrderItemDto> dtos) {

        StockStatusKafka event = new StockStatusKafka();
        event.setActionType(actionType);
        event.addItems(dtos);

        kafkaTemplate.send(stockStatusTopic, "orderItem", event);
    }

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, OrderItemDto dto) {

        StockStatusKafka event = new StockStatusKafka();
        event.addItem(dto);
        event.setActionType(actionType);
        kafkaTemplate.send(stockStatusTopic, "orderItem", event);
    }
}
