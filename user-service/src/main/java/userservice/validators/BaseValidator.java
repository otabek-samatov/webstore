package userservice.validators;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class BaseValidator {
    protected final Validator validator;

    public BaseValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Object to validate cannot be null");
        }

        Set<ConstraintViolation<Object>> violations = validator.validate(obj);

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<Object> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("\n");
            }
            throw new IllegalArgumentException("Validation failed:\n" + sb);
        }
    }
}
