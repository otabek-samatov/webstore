package orderservice.validators;

import jakarta.validation.Validator;
import orderservice.dto.OrderDto;
import orderservice.repositories.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderValidator extends BaseValidator {

    private final OrderRepository orderRepository;

    public OrderValidator(Validator validator, OrderRepository orderRepository) {
        super(validator);
        this.orderRepository = orderRepository;
    }

    public void validate(OrderDto dto) {
        super.validate(dto);
    }
}
