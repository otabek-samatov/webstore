package userservice.validators;

import jakarta.persistence.EntityNotFoundException;
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

        if (dto.getSecurityRoleType() != null) {
            Long id = securityRoleRepository.getIDByRoleType(dto.getSecurityRoleType());
            if (id == null) {
                throw new EntityNotFoundException("Security role with type " + dto.getSecurityRoleType() + " not found");
            }
        }

    }
}
