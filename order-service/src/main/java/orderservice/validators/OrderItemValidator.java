package orderservice.validators;

import jakarta.validation.Validator;
import orderservice.dto.OrderItemDto;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Component
public class OrderItemValidator extends BaseValidator {


    public OrderItemValidator(Validator validator) {
        super(validator);
    }

    public void validate(Collection<OrderItemDto> dtos) {
        if (CollectionUtils.isEmpty(dtos)) {
            throw new IllegalArgumentException("dtos cannot be empty");
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
