package inventoryservice.services;

import inventoryservice.entities.Inventory;
import inventoryservice.entities.InventoryChange;
import inventoryservice.entities.ReasonType;
import inventoryservice.exceptions.NotEnoughStockException;
import inventoryservice.repositories.InventoryChangeRepository;
import inventoryservice.repositories.InventoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class InventoryManager {

    private final InventoryRepository inventoryRepository;
    private final InventoryChangeRepository inventoryChangeRepository;

    public long getAvailableProductCount(String productSKU) {
        return inventoryRepository.getAvailableStockLevel(productSKU);
    }

    @Transactional
    public void reserveStock(String productSKU, long quantity) {

        if (quantity < 0) {
            throw new IllegalArgumentException("Stock level must not be negative");
        }

        Inventory inv = findInventoryForUpdate(productSKU);

        if (inv.getStockLevel() - inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() + quantity);

        saveChanges(inv, quantity, ReasonType.RESERVE_STOCK);
    }

    @Transactional
    public void commitStock(String productSKU, long quantity) {

        if (quantity < 0) {
            throw new IllegalArgumentException("Stock level must not be negative");
        }

        Inventory inv = findInventoryForUpdate(productSKU);
        if (inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() - quantity);
        inv.setStockLevel(inv.getStockLevel() - quantity);

        saveChanges(inv, quantity, ReasonType.COMMIT_STOCK);
    }

    @Transactional
    public void revertStock(String productSKU, long quantity) {

        if (quantity < 0) {
            throw new IllegalArgumentException("Stock level must not be negative");
        }

        Inventory inv = findInventoryForUpdate(productSKU);
        if (inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() - quantity);

        saveChanges(inv, quantity, ReasonType.REVERT_STOCK);
    }

    @Transactional
    public void increaseStockLevel(String productSKU, long quantity) {

        if (quantity < 0) {
            throw new IllegalArgumentException("Stock level must not be negative");
        }

        if (!StringUtils.hasText(productSKU)) {
            throw new IllegalArgumentException("productSKU is empty");
        }

        Optional<Inventory> inventoryOptional = inventoryRepository.findInventoryByProductSKU(productSKU);

        Inventory inv = inventoryOptional.orElse(new Inventory());
        inv.setProductSKU(productSKU);
        inv.setStockLevel(inv.getStockLevel() + quantity);

        saveChanges(inv, quantity, ReasonType.INCREASED_BY_WAREHOUSE);
    }

    @Transactional
    public void decreaseStockLevel(String productSKU, long quantity) {

        if (quantity < 0) {
            throw new IllegalArgumentException("Stock level must not be negative");
        }

        Inventory inv = findInventoryForUpdate(productSKU);
        if (inv.getStockLevel() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setStockLevel(inv.getStockLevel() - quantity);

        saveChanges(inv, quantity, ReasonType.CANCELLED_BY_WAREHOUSE);
    }

    private Inventory findInventoryForUpdate(String productSKU) {

        if (!StringUtils.hasText(productSKU)) {
            throw new IllegalArgumentException("productSKU is empty");
        }

        Optional<Inventory> inv = inventoryRepository.findInventoryByProductSKU(productSKU);

        return inv.orElseThrow(() -> new EntityNotFoundException("Product not found: " + productSKU));
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

    private void saveChanges(Inventory inventory, long changeQuantity, ReasonType reasonType) {

        InventoryChange invChange = new InventoryChange();
        invChange.setInventory(inventory);
        invChange.setChangeAmount(changeQuantity);
        invChange.setEventType(reasonType);

        inventoryRepository.save(inventory);
        inventoryChangeRepository.save(invChange);
    }
}
