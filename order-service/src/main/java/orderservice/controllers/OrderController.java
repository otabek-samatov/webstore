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

import java.util.ArrayList;
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

    @GetMapping("/{orderID}")
    public ResponseEntity<OrderDto> getByOrderID(@PathVariable Long orderID) {
        Order order = manager.getOrderById(orderID);
        OrderDto orderDto = orderMapper.toDto(order);

        return ResponseEntity.ok(orderDto);
    }

    @GetMapping("/user/{userID}")
    public ResponseEntity<List<OrderDto>> getUserOrders(@PathVariable Long userID) {
        List<Order> orders = manager.getOrderByCustomerId(userID);
        List<OrderDto> dtoList = new ArrayList<>();
        for (Order order : orders) {
            OrderDto dto = orderMapper.toDto(order);
            dtoList.add(dto);
        }
        return ResponseEntity.ok(dtoList);
    }

    @PutMapping("/{orderID}/{status}")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable Long orderID, @PathVariable OrderStatus status) {
        manager.changeOrderStatus(orderID, status);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{orderID}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderID) {
        manager.changeOrderStatus(orderID, OrderStatus.CANCELLED);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{orderID}/items")
    public ResponseEntity<List<OrderItemDto>> getItemsByOrderID(@PathVariable Long orderID) {
        List<OrderItem> items = manager.getItemsByOrderID(orderID);
        return ResponseEntity.ok(itemMapper.toDto(items));
    }

    @GetMapping("/items/{itemID}")
    public ResponseEntity<OrderItemDto> getItem(@PathVariable Long itemID) {
        OrderItem item = manager.getOrderItem(itemID);
        return ResponseEntity.ok(itemMapper.toDto(item));
    }

    @DeleteMapping("/items/{itemID}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemID) {
        manager.removeOrderItem(itemID);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderID}/items")
    public ResponseEntity<Void> addItems(@PathVariable Long orderID, @Valid @RequestBody List<OrderItemDto> items) {
        manager.addItemsToOrder(orderID, items);
        return ResponseEntity.noContent().build();
    }

}
