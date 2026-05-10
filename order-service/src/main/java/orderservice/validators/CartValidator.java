package orderservice.validators;

import jakarta.validation.Validator;
import orderservice.repositories.CartRepository;
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
