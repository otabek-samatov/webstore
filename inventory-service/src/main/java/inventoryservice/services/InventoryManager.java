package inventoryservice.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryManager {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public long getAvailableProductCount(long productID, String productClass){
        return entityManager.createQuery("select p.stockLevel - p.reservedStock from Inventory p where p.productID = :productID and p.productClass = :productClass", Long.class)
                .setParameter("productID", productID)
                .setParameter("productClass", productClass)
                .getSingleResult();
    }



}
