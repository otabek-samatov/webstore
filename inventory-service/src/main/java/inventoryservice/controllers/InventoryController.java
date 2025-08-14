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
    private ResponseEntity<InventoryDto> getBySKU(@PathVariable String sku) {
        Inventory inv = manager.findInventoryByProductSKU(sku);
        return ResponseEntity.ok(inventoryMapper.toDto(inv));
    }

    @DeleteMapping("/{sku}")
    private ResponseEntity<InventoryDto> deleteBySKU(@PathVariable String sku) {
        manager.deleteBySKU(sku);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<BookDto> create(@RequestBody BookDto dto) {
        Book book = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @PutMapping
    public ResponseEntity<BookDto> update(@RequestBody BookDto dto) {
        Book book = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

}
