package cartservice.repositories;

import cartservice.entities.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("select c from Cart c where c.status = 'Active' and c.userId = :userId")
    Cart findActiveCartByUserId(@Param("userId") Long userId);

    @Query("select c.id from Cart c where c.status = 'Active' and c.userId = :userId")
    Long findActiveCartIdByUserId(@Param("userId") Long userId);
}