package orderservice.controllers;


import lombok.RequiredArgsConstructor;
import orderservice.dto.OrderDto;
import orderservice.entities.Order;
import orderservice.entities.OrderStatus;
import orderservice.managers.OrderManager;
import orderservice.mappers.OrderItemMapper;
import orderservice.mappers.OrderMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/inventories/inventory")
public class OrderController {

    private final OrderManager manager;
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

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
        return ResponseEntity.ok(orderMapper.toDto(orders));
    }

    @PutMapping("/{orderID}/{status}")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable long orderID, @PathVariable OrderStatus status) {
        manager.changeOrderStatus(orderID, status);
        return ResponseEntity.noContent().build();
    }


}
