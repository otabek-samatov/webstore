package cartservice.repositories;

import cartservice.entities.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select sum(item.quantity * item.unitPrice) from CartItem item where item.cart.status = 'IN_PROGRESS' and item.cart.userId = :userId")
    BigDecimal getSumByUserID(@Param("userId") Long userId);

    @Query("select item.id from CartItem item where item.cart.status = 'IN_PROGRESS' and item.cart.userId = :userId and item.productSKU = :productSKU")
    Long getCartItemID(@Param("userId") Long userId, @Param("productSKU") String productSKU);


    @Query("select item from CartItem item where item.cart.status = 'IN_PROGRESS' and item.cart.userId = :userId")
    List<CartItem> getItemsByUserID(@Param("userId") Long userId);

    @Modifying
    @Query("Update CartItem item  set item.quantity = :quantity where item.id = :itemId")
    void updateQuantity(@Param("itemId") Long itemId, Long quantity);


}
