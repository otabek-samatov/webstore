package orderservice.saga.createorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.mappers.AddressMapper;
import orderservice.mappers.OrderItemMapper;
import orderservice.repositories.OrderRepository;
import orderservice.saga.SagaContext;
import orderservice.saga.SagaStep;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Saga step 3: builds the {@link Order} aggregate from the DTO, applies prices
 * fetched by {@link PriceItemsStep}, and persists it.
 * <p>
 * Compensation cancels the persisted order ({@code NEW → CANCELLED}) directly
 * via the repository. It does <strong>not</strong> emit a stock-release event —
 * that is owned by {@link ReserveStockStep#compensate} so the stock is released
 * exactly once.
 */
@Slf4j
@RequiredArgsConstructor
class PersistOrderStep implements SagaStep {

    private static final BigDecimal SHIPPING_COST = BigDecimal.valueOf(100);

    private final OrderRepository orderRepository;
    private final OrderItemMapper orderItemMapper;
    private final AddressMapper addressMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String name() {
        return "persist-order";
    }

    @Override
    public void execute(SagaContext context) {
        CreateOrderDto orderDto = context.get(CreateOrderSaga.CTX_ORDER_DTO, CreateOrderDto.class);
        Map<String, BigDecimal> prices = readPrices(context);

        Order saved = transactionTemplate.execute(status -> {
            Order order = new Order();
            order.setOrderStatus(OrderStatus.NEW);
            order.setShippingCost(SHIPPING_COST);
            order.setOrderAddress(addressMapper.toEntity(orderDto.getOrderAddress()));
            order.setCustomerId(orderDto.getCustomerId());

            List<OrderItemDto> itemDtos = orderDto.getOrderItems();
            if (itemDtos != null) {
                for (OrderItemDto itemDto : itemDtos) {
                    OrderItem item = orderItemMapper.toEntity(itemDto);
                    item.setUnitPrice(prices.getOrDefault(item.getProductSKU(), BigDecimal.ZERO));
                    order.addItem(item);
                }
            }

            return orderRepository.save(order);
        });

        context.put(CreateOrderSaga.CTX_ORDER, saved);
        log.info("Saga step persist-order saved orderId={} sagaId={}",
                saved.getId(), context.getSagaId());
    }

    @Override
    public void compensate(SagaContext context) {
        Order saved = context.get(CreateOrderSaga.CTX_ORDER, Order.class);
        if (saved == null || saved.getId() == null) {
            return;
        }

        Long orderId = saved.getId();
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findById(orderId).ifPresent(order -> {
                    order.setOrderStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                }));

        log.info("Saga compensation persist-order cancelled orderId={} sagaId={}",
                orderId, context.getSagaId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> readPrices(SagaContext context) {
        Object raw = context.get(CreateOrderSaga.CTX_PRICES, Object.class);
        if (raw == null) {
            return Map.of();
        }
        return (Map<String, BigDecimal>) raw;
    }
}
