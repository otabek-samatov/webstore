package orderservice.validators;

import jakarta.validation.Validator;
import orderservice.dto.OrderItemDto;
import orderservice.repositories.OrderItemRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Service
public class OrderItemValidator extends BaseValidator {

    private final OrderItemRepository orderItemRepository;

    public OrderItemValidator(Validator validator, OrderItemRepository orderItemRepository) {
        super(validator);
        this.orderItemRepository = orderItemRepository;
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
