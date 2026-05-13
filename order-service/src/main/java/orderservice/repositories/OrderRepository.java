package orderservice.repositories;

import orderservice.entities.Order;
import orderservice.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {


    List<Order> findByCustomerId(Long userId);

    Optional<Order> findByIdAndOrderStatus(Long id, OrderStatus orderStatus);
}

