package userservice.validators;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import userservice.dto.UserProfileDto;
import userservice.repositories.UserRepository;

@Service
public class UserProfileValidator extends BaseValidator {

    private final UserRepository userRepository;

    public UserProfileValidator(Validator validator, UserRepository userRepository) {
        super(validator);
        this.userRepository = userRepository;
    }

    public void validate(UserProfileDto dto) {
        super.validate(dto);

        boolean f = userRepository.existsUserByUserName(dto.getUserName());
        if (!f) {
            throw new EntityNotFoundException("User with username " + dto.getUserName() + " not found");
        }
    }
}
