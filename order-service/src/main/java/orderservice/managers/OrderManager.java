package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.InventoryDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.exceptions.NotEnoughStockException;
import orderservice.mappers.AddressMapper;
import orderservice.mappers.OrderItemMapper;
import orderservice.repositories.OrderItemRepository;
import orderservice.repositories.OrderRepository;
import orderservice.validators.BaseValidator;
import orderservice.validators.OrderItemValidator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderManager {

    private final OrderItemMapper orderItemMapper;
    private final OrderRepository orderRepository;
    private final KafkaProducerService kafkaProducerService;
    private final OrderItemValidator orderItemValidator;
    private final BaseValidator baseValidator;
    private final AddressMapper addressMapper;
    private final RestClient restClient;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public Order createOrder(CreateOrderDto orderDto) {
        if (orderDto == null) {
            throw new IllegalArgumentException("orderDto is null");
        }

        log.info("Creating order customerId={} itemCount={}",
                orderDto.getCustomerId(),
                orderDto.getOrderItems() == null ? 0 : orderDto.getOrderItems().size());

        baseValidator.validate(orderDto);
        orderItemValidator.validate(orderDto.getOrderItems());

        Order newOrder = new Order();
        newOrder.setOrderStatus(OrderStatus.NEW);

        List<OrderItemDto> orderItems = orderDto.getOrderItems();
        for (OrderItemDto orderItemDto : orderItems) {
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            newOrder.addItem(orderItem);
        }

        reserveStock(orderItems);

        BigDecimal shippingCost = getShippingCost();

        newOrder.setShippingCost(shippingCost);
        newOrder.setOrderAddress(addressMapper.toEntity(orderDto.getOrderAddress()));
        newOrder.setCustomerId(orderDto.getCustomerId());

        orderRepository.save(newOrder);

        log.info("Order created orderId={} customerId={} itemCount={}",
                newOrder.getId(), newOrder.getCustomerId(), orderItems.size());

        return newOrder;
    }

    private BigDecimal getShippingCost() {
        return BigDecimal.valueOf(100);
    }

    public Order getOrderById(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        return orderRepository.findById(orderId).orElseThrow(() ->
                new EntityNotFoundException("Order id = " + orderId + " not found")
        );
    }

    @Transactional(readOnly = true)
    public List<Order> getOrderByCustomerId(Long customerID) {
        if (customerID == null) {
            throw new IllegalArgumentException("customerID is null");
        }

        return orderRepository.findByCustomerId(customerID);
    }

    @Transactional
    public void changeOrderStatus(Long orderId, OrderStatus newOrderStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        if (newOrderStatus == null) {
            throw new IllegalArgumentException("newOrderStatus is null");
        }

        Order order = getOrderById(orderId);

        OrderStatus oldOrderStatus = order.getOrderStatus();
        if (!oldOrderStatus.isAcceptableNextStatus(newOrderStatus)) {
            log.error("Rejected status transition orderId={} from={} to={}",
                    orderId, oldOrderStatus, newOrderStatus);
            throw new IllegalArgumentException("order = " + orderId + " cannot be changed to " + newOrderStatus);
        }

        log.info("Changing order status orderId={} from={} to={}",
                orderId, oldOrderStatus, newOrderStatus);

        order.setOrderStatus(newOrderStatus);

        String actionType = null;
        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REFUNDED) {
            actionType = "release";
        } else if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            actionType = "commit";
        }

        if (StringUtils.hasText(actionType)) {
            kafkaProducerService.sendStockStatus(actionType, orderId, orderItemMapper.toDto(order.getItems()));
        }

        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getItemsByOrderID(Long orderID) {
        if (orderID == null) {
            throw new IllegalArgumentException("orderID is null");
        }

        return orderItemRepository.findAllByOrderId(orderID);
    }

    @Transactional(readOnly = true)
    public OrderItem getOrderItem(Long orderItemId) {
        if (orderItemId == null) {
            throw new IllegalArgumentException("orderItemId is null");
        }

        return orderItemRepository.findById(orderItemId).orElseThrow(() -> new EntityNotFoundException("Order Item ID = " + orderItemId + " not found"));
    }

    @Transactional
    public void removeOrderItem(Long orderItemId) {
        if (orderItemId == null) {
            throw new IllegalArgumentException("orderItemId is null");
        }

        OrderItem item = orderItemRepository.findById(orderItemId).orElseThrow(() -> new EntityNotFoundException("Order Item ID = " + orderItemId + " not found"));
        Order order = item.getOrder();

        if (order.getOrderStatus() != OrderStatus.NEW) {
            throw new IllegalArgumentException(
                    "Cannot remove items from order " + order.getId() + " in status " + order.getOrderStatus());
        }

        order.removeItem(item);
        orderRepository.save(order);

        log.info("Removed order item itemId={} orderId={} sku={}",
                orderItemId, order.getId(), item.getProductSKU());

        kafkaProducerService.sendStockStatus("release", order.getId(), List.of(orderItemMapper.toDto(item)));
    }

    @Transactional
    public void addItemsToOrder(Long orderID, List<OrderItemDto> orderItemDtos) {
        if (orderID == null) {
            throw new IllegalArgumentException("orderID is null");
        }

        orderItemValidator.validate(orderItemDtos);

        log.info("Adding items to order orderId={} count={}", orderID, orderItemDtos.size());

        Order order = orderRepository.findByIdAndOrderStatus(orderID, OrderStatus.NEW).orElseThrow(() -> new EntityNotFoundException("Order ID = " + orderID + " not found"));

        for (OrderItemDto orderItemDto : orderItemDtos) {
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            order.addItem(orderItem);
        }

        reserveStock(orderItemDtos);

        orderRepository.save(order);

        log.info("Items added to order orderId={} count={}", orderID, orderItemDtos.size());
    }

    private void reserveStock(List<OrderItemDto> dtos) {
        List<InventoryDto> invList = new LinkedList<>();
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


}
