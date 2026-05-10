package orderservice.repositories;

import orderservice.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select sum(item.quantity * item.unitPrice) from OrderItem item where item.order.orderStatus = 'IN_PROGRESS' and item.order.userId = :userId")
    BigDecimal getSumByUserID(@Param("userId") Long userId);

    @Query("select item.id from OrderItem item where item.order.orderStatus = 'IN_PROGRESS' and item.order.userId = :userId and item.productSKU = :productSKU")
    Long getCartItemID(@Param("userId") Long userId, @Param("productSKU") String productSKU);


    @Query("select item from OrderItem item where item.order.orderStatus = 'IN_PROGRESS' and item.order.userId = :userId")
    List<OrderItem> getItemsByUserID(@Param("userId") Long userId);

    @Modifying
    @Query("Update OrderItem item  set item.quantity = :quantity where item.id = :itemId")
    void updateQuantity(@Param("itemId") Long itemId, Long quantity);

    @Query("select item from OrderItem item where item.order.id = :cartId")
    List<OrderItem> getItemsByCartID(Long cartId);
}
