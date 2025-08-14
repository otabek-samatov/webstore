package inventoryservice.repositories;

import inventoryservice.entities.InventoryChange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryChangeRepository extends JpaRepository<InventoryChange, Long> {

    @Query("select p.id from InventoryChange p where p.inventory.productSKU = :productSKU")
    List<Long> getHistoryBySKU(String productSKU);

}