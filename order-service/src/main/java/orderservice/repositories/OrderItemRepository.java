package orderservice.repositories;

import orderservice.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Modifying
    @Query("Update OrderItem item  set item.quantity = :quantity where item.id = :itemId")
    void updateQuantity(@Param("itemId") Long itemId, Long quantity);


    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> orderId(Long orderId);

    Long findIdByOrderIdAndProductSKU(Long orderId, String productSKU);

}
