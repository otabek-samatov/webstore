package inventoryservice.repositories;

import inventoryservice.entities.InventoryChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryChangeRepository extends JpaRepository<InventoryChange, Long> {

    @Query("select p.id from InventoryChange p where p.inventory.productSKU = :productSKU")
    List<Long> getHistoryIDBySKU(String productSKU);


    @Query("select p from InventoryChange p where p.inventory.productSKU = :productSKU")
    List<InventoryChange> getHistoryBySKU(String productSKU);


}