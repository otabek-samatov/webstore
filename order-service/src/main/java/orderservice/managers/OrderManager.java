package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import orderservice.dto.CartItemDto;
import orderservice.dto.OrderDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Address;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.mappers.OrderItemMapper;
import orderservice.mappers.OrderMapper;
import orderservice.repositories.OrderRepository;
import org.springframework.core.ParameterizedTypeReference;
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
    private final RestClient restClient;
    private final KafkaService kafkaService;

    @Transactional
    public Order createOrder(OrderDto orderDto) {
        if (orderDto == null) {
            throw new IllegalArgumentException("orderDTO is null");
        }

        List<CartItemDto> cartItems = getCartItemDtos(orderDto.getCartId());
        if (cartItems == null || cartItems.isEmpty()) {
            throw new EntityNotFoundException("CartItems not found for cart id = " + orderDto.getCartId());
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        Order newOrder = orderMapper.toEntity(orderDto);
        newOrder.setOrderStatus(OrderStatus.PENDING);

        for (CartItemDto cartItem : cartItems) {
            OrderItemDto orderItemDto = OrderItemDto.createFromCartItem(cartItem);
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            newOrder.addItem(orderItem);
            totalAmount = totalAmount.add(orderItem.getUnitPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity())));
        }

        BigDecimal taxAmount = getTaxAmount(totalAmount);
        BigDecimal shippingCost = getShippingCost(newOrder.getOrderAddress());

        totalAmount = totalAmount.add(taxAmount);
        totalAmount = totalAmount.add(shippingCost);

        newOrder.setTaxAmount(taxAmount);
        newOrder.setTotalAmount(totalAmount);
        newOrder.setShippingCost(shippingCost);

        orderRepository.save(newOrder);

        return newOrder;
    }

    private List<CartItemDto> getCartItemDtos(Long cartID) {
        return restClient.get()
                .uri("http://cart-service/v1/carts/cart/items/{cartID}", cartID)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private BigDecimal getTaxAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(0.2));
    }

    private BigDecimal getShippingCost(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("address not found");
        }

        if (address.getAddressLine() == null || address.getAddressLine().isBlank()) {
            throw new IllegalArgumentException("address line not found");
        }

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
        OrderStatus currentStatus = order.getOrderStatus();

        order.setOrderStatus(orderStatus);

        OrderDto orderDto = orderMapper.toDto(order);

        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.REFUNDED) {
            kafkaService.sendStockStatus("release", orderDto);
        } else if (order.getOrderStatus() == OrderStatus.DELIVERED) {
            kafkaService.sendStockStatus("commit", orderDto);
        }

        orderRepository.save(order);
    }


}
