package cartservice.managers;

import cartservice.dto.CartDto;
import cartservice.dto.CartItemDto;
import cartservice.entities.Cart;
import cartservice.entities.CartItem;
import cartservice.mappers.CartItemMapper;
import cartservice.mappers.CartMapper;
import cartservice.repositories.CartItemRepository;
import cartservice.repositories.CartRepository;
import cartservice.validators.CartItemValidator;
import cartservice.validators.CartValidator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartManager {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final CartValidator cartValidator;
    private final CartItemValidator cartItemValidator;


    public CartManager(CartRepository cartRepository, CartItemRepository cartItemRepository, CartMapper cartMapper, CartItemMapper cartItemMapper, CartValidator cartValidator, CartItemValidator cartItemValidator) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.cartValidator = cartValidator;
        this.cartItemValidator = cartItemValidator;
    }

    public CartDto getCart(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        Cart cart = cartRepository.findActiveCartByUserId(userId);
        if (cart == null) {
            throw new EntityNotFoundException("No cart found");
        }

        return cartMapper.toDto(cart);
    }

    public List<CartItemDto> getCartItems(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        return cartItemMapper.toDto(cartItemRepository.getItemsByUserID(userId));
    }

    public CartItemDto getCartItem(Long cartItemId) {
        if (cartItemId == null) {
            throw new IllegalArgumentException("cartItemId is null");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow(() -> new EntityNotFoundException("Cart Item with = " + cartItemId + " not found"));
        return cartItemMapper.toDto(item);
    }

    public void removeCartItem(Long cartItemId) {
        if (cartItemId == null) {
            throw new IllegalArgumentException("cartItemId is null");
        }

        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow(() -> new EntityNotFoundException("Cart Item with = " + cartItemId + " not found"));
        Cart cart = item.getCart();
        cart.removeCartItem(item);
        cartRepository.save(cart);

    }

    @Transactional
    public void deleteCart(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        Cart cart = cartRepository.findActiveCartByUserId(userId);
        if (cart != null) {
            cartRepository.delete(cart);
        }
    }

    public BigDecimal getTotal(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        return cartItemRepository.getSumByUserID(userId);
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

            for (CartItemDto cartItemDto : cartItemDtos) {
                CartItem cartItem = cartItemMapper.toEntity(cartItemDto);
                cartItem.setUnitPrice(getUnitPrice(cartItemDto.getProductSKU()));
                cart.addCartItem(cartItem);
            }
        } else {
            for (CartItemDto cartItemDto : cartItemDtos) {
                Long itemID = cartItemRepository.getCartItemID(userId, cartItemDto.getProductSKU());
                if (itemID != null) {
                    throw new IllegalArgumentException("Product = " + cartItemDto.getProductSKU() + " already exists in cart");
                }

                CartItem cartItem = cartItemMapper.toEntity(cartItemDto);
                cartItem.setUnitPrice(getUnitPrice(cartItemDto.getProductSKU()));
                cart.addCartItem(cartItem);
            }
        }

        cartRepository.save(cart);
    }

    @Transactional
    public void  updateQuantity(CartItemDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("cartItemDto is null");
        }

        cartItemValidator.validate(dto);

       CartItem item = cartItemRepository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException("Cart Item with = " + dto.getId() + " not found"));
       item.setQuantity(dto.getQuantity());

       cartItemRepository.save(item);
    }

    private BigDecimal getUnitPrice(String productSKU) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
