package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.OrderDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.mappers.AddressMapper;
import orderservice.mappers.OrderItemMapper;
import orderservice.mappers.OrderMapper;
import orderservice.repositories.OrderRepository;
import orderservice.validators.BaseValidator;
import orderservice.validators.OrderItemValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        for (OrderItemDto orderItem : orderItems) {
            totalAmount = totalAmount.add(orderItem.getItemPrice());
            OrderItem orderItemEntity = orderItemMapper.toEntity(orderItem);
            newOrder.addItem(orderItemEntity);
        }

        BigDecimal taxAmount = getTaxAmount(totalAmount);
        BigDecimal shippingCost = getShippingCost();

        totalAmount = totalAmount.add(taxAmount);
        totalAmount = totalAmount.add(shippingCost);
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        newOrder.setTaxAmount(taxAmount);
        newOrder.setTotalAmount(totalAmount);
        newOrder.setShippingCost(shippingCost);
        newOrder.setOrderAddress(addressMapper.toEntity(orderDto.getOrderAddress()));
        newOrder.setCustomerId(orderDto.getCustomerId());

        orderRepository.save(newOrder);

        return newOrder;
    }

    private BigDecimal getTaxAmount(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
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

    public List<Order> getOrderByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        return orderRepository.findByUserId(userId);
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


}
