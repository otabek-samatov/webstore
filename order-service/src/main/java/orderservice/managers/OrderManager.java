package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.InventoryDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.exceptions.NotEnoughStockException;
import orderservice.mappers.AddressMapper;
import orderservice.mappers.OrderItemMapper;
import orderservice.mappers.OrderMapper;
import orderservice.repositories.OrderItemRepository;
import orderservice.repositories.OrderRepository;
import orderservice.validators.BaseValidator;
import orderservice.validators.OrderItemValidator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderManager {

    private final OrderMapper orderMapper;
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

        baseValidator.validate(orderDto);
        orderItemValidator.validate(orderDto.getOrderItems());

        Order newOrder = new Order();
        newOrder.setOrderStatus(OrderStatus.NEW);

        List<OrderItemDto> orderItems = orderDto.getOrderItems();
        for (OrderItemDto orderItemDto : orderItems) {
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            newOrder.addItem(orderItem);
            reserveStock(orderItemDto);
        }

        BigDecimal shippingCost = getShippingCost();


        newOrder.setShippingCost(shippingCost);
        newOrder.setOrderAddress(addressMapper.toEntity(orderDto.getOrderAddress()));
        newOrder.setCustomerId(orderDto.getCustomerId());

        orderRepository.save(newOrder);

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
        if (!oldOrderStatus.getNextPossibleStatuses().contains(newOrderStatus)) {
            throw new IllegalArgumentException("order = " + orderId + " cannot be changed to " + newOrderStatus);

        }

        order.setOrderStatus(newOrderStatus);

        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REFUNDED) {
            kafkaProducerService.sendStockStatus("release", orderItemMapper.toDto(order.getItems()));
        } else if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            kafkaProducerService.sendStockStatus("commit", orderItemMapper.toDto(order.getItems()));
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
        order.removeItem(item);
        orderRepository.save(order);

        kafkaProducerService.sendStockStatus("release", orderItemMapper.toDto(item));
    }

    @Transactional
    public void addItemsToOrder(Long orderID, List<OrderItemDto> orderItemDtos) {
        if (orderID == null) {
            throw new IllegalArgumentException("orderID is null");
        }

        orderItemValidator.validate(orderItemDtos);

        Order order = orderRepository.findByIdAndOrderStatus(orderID, OrderStatus.NEW).orElseThrow(() -> new EntityNotFoundException("Order ID = " + orderID + " not found"));

        for (OrderItemDto orderItemDto : orderItemDtos) {
            Long itemID = orderItemRepository.findIdByOrderIdAndProductSKU(orderID, orderItemDto.getProductSKU());
            if (itemID != null) {
                throw new IllegalArgumentException("Product = " + orderItemDto.getProductSKU() + " already exists in Order");
            }

            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            order.addItem(orderItem);
            reserveStock(orderItemDto);
        }

        orderRepository.save(order);
    }

    private void reserveStock(OrderItemDto dto) {
        InventoryDto inventoryDto = new InventoryDto();
        inventoryDto.setProductSKU(dto.getProductSKU());
        inventoryDto.setReservedStock(dto.getQuantity());

        restClient.post()
                .uri("http://inventory-service/v1/inventory/reserve-stock")
                .body(inventoryDto)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // inventory uses 400 for NotEnoughStock; treat 4xx as a domain error
                    throw new NotEnoughStockException(dto.getProductSKU());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new IllegalStateException(
                            "inventory-service failed for SKU " + dto.getProductSKU()
                                    + ": " + res.getStatusCode());
                }).toBodilessEntity();
    }


}
