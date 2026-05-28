package orderservice.repositories;

import jakarta.persistence.LockModeType;
import orderservice.entities.Order;
import orderservice.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o join fetch o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o FROM Order o join fetch o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o.id FROM Order o WHERE o.orderStatus = :status AND o.createdAt < :cutoff")
    List<Long> findIdsByStatusAndCreatedBefore(@Param("status") OrderStatus status,
                                               @Param("cutoff") Instant cutoff);

    List<Order> findByCustomerId(Long userId);

}

