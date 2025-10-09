package orderservice.controllers;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import orderservice.dto.OrderDto;
import orderservice.entities.Order;
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
    public ResponseEntity<Void> createOrder(@Valid @RequestBody OrderDto dto) {
        manager.createOrder(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{orderID}")
    public ResponseEntity<OrderDto> getByOrderID(@PathVariable Long orderID) {
        Order order = manager.getOrderById(orderID);
        OrderDto orderDto = orderMapper.toDto(order);
        orderDto.setOrderItems(itemMapper.toDto(order.getItems()));

        return ResponseEntity.ok(orderDto);
    }

    @GetMapping("/user/{userID}")
    public ResponseEntity<List<OrderDto>> getUserOrders(@PathVariable Long userID) {
        List<Order> orders = manager.getOrderByUserId(userID);
        List<OrderDto> dtoList = new ArrayList<>();
        for (Order order : orders) {
            OrderDto dto = orderMapper.toDto(order);
            dto.setOrderItems(itemMapper.toDto(order.getItems()));
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

}
