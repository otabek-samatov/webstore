package inventoryservice.services;

import inventoryservice.entities.Inventory;
import inventoryservice.entities.ReasonType;
import inventoryservice.exceptions.NotEnoughStockException;
import inventoryservice.repositories.InventoryChangeRepository;
import inventoryservice.repositories.InventoryRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
@Service
public class InventoryManager {

    private final InventoryRepository inventoryRepository;
    private final InventoryChangeRepository inventoryChangeRepository;

    public long getAvailableProductCount(String productSKU) {
        return entityManager.createQuery("select p.stockLevel - p.reservedStock from Inventory p where p.productSKU = :productSKU", Long.class)
                .setParameter("productSKU", productSKU)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getSingleResult();
    }

    @Transactional
    public void processStockAdjustment(String productSKU, long quantity, ReasonType reason) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Stock level must be greater than 0");
        }

        if (reason == null) {
            throw new IllegalArgumentException("Reason is empty");
        }

        Inventory inv = findInventoryForUpdate(productSKU);

        if (ReasonType.ORDER_CREATED.equals(reason)) {
            if (inv.getStockLevel() - inv.getReservedStock() < quantity) {
                throw new NotEnoughStockException(productSKU);
            }

            inv.setReservedStock(inv.getReservedStock() + quantity);

        } else if (ReasonType.ORDER_CONFIRMED.equals(reason)) {
            if (inv.getReservedStock() < quantity) {
                throw new NotEnoughStockException(productSKU);
            }

            inv.setReservedStock(inv.getReservedStock() - quantity);
            inv.setStockLevel(inv.getStockLevel() - quantity);

        } else if (ReasonType.ORDER_CANCELLED.equals(reason)) {
            if (inv.getReservedStock() < quantity) {
                throw new NotEnoughStockException(productSKU);
            }
            inv.setReservedStock(inv.getReservedStock() - quantity);
        } else if (ReasonType.FILLED_BY_WAREHOUSE.equals(reason)) {
            inv.setStockLevel(inv.getStockLevel() + quantity);
        } else if (ReasonType.CANCELLED_BY_WAREHOUSE.equals(reason)) {
            if (inv.getStockLevel() < quantity) {
                throw new NotEnoughStockException(productSKU);
            }

            inv.setStockLevel(inv.getStockLevel() - quantity);
        }

    }

    private Inventory findInventoryForUpdate(String productSKU) {

        if (!StringUtils.hasText(productSKU)) {
            throw new IllegalArgumentException("productSKU is empty");
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

    @Transactional
    public Inventory createNewInventory(String productSKU, long stockLevel) {

        if (!StringUtils.hasText(productSKU)) {
            throw new IllegalArgumentException("productSKU is empty");
        }

        Inventory newInventory = new Inventory();
        newInventory.setProductSKU(productSKU);
        newInventory.setStockLevel(stockLevel);

        entityManager.persist(newInventory);

        return newInventory;
    }

    public Inventory findInventoryByProductSKU(String productSKU) {
        return inventoryRepository.findInventoryByProductSKU(productSKU)
                .orElseThrow(() -> new EntityNotFoundException("Inventory with productSKU " + productSKU + " not found"));
    }

    @Transactional
    public void deleteBySKU(String sku) {
        if (!StringUtils.hasText(sku)) {
            throw new IllegalArgumentException("productSKU is empty");
        }

        List<Long> historyList = inventoryChangeRepository.getHistoryBySKU(sku);
        if (!historyList.isEmpty()) {
            inventoryChangeRepository.deleteAllById(historyList);
        }

        Inventory inventory = findInventoryByProductSKU(sku);
        inventoryRepository.delete(inventory);
    }


}
