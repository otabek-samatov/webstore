package orderservice.validators;

import jakarta.validation.Validator;
import orderservice.dto.OrderItemDto;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Service
public class OrderItemValidator extends BaseValidator {


    public OrderItemValidator(Validator validator) {
        super(validator);
    }

    public void validate(Collection<OrderItemDto> dtos) {
        if (dtos == null) {
            throw new IllegalArgumentException("dtos cannot be null");
        }

        List<String> products = dtos.stream().map(OrderItemDto::getProductSKU).toList();
        HashSet<String> productSet = new HashSet<>(products);
        if (products.size() != productSet.size()) {
            throw new IllegalArgumentException("Duplicate products found in item list");
        }

        for (OrderItemDto itemDto : dtos) {
            validate(itemDto);
        }
    }
}
