package userservice.validators;

import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import userservice.dto.UserDto;
import userservice.repositories.SecurityRoleRepository;
import userservice.repositories.UserRepository;

@Service
public class UserValidator extends BaseValidator {

    private final UserRepository userRepository;
    private final SecurityRoleRepository securityRoleRepository;

    public UserValidator(Validator validator, UserRepository userRepository, SecurityRoleRepository securityRoleRepository) {
        super(validator);
        this.userRepository = userRepository;
        this.securityRoleRepository = securityRoleRepository;
    }

    public void validate(UserDto dto) {
        super.validate(dto);

        if (dto.getId() == null) {
            boolean f = userRepository.existsUserByUserName(dto.getUserName());
            if (f) {
                throw new IllegalArgumentException("User with username " + dto.getUserName() + " already exists");
            }
        }

        if (dto.getSecurityRoleId() != null) {
            boolean f = securityRoleRepository.existsById(dto.getSecurityRoleId());
            if (!f) {
                throw new IllegalArgumentException("Security role with id " + dto.getSecurityRoleId() + " not found");
            }
        }

    }
}
