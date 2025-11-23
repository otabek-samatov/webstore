package cartservice.managers;

import cartservice.dto.CartItemDto;
import cartservice.dto.InventoryDto;
import cartservice.entities.Cart;
import cartservice.entities.CartItem;
import cartservice.entities.CartStatus;
import cartservice.mappers.CartItemMapper;
import cartservice.repositories.CartItemRepository;
import cartservice.repositories.CartRepository;
import cartservice.validators.CartItemValidator;
import exceptions.NotEnoughStockException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CartManager {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemMapper cartItemMapper;
    private final CartItemValidator cartItemValidator;
    private final RestClient restClient;

    public Cart getCart(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        Cart cart = cartRepository.findActiveCartByUserId(userId);
        if (cart == null) {
            throw new EntityNotFoundException("No cart found");
        }

        return cart;
    }

    public List<CartItem> getCartItems(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        return cartItemRepository.getItemsByUserID(userId);
    }

    public List<CartItem> getCartItemsByCartID(Long cartID) {
        if (cartID == null) {
            throw new IllegalArgumentException("cartID is null");
        }

        return cartItemRepository.getItemsByCartID(cartID);
    }

    public CartItem getCartItem(Long cartItemId) {
        if (cartItemId == null) {
            throw new IllegalArgumentException("cartItemId is null");
        }

        return cartItemRepository.findById(cartItemId).orElseThrow(() -> new EntityNotFoundException("Cart Item with = " + cartItemId + " not found"));
    }

    @Transactional
    public void removeCartItem(Long cartItemId) {
        if (cartItemId == null) {
            throw new IllegalArgumentException("cartItemId is null");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow(() -> new EntityNotFoundException("Cart Item with = " + cartItemId + " not found"));
        Cart cart = item.getCart();
        cart.removeCartItem(item);
        cartRepository.save(cart);

        releaseStock(item);
    }

    @Transactional
    public void deleteCart(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        Cart cart = cartRepository.findActiveCartByUserId(userId);
        if (cart != null) {
            cartRepository.delete(cart);
            releaseStocks(cart.getCartItems());
        }

    }

    public BigDecimal getTotal(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        BigDecimal total = cartItemRepository.getSumByUserID(userId);
        if (total == null) {
            total = BigDecimal.ZERO;
        }

        return total;
    }

    @Transactional
    public void addToCart(Long userId, List<CartItemDto> cartItemDtos) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        cartItemValidator.validate(cartItemDtos);

        Cart cart = cartRepository.findActiveCartByUserId(userId);
        if (cart == null) {
            cart = new Cart();
            cart.setUserId(userId);
            cart.setStatus(CartStatus.IN_PROGRESS);

            for (CartItemDto cartItemDto : cartItemDtos) {
                checkQuantity(cartItemDto);

                CartItem cartItem = cartItemMapper.toEntity(cartItemDto);
                cartItem.setUnitPrice(getUnitPrice(cartItemDto.getProductSKU()));
                cart.addCartItem(cartItem);
                reserveStock(cartItemDto);
            }
        } else {
            for (CartItemDto cartItemDto : cartItemDtos) {
                Long itemID = cartItemRepository.getCartItemID(userId, cartItemDto.getProductSKU());
                if (itemID != null) {
                    throw new IllegalArgumentException("Product = " + cartItemDto.getProductSKU() + " already exists in cart");
                }

                checkQuantity(cartItemDto);

                CartItem cartItem = cartItemMapper.toEntity(cartItemDto);
                cartItem.setUnitPrice(getUnitPrice(cartItemDto.getProductSKU()));
                cart.addCartItem(cartItem);
                reserveStock(cartItemDto);
            }
        }

        cartRepository.save(cart);
    }

    @Transactional
    public void updateQuantity(CartItemDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("cartItemDto is null");
        }

        cartItemValidator.validate(dto);

        checkQuantity(dto);

        cartItemRepository.updateQuantity(dto.getId(), dto.getQuantity());
    }

    private BigDecimal getUnitPrice(String productSKU) {
        InventoryDto inventoryDto = restClient.get()
                .uri("http://inventory-service/v1/inventories/inventory/{sku}", productSKU)
                .retrieve()
                .body(InventoryDto.class);
        if (inventoryDto == null) {
            throw new EntityNotFoundException("Product with ID = " + productSKU + " not found");
        }

        return inventoryDto.getSellPrice();
    }

    private void checkQuantity(CartItemDto dto) {
        Long availableCount = restClient.get()
                .uri("http://inventory-service/v1/inventories/available-count/{sku}", dto.getProductSKU())
                .retrieve()
                .body(Long.class);

        if (availableCount == null || availableCount < dto.getQuantity()) {
            throw new NotEnoughStockException(dto.getProductSKU());
        }
    }

    private void reserveStock(CartItemDto dto) {
        restClient.post()
                .uri("http://inventory-service/v1/inventories/reserve-stock", dto)
                .retrieve()
                .toBodilessEntity();
    }

    private void releaseStock(CartItem item) {
        throw new UnsupportedOperationException("releaseStock is not supported yet.");
    }

    private void releaseStocks(Collection<CartItem> items) {
        throw new UnsupportedOperationException("releaseStocks is not supported yet.");
    }
}
