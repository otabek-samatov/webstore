package cartservice.validators;

import cartservice.dto.CartDto;
import cartservice.repositories.CartRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

@Service
public class CartValidator extends BaseValidator {

    private final CartRepository cartRepository;

    public CartValidator(Validator validator, CartRepository cartRepository) {
        super(validator);
        this.cartRepository = cartRepository;
    }

    public void validate(CartDto dto) {
        super.validate(dto);
    }
}
