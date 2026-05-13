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
    public void sendStockStatus(String actionType, List<OrderItemDto> dtos) {
        dtos.forEach(dto -> sendStockStatus(actionType, dto));
    }

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, OrderItemDto dto) {

        StockStatusKafka event = new StockStatusKafka();
        event.addItem(dto);
        event.setActionType(actionType);

        log.info("Publishing stock-status event topic={} actionType={} sku={} quantity={}",
                stockStatusTopic, actionType, dto.getProductSKU(), dto.getQuantity());

        kafkaTemplate.send(stockStatusTopic, "orderItem-" + dto.getProductSKU(), event);
    }
}
