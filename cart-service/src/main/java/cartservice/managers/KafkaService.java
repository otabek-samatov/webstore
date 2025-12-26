package cartservice.managers;

import cartservice.dto.CartItemDto;
import cartservice.dto.kafka.StockStatusKafka;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class KafkaService {
    private final KafkaTemplate<String, StockStatusKafka> kafkaTemplate;
    @Value("${stock.status.topic}")
    private String stockStatusTopic;

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, long userID, CartItemDto cartItemDto) {
        sendStockStatus(actionType, userID, List.of(cartItemDto));
    }

    @Transactional("kafkaTransactionManager")
    public void sendStockStatus(String actionType, long userID, Collection<CartItemDto> cartItemDtos) {

        if (CollectionUtils.isEmpty(cartItemDtos)) {
            return;
        }

        StockStatusKafka event = new StockStatusKafka();
        event.addItems(cartItemDtos);
        event.setActionType(actionType);
        kafkaTemplate.send(stockStatusTopic, String.valueOf(userID), event);
    }
}
