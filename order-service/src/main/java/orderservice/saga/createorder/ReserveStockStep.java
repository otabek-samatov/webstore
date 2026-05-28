package orderservice.saga.createorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.InventoryDto;
import orderservice.dto.OrderItemDto;
import orderservice.exceptions.NotEnoughStockException;
import orderservice.outbox.OutboxPublisher;
import orderservice.saga.SagaContext;
import orderservice.saga.SagaStep;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga step 2: reserves stock against inventory-service. If a later step
 * fails, {@link #compensate(SagaContext)} publishes a {@code release} event
 * via the outbox so the inventory side is restored.
 * <p>
 * Compensation uses the saga id as the outbox aggregateId because the
 * order may not have been persisted yet by the time we compensate.
 */
@Slf4j
@RequiredArgsConstructor
class ReserveStockStep implements SagaStep {

    private static final String AGGREGATE_TYPE = "CreateOrderSaga";
    private static final String RELEASE_EVENT = "release";

    private final RestClient restClient;
    private final OutboxPublisher outboxPublisher;
    private final TransactionTemplate transactionTemplate;
    private final String stockStatusTopic;

    @Override
    public String name() {
        return "reserve-stock";
    }

    @Override
    public void execute(SagaContext context) {
        CreateOrderDto orderDto = context.get(CreateOrderSaga.CTX_ORDER_DTO, CreateOrderDto.class);
        List<OrderItemDto> items = orderDto.getOrderItems();
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        List<InventoryDto> invList = new ArrayList<>(items.size());
        for (OrderItemDto dto : items) {
            InventoryDto inv = new InventoryDto();
            inv.setProductSKU(dto.getProductSKU());
            inv.setReservedStock(dto.getQuantity());
            invList.add(inv);
        }

        restClient.post()
                .uri("http://inventory-service/v1/inventory/reserve-stock")
                .body(invList)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("Reserve stock rejected status={} body={}", res.getStatusCode(), body);
                    throw new NotEnoughStockException(body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("inventory-service 5xx for status={}", res.getStatusCode());
                    throw new IllegalStateException("inventory-service failed : " + res.getStatusCode());
                })
                .toBodilessEntity();

        context.put(CreateOrderSaga.CTX_RESERVED_ITEMS, items);
        log.info("Saga step reserve-stock reserved {} items sagaId={}", items.size(), context.getSagaId());
    }

    @Override
    public void compensate(SagaContext context) {
        List<OrderItemDto> reserved = readReservedItems(context);
        if (CollectionUtils.isEmpty(reserved)) {
            return;
        }

        String aggregateId = String.valueOf(context.getSagaId());

        transactionTemplate.executeWithoutResult(status ->
                outboxPublisher.publish(AGGREGATE_TYPE, aggregateId, RELEASE_EVENT, stockStatusTopic, reserved)
        );

        log.info("Saga compensation reserve-stock released {} items sagaId={}",
                reserved.size(), context.getSagaId());
    }

    @SuppressWarnings("unchecked")
    private List<OrderItemDto> readReservedItems(SagaContext context) {
        Object raw = context.get(CreateOrderSaga.CTX_RESERVED_ITEMS, Object.class);
        if (raw == null) {
            return List.of();
        }
        return (List<OrderItemDto>) raw;
    }
}
