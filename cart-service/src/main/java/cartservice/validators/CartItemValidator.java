package cartservice.validators;

import cartservice.dto.CartItemDto;
import cartservice.repositories.CartItemRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Service
public class CartItemValidator extends BaseValidator {

    private final CartItemRepository cartItemRepository;

    public CartItemValidator(Validator validator, CartItemRepository cartItemRepository) {
        super(validator);
        this.cartItemRepository = cartItemRepository;
    }

    public void validate(CartItemDto dto) {
        super.validate(dto);
    }

    public void validate(Collection<CartItemDto> dtos) {
        List<String> products = dtos.stream().map(CartItemDto::getProductSKU).toList();
        HashSet<String> productSet = new HashSet<>(products);
        if (products.size() != productSet.size()) {
            throw new IllegalArgumentException("Duplicate products found in item list");
        }

        for (CartItemDto cartItemDto : dtos) {
            validate(cartItemDto);
        }
    }
}
