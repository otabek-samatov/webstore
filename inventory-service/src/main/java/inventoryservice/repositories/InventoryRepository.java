package inventoryservice.repositories;

import inventoryservice.entities.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findInventoryByProductSKU(String productSKU);

    @Lock(LockModeType.PESSIMISTIC_READ)
    long countInventoryByProductSKU(String productSKU);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p.stockLevel - p.reservedStock from Inventory p where p.productSKU = :productSKU")
    long getAvailableStockLevel(Long categoryId);

}