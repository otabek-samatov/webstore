package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.OrderDto;
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
            throw new IllegalArgumentException("orderDTO is null");
        }

        baseValidator.validate(orderDto);
        orderItemValidator.validate(orderDto.getOrderItems());

        Order newOrder = new Order();
        newOrder.setOrderStatus(OrderStatus.NEW);

        BigDecimal totalAmount = BigDecimal.ZERO;

        List<OrderItemDto> orderItems = orderDto.getOrderItems();
        for (OrderItemDto orderItemDto : orderItems) {
            checkQuantity(orderItemDto);

            totalAmount = totalAmount.add(orderItemDto.getItemPrice());
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            newOrder.addItem(orderItem);
            reserveStock(orderItemDto);
        }

        BigDecimal taxAmount = getTaxAmount(totalAmount);
        BigDecimal shippingCost = getShippingCost();

        totalAmount = totalAmount.add(taxAmount);
        totalAmount = totalAmount.add(shippingCost);

        newOrder.setTaxAmount(taxAmount);
        newOrder.setTotalAmount(totalAmount);
        newOrder.setShippingCost(shippingCost);
        newOrder.setOrderAddress(addressMapper.toEntity(orderDto.getOrderAddress()));
        newOrder.setCustomerId(orderDto.getCustomerId());

        orderRepository.save(newOrder);

        return newOrder;
    }

    private BigDecimal getTaxAmount(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.20"));
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

    public List<Order> getOrderByCustomerId(Long customerID) {
        if (customerID == null) {
            throw new IllegalArgumentException("customerID is null");
        }

        return orderRepository.findByCustomerId(customerID);
    }

    @Transactional
    public void changeOrderStatus(Long orderId, OrderStatus orderStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is null");
        }

        if (orderStatus == null) {
            throw new IllegalArgumentException("orderStatus is null");
        }

        Order order = getOrderById(orderId);

        order.setOrderStatus(orderStatus);

        OrderDto orderDto = orderMapper.toDto(order);

        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REFUNDED) {
            kafkaProducerService.sendStockStatus("release", orderDto);
        } else if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            kafkaProducerService.sendStockStatus("commit", orderDto);
        }

        orderRepository.save(order);
    }

    public List<OrderItem> getItemsByOrderID(Long orderID) {
        if (orderID == null) {
            throw new IllegalArgumentException("cartID is null");
        }

        return orderItemRepository.findAllByOrderId(orderID);
    }

    public OrderItem getOrderItem(Long orderItemId) {
        if (orderItemId == null) {
            throw new IllegalArgumentException("orderItemId is null");
        }

        return orderItemRepository.findById(orderItemId).orElseThrow(() -> new EntityNotFoundException("Order Item with = " + orderItemId + " not found"));
    }

    @Transactional
    public void removeOrderItem(Long orderItemId) {
        if (orderItemId == null) {
            throw new IllegalArgumentException("orderItemId is null");
        }

        OrderItem item = orderItemRepository.findById(orderItemId).orElseThrow(() -> new EntityNotFoundException("Order Item with = " + orderItemId + " not found"));
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

        Order order = orderRepository.findByIdAndOrderStatus(orderID, OrderStatus.NEW);

        if (order == null) {
            throw new EntityNotFoundException("New Order with id = " + orderID + " not found");
        }

        for (OrderItemDto orderItemDto : orderItemDtos) {
            Long itemID = orderItemRepository.findIdByOrderIdAndProductSKU(orderID, orderItemDto.getProductSKU());
            if (itemID != null) {
                throw new IllegalArgumentException("Product = " + orderItemDto.getProductSKU() + " already exists in cart");
            }

            checkQuantity(orderItemDto);

            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            order.addItem(orderItem);
            reserveStock(orderItemDto);
        }

        orderRepository.save(order);
    }


    private void checkQuantity(OrderItemDto dto) {
        Long availableCount = restClient.get()
                .uri("http://inventory-service/v1/inventories/available-count/{sku}", dto.getProductSKU())
                .retrieve()
                .body(Long.class);

        if (availableCount == null || availableCount < dto.getQuantity()) {
            throw new NotEnoughStockException(dto.getProductSKU());
        }
    }

    private void reserveStock(OrderItemDto dto) {
        restClient.post()
                .uri("http://inventory-service/v1/inventories/reserve-stock", dto)
                .retrieve()
                .toBodilessEntity();
    }


}
