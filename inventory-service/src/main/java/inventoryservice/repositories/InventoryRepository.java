package inventoryservice.repositories;

import inventoryservice.dto.InventoryDto;
import inventoryservice.entities.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findInventoryByProductSKU(String productSKU);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p.stockLevel - p.reservedStock from Inventory p where p.productSKU = :productSKU")
    Optional<Long> getAvailableStockLevel(String productSKU);

    @Query("select new inventoryservice.dto.InventoryDto (p.productSKU, p.sellPrice) from Inventory p where p.productSKU in :productList")
    List<InventoryDto> getInventoryPrices(Collection<String> productList);


}