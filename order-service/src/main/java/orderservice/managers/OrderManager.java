package orderservice.managers;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderManager {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(@Valid OrderDto orderDto) {

        Order newOrder = orderMapper.toEntity(orderDto);
        newOrder.setOrderStatus(OrderStatus.CREATED);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CartItemDto> cartItems = getCartItemDtos(orderDto.getCartId());
        for (CartItemDto cartItem : cartItems) {
            OrderItemDto orderItemDto = OrderItemDto.createFromCartItem(cartItem);
            OrderItem orderItem = orderItemMapper.toEntity(orderItemDto);
            newOrder.addItem(orderItem);
            totalAmount = totalAmount.add(orderItem.getUnitPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity())));
        }

        newOrder.setTaxAmount(getTaxAmount(totalAmount));
        newOrder.setTotalAmount(totalAmount);
        newOrder.setShippingCost(getShippingCost(newOrder.getOrderAddress()));

        orderRepository.save(newOrder);

        return newOrder;
    }

    private List<CartItemDto> getCartItemDtos(Long cartID) {
        throw new UnsupportedOperationException("getCartItemDtos is not supported yet");
    }

    private BigDecimal getTaxAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(0.2));
    }

    private BigDecimal getShippingCost(Address address) {
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
        orderRepository.save(order);
    }

}
