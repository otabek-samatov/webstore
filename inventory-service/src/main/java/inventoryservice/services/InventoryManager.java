package inventoryservice.services;

import inventoryservice.dto.InventoryDto;
import inventoryservice.entities.Inventory;
import inventoryservice.entities.InventoryChange;
import inventoryservice.entities.ReasonType;
import inventoryservice.exceptions.NotEnoughStockException;
import inventoryservice.mappers.InventoryMapper;
import inventoryservice.repositories.InventoryChangeRepository;
import inventoryservice.repositories.InventoryRepository;
import inventoryservice.validators.CustomValidator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class InventoryManager {

    private final InventoryRepository inventoryRepository;
    private final InventoryChangeRepository inventoryChangeRepository;
    private final CustomValidator validator;
    private final InventoryMapper inventoryMapper;

    @Transactional
    public long getAvailableProductCount(String productSKU) {
        return inventoryRepository.getAvailableStockLevel(productSKU).orElse(0L);
    }

    @Transactional
    public void reserveStock(InventoryDto dto) {

        validator.validate(dto);

        String productSKU = dto.getProductSKU();
        long quantity = dto.getReservedStock();

        Inventory inv = findInventoryByProductSKU(productSKU);

        if (inv.getStockLevel() - inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() + quantity);

        saveChanges(inv, quantity, ReasonType.RESERVE_STOCK);
    }

    @Transactional
    public void commitStock(InventoryDto dto) {

        validator.validate(dto);

        String productSKU = dto.getProductSKU();
        long quantity = dto.getReservedStock();

        Inventory inv = findInventoryByProductSKU(productSKU);
        if (inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() - quantity);
        inv.setStockLevel(inv.getStockLevel() - quantity);

        saveChanges(inv, quantity, ReasonType.COMMIT_STOCK);
    }

    @Transactional
    public void revertStock(InventoryDto dto) {

        validator.validate(dto);

        String productSKU = dto.getProductSKU();
        long quantity = dto.getReservedStock();

        Inventory inv = findInventoryByProductSKU(productSKU);
        if (inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setReservedStock(inv.getReservedStock() - quantity);

        saveChanges(inv, quantity, ReasonType.REVERT_STOCK);
    }

    @Transactional
    public void increaseStockLevel(InventoryDto dto) {

        validator.validate(dto);

        String productSKU = dto.getProductSKU();
        long quantity = dto.getStockLevel();

        if (!StringUtils.hasText(productSKU)) {
            throw new IllegalArgumentException("productSKU is empty");
        }

        Optional<Inventory> inventoryOptional = inventoryRepository.findInventoryByProductSKU(productSKU);

        Inventory inv = inventoryOptional.orElse(null);
        if (inv == null) {
            inv = inventoryMapper.toEntity(dto);
        } else {
            inv.setStockLevel(inv.getStockLevel() + quantity);
        }

        saveChanges(inv, quantity, ReasonType.INCREASED_BY_WAREHOUSE);
    }

    @Transactional
    public void decreaseStockLevel(InventoryDto dto) {

        validator.validate(dto);

        String productSKU = dto.getProductSKU();
        long quantity = dto.getStockLevel();

        Inventory inv = findInventoryByProductSKU(productSKU);
        if (inv.getStockLevel() - inv.getReservedStock() < quantity) {
            throw new NotEnoughStockException(productSKU);
        }

        inv.setStockLevel(inv.getStockLevel() - quantity);

        saveChanges(inv, quantity, ReasonType.CANCELLED_BY_WAREHOUSE);
    }

    @Transactional
    public Inventory findInventoryByProductSKU(String productSKU) {

        if (!StringUtils.hasText(productSKU)) {
            throw new IllegalArgumentException("productSKU is empty");
        }

        Optional<Inventory> inv = inventoryRepository.findInventoryByProductSKU(productSKU);

        return inv.orElseThrow(() -> new EntityNotFoundException("Product with ID = " + productSKU + " not found"));
    }

    @Transactional
    public void deleteBySKU(String sku) {
        if (!StringUtils.hasText(sku)) {
            throw new IllegalArgumentException("productSKU is empty");
        }


        Inventory inventory = findInventoryByProductSKU(sku);
        inventoryChangeRepository.deleteByInventoryProductSKU(sku);
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
