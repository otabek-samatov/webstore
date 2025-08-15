package inventoryservice.controllers;

import inventoryservice.dto.InventoryDto;
import inventoryservice.entities.Inventory;
import inventoryservice.mappers.InventoryMapper;
import inventoryservice.services.InventoryManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/inventories/inventory")
public class InventoryController {

    private final InventoryManager manager;
    private final InventoryMapper inventoryMapper;

    @GetMapping("/{sku}")
    public ResponseEntity<InventoryDto> getBySKU(@PathVariable String sku) {
        Inventory inv = manager.findInventoryByProductSKU(sku);
        return ResponseEntity.ok(inventoryMapper.toDto(inv));
    }

    @GetMapping("/available-count/{sku}")
    public ResponseEntity<Long> getAvailableCount(@PathVariable String sku) {
        long c = manager.getAvailableProductCount(sku);
        return ResponseEntity.ok(c);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> deleteBySKU(@PathVariable String sku) {
        manager.deleteBySKU(sku);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reserve-stock")
    public ResponseEntity<Void> reserveStock(@RequestBody InventoryDto dto) {
        manager.reserveStock(dto.getProductSKU(), dto.getReservedStock());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revert-stock")
    public ResponseEntity<Void> releaseStock(@RequestBody InventoryDto dto) {
        manager.revertStock(dto.getProductSKU(), dto.getReservedStock());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/commit-stock")
    public ResponseEntity<Void> commitStock(@RequestBody InventoryDto dto) {
        manager.commitStock(dto.getProductSKU(), dto.getReservedStock());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/increase-stock")
    public ResponseEntity<Void> fillStockByWarehouse(@RequestBody InventoryDto dto) {
        manager.increaseStockLevel(dto.getProductSKU(), dto.getReservedStock());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/decrease-stock")
    public ResponseEntity<Void> cancelStockByWarehouse(@RequestBody InventoryDto dto) {
        manager.decreaseStockLevel(dto.getProductSKU(), dto.getReservedStock());
        return ResponseEntity.noContent().build();
    }


}
