package orderservice.managers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.OrderItemDto;
import orderservice.dto.kafka.StockStatusKafka;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaProducerService {
    private final KafkaTemplate<String, StockStatusKafka> kafkaTemplate;
    @Value("${topic.stock.status}")
    private String stockStatusTopic;

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, long orderId, List<OrderItemDto> dtos) {

        StockStatusKafka event = new StockStatusKafka();
        event.addItems(dtos);
        event.setActionType(actionType);

        log.info("Publishing stock-status event topic={} actionType={} order id={}",
                stockStatusTopic, actionType, orderId);

        kafkaTemplate.send(stockStatusTopic, "order-" + orderId, event);
    }

}
