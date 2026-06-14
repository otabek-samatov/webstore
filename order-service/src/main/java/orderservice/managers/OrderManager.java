package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.client.PaymentClient;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.InventoryDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.exceptions.NotEnoughStockException;
import orderservice.mappers.OrderItemMapper;
import orderservice.outbox.OutboxPublisher;
import orderservice.repositories.OrderItemRepository;
import orderservice.repositories.OrderRepository;
import orderservice.saga.createorder.CreateOrderSaga;
import orderservice.validators.OrderItemValidator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderManager {

    private final OrderItemMapper orderItemMapper;
    private final OrderRepository orderRepository;
    private final OrderItemValidator orderItemValidator;
    private final RestClient restClient;
    private final OrderItemRepository orderItemRepository;
    private final OutboxPublisher outboxPublisher;
    private final CreateOrderSaga createOrderSaga;
    private final PaymentClient paymentClient;

    public Order createOrder(CreateOrderDto orderDto) {
        return createOrderSaga.execute(orderDto);
    }

    /**
     * Re-attempts payment for an order left in {@link OrderStatus#PAYMENT_FAILED}
     * by a declined payment during creation. The reserved stock is still held, so
     * no re-reservation is needed — this only re-charges the customer.
     * <p>
     * On a successful charge the order transition to {@code COMPLETED} happens
     * <strong>asynchronously</strong>: payment-service publishes an
     * {@code PaymentStatusMessage} event that {@code KafkaConsumerService} turns into
     * {@code PAYMENT_FAILED → COMPLETED}. The returned order therefore still reads
     * {@code PAYMENT_FAILED} until that event is processed. A repeated decline
     * leaves the order {@code PAYMENT_FAILED} so the customer can try again (or
     * cancel via {@code changeOrderStatus}).
     */
    public Order retryPayment(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order Id = " + orderId + " not found"));

        if (order.getOrderStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new IllegalArgumentException(
                    "Order " + orderId + " is not awaiting payment retry (status=" + order.getOrderStatus() + ")");
        }

        paymentClient.charge(order);

        log.info("Payment retry submitted orderId={} (COMPLETED transition is driven async via Kafka)", orderId);
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        return orderRepository.findById(orderId).orElseThrow(() ->
                new EntityNotFoundException("Order id = " + orderId + " not found")
        );
    }

    @Transactional(readOnly = true)
    public List<Order> getOrderByCustomerId(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is null");
        }

        return orderRepository.findByCustomerId(customerId);
    }

    @Transactional
    public void changeOrderStatus(Long orderId, OrderStatus newOrderStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        if (newOrderStatus == null) {
            throw new IllegalArgumentException("newOrderStatus is null");
        }

        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new EntityNotFoundException("Order Id = " + orderId + " not found"));
        OrderStatus oldOrderStatus = order.getOrderStatus();
        if (oldOrderStatus == newOrderStatus) {
            log.info("Order status unchanged, skipping orderId={} status={}", orderId, oldOrderStatus);
            return;
        }

        order.setOrderStatus(newOrderStatus);
        log.info("Changed order status orderId={} from={} to={}",
                orderId, oldOrderStatus, newOrderStatus);

        String actionType = null;
        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REFUNDED) {
            actionType = "release";
        } else if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            actionType = "commit";
        }

        if (StringUtils.hasText(actionType)) {
            outboxPublisher.publishOrderItemEvent(orderId, actionType, orderItemMapper.toDto(order.getItems()));
        }

        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getItemsByOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        return orderItemRepository.findAllByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public OrderItem getOrderItem(Long orderId, Long itemId) {
        if (itemId == null || orderId == null) {
            throw new IllegalArgumentException("orderId or itemId cannot be null");
        }

        return orderItemRepository.findByIdAndOrderId(itemId, orderId).orElseThrow(() -> new EntityNotFoundException("Order Item Id = " + itemId + " not found"));
    }

    @Transactional
    public void removeOrderItem(Long orderId, Long orderItemId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        if (orderItemId == null) {
            throw new IllegalArgumentException("orderItemId is null");
        }

        OrderItem item = orderItemRepository.findByIdAndOrderId(orderItemId, orderId).orElseThrow(() -> new EntityNotFoundException("Order Item Id = " + orderItemId + " not found"));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order Id = " + orderId + " not found"));

        order.removeItem(item);
        orderRepository.save(order);

        log.info("Removed order item itemId={} orderId={} sku={}",
                orderItemId, order.getId(), item.getProductSKU());

        outboxPublisher.publishOrderItemEvent(orderId, "release", List.of(orderItemMapper.toDto(item)));

    }

    @Transactional
    public void addItemsToOrder(Long orderId, List<OrderItemDto> orderItemDtos) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        orderItemValidator.validate(orderItemDtos);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order Id = " + orderId + " not found"));

        addItems(order, orderItemDtos);

        orderRepository.save(order);

        log.info("Items added to order orderId={} count={}", orderId, orderItemDtos.size());
    }

    private void addItems(Order order, List<OrderItemDto> orderItems) {
        if (CollectionUtils.isEmpty(orderItems)) {
            return;
        }

        Map<String, BigDecimal> prices = getPrices(orderItems);
        for (OrderItemDto orderItemDto : orderItems) {
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            orderItem.setUnitPrice(prices.getOrDefault(orderItem.getProductSKU(), BigDecimal.ZERO));
            order.addItem(orderItem);
        }

        reserveStock(orderItems);
    }

    private void reserveStock(List<OrderItemDto> dtos) {
        if (CollectionUtils.isEmpty(dtos)) {
            return;
        }

        List<InventoryDto> invList = new ArrayList<>(dtos.size());
        for (OrderItemDto dto : dtos) {
            InventoryDto inventoryDto = new InventoryDto();
            inventoryDto.setProductSKU(dto.getProductSKU());
            inventoryDto.setReservedStock(dto.getQuantity());
            invList.add(inventoryDto);
            log.debug("Reserving stock sku={} quantity={}", dto.getProductSKU(), dto.getQuantity());
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
                }).toBodilessEntity();
    }

    private Map<String, BigDecimal> getPrices(List<OrderItemDto> dtos) {
        if (CollectionUtils.isEmpty(dtos)) {
            return Collections.emptyMap();
        }

        List<String> productList = new ArrayList<>(dtos.size());
        for (OrderItemDto dto : dtos) {
            productList.add(dto.getProductSKU());
            log.debug("Getting prices stock sku={} quantity={}", dto.getProductSKU(), dto.getQuantity());
        }

        List<InventoryDto> prices = restClient.post()
                .uri("http://inventory-service/v1/inventory/prices")
                .body(productList)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("Price lookup rejected status={} body={}", res.getStatusCode(), body);
                    throw new IllegalArgumentException("Inventory rejected price request: " + body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("inventory-service 5xx for prices status={}", res.getStatusCode());
                    throw new IllegalStateException("inventory-service price lookup failed: " + res.getStatusCode());
                }).body(new ParameterizedTypeReference<>() {
                });

        if (CollectionUtils.isEmpty(prices)) {
            return Collections.emptyMap();
        }

        return prices.stream()
                .collect(Collectors.toMap(
                        InventoryDto::getProductSKU,
                        InventoryDto::getSellPrice
                ));
    }


}
