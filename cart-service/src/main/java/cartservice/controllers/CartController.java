package cartservice.controllers;

import cartservice.dto.CartDto;
import cartservice.dto.CartItemDto;
import cartservice.entities.Cart;
import cartservice.entities.CartItem;
import cartservice.managers.CartManager;
import cartservice.mappers.CartItemMapper;
import cartservice.mappers.CartMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@RequestMapping(value = "v1/carts/cart")
@RestController
public class CartController {
    private final CartManager cartManager;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;


    @GetMapping("/{userID}")
    public ResponseEntity<CartDto> getCart(@PathVariable Long userID) {
        Cart cart = cartManager.getCart(userID);
        return ResponseEntity.ok(cartMapper.toDto(cart));
    }

    @DeleteMapping("/{userID}")
    public ResponseEntity<Void> deleteCart(@PathVariable Long userID) {
        cartManager.deleteCart(userID);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userID}/total")
    public ResponseEntity<BigDecimal> getCartTotal(@PathVariable Long userID) {
        BigDecimal total = cartManager.getTotal(userID);
        return ResponseEntity.ok(total);
    }

    @PostMapping("/{userID}/items")
    public ResponseEntity<Void> addItems(@PathVariable Long userID, @Valid  @RequestBody List<CartItemDto> items) {
        cartManager.addToCart(userID, items);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userID}/item")
    public ResponseEntity<Void> addItem(@PathVariable Long userID, @Valid @RequestBody CartItemDto item) {
        cartManager.addToCart(userID, List.of(item));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/item")
    public ResponseEntity<Void> updateItem(@Valid @RequestBody CartItemDto item) {
        cartManager.updateQuantity(item);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/item/{itemID}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemID) {
        cartManager.removeCartItem(itemID);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/item/{itemID}")
    public ResponseEntity<CartItemDto> getItem(@PathVariable Long itemID) {
        CartItem item = cartManager.getCartItem(itemID);
        return ResponseEntity.ok(cartItemMapper.toDto(item));
    }

    @GetMapping("/{userID}/items")
    public ResponseEntity<List<CartItemDto>> getItems(@PathVariable Long userID) {
        List<CartItem> items = cartManager.getCartItems(userID);
        return ResponseEntity.ok(cartItemMapper.toDto(items));
    }

    @PostMapping("/{userID}/complete")
    public ResponseEntity<Void> completeCart(@PathVariable Long userID) {
        cartManager.checkout(userID);
        return ResponseEntity.noContent().build();
    }
}
