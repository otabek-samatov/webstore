package orderservice.controllers;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import orderservice.dto.CreateOrderDto;
import orderservice.dto.OrderDto;
import orderservice.dto.OrderItemDto;
import orderservice.entities.Order;
import orderservice.entities.OrderItem;
import orderservice.entities.OrderStatus;
import orderservice.managers.OrderManager;
import orderservice.mappers.OrderItemMapper;
import orderservice.mappers.OrderMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/v1/orders")
public class OrderController {

    private final OrderManager manager;
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@Valid @RequestBody CreateOrderDto dto) {
        Order order = manager.createOrder(dto);
        OrderDto orderDto = orderMapper.toDto(order);
        return ResponseEntity.ok(orderDto);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getByOrderID(@PathVariable Long orderId) {
        Order order = manager.getOrderById(orderId);
        OrderDto orderDto = orderMapper.toDto(order);

        return ResponseEntity.ok(orderDto);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderDto>> getUserOrders(@PathVariable Long customerId) {
        List<Order> orders = manager.getOrderByCustomerId(customerId);
        return ResponseEntity.ok(orderMapper.toDto(orders));
    }

    @PutMapping("/{orderId}/{status}")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable Long orderId, @PathVariable OrderStatus status) {
        manager.changeOrderStatus(orderId, status);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/retry-payment")
    public ResponseEntity<OrderDto> retryPayment(@PathVariable Long orderId) {
        Order order = manager.retryPayment(orderId);
        return ResponseEntity.ok(orderMapper.toDto(order));
    }

    @GetMapping("/{orderId}/items")
    public ResponseEntity<List<OrderItemDto>> getItemsByOrderID(@PathVariable Long orderId) {
        List<OrderItem> items = manager.getItemsByOrderId(orderId);
        return ResponseEntity.ok(itemMapper.toDto(items));
    }

    @GetMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderItemDto> getItem(@PathVariable Long orderId, @PathVariable Long itemId) {
        OrderItem item = manager.getOrderItem(orderId, itemId);
        return ResponseEntity.ok(itemMapper.toDto(item));
    }

    @DeleteMapping("/{orderId}/items/{itemID}")
    public ResponseEntity<Void> removeItem(@PathVariable Long orderId, @PathVariable Long itemID) {
        manager.removeOrderItem(orderId, itemID);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<Void> addItems(@PathVariable Long orderId, @Valid @RequestBody List<OrderItemDto> items) {
        manager.addItemsToOrder(orderId, items);
        return ResponseEntity.noContent().build();
    }

}
