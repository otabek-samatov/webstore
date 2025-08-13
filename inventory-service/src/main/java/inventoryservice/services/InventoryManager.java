package inventoryservice.services;

import inventoryservice.entities.Inventory;
import inventoryservice.entities.ReasonType;
import inventoryservice.exceptions.IncorrectParameterException;
import inventoryservice.exceptions.NotEnoughStockException;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryManager {

    @PersistenceContext
    private EntityManager entityManager;

    public long getAvailableProductCount(String productSKU) {
        return entityManager.createQuery("select p.stockLevel - p.reservedStock from Inventory p where p.productSKU = :productSKU", Long.class)
                .setParameter("productSKU", productSKU)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getSingleResult();
    }

    @Transactional
    public void reserveProduct(String productSKU, long reservedStock) {

        if (reservedStock <= 0) {
            throw new IncorrectParameterException("Reserved stock must be greater than 0");
        }

        Inventory inv = findInventoryForUpdate(productSKU);

        if (inv.getStockLevel() - inv.getReservedStock() < reservedStock) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() + reservedStock);
    }

    @Transactional
    public void processStockAdjustment(String productSKU, long stockLevel, ReasonType reason) {

        if (stockLevel <= 0) {
            throw new IncorrectParameterException("Stock level must be greater than 0");
        }

        if (reason == null) {
            throw new IncorrectParameterException("Reason is empty");
        }

        Inventory inv = findInventoryForUpdate(productSKU);

        if (ReasonType.ORDER_CONFIRMED.equals(reason)) {
            if (inv.getReservedStock() < stockLevel) {
                throw new NotEnoughStockException(productSKU);
            }

            inv.setReservedStock(inv.getReservedStock() - stockLevel);
            inv.setStockLevel(inv.getStockLevel() - stockLevel);

        } else if (ReasonType.ORDER_CANCELLED.equals(reason)) {
            if (inv.getReservedStock() < stockLevel) {
                throw new NotEnoughStockException(productSKU);
            }
            inv.setReservedStock(inv.getReservedStock() - stockLevel);
        } else if (ReasonType.FILLED_BY_WAREHOUSE.equals(reason)) {
            inv.setStockLevel(inv.getStockLevel() + stockLevel);
        } else if (ReasonType.CANCELLED_BY_WAREHOUSE.equals(reason)) {
            if (inv.getStockLevel() < stockLevel) {
                throw new NotEnoughStockException(productSKU);
            }

            inv.setStockLevel(inv.getStockLevel() - stockLevel);
        }

    }

    private Inventory findInventoryForUpdate(String productSKU) {

        if (!StringUtils.hasText(productSKU)) {
            throw new IncorrectParameterException("productSKU is empty");
        }

        try {
            return entityManager.createQuery("select p from Inventory p where p.productSKU = :productSKU", Inventory.class)
                    .setParameter("productSKU", productSKU)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new EntityNotFoundException("Product not found: " + productSKU);
        }
    }

}
