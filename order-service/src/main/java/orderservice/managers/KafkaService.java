package orderservice.managers;

import lombok.RequiredArgsConstructor;
import orderservice.dto.OrderDto;
import orderservice.dto.kafka.StockStatusKafka;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@RequiredArgsConstructor
@Service
public class KafkaService {
    private final KafkaTemplate<String, StockStatusKafka> kafkaTemplate;
    @Value("${stock.status.topic}")
    private String stockStatusTopic;

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, OrderDto orderDto) {

        if (CollectionUtils.isEmpty(orderDto.getOrderItems())) {
            return;
        }

        StockStatusKafka event = new StockStatusKafka();
        event.setOrderItems(orderDto.getOrderItems());
        event.setActionType(actionType);
        kafkaTemplate.send(stockStatusTopic, String.valueOf(orderDto.getUserId()), event);
    }
}
