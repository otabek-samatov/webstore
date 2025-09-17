package userservice.validators;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import userservice.dto.SecurityRoleDto;
import userservice.repositories.SecurityRoleRepository;

@Service
public class SecurityRoleValidator extends BaseValidator{

    private final SecurityRoleRepository securityRoleRepository;

    public SecurityRoleValidator(Validator validator, SecurityRoleRepository securityRoleRepository) {
        super(validator);
        this.securityRoleRepository = securityRoleRepository;
    }

    public void validate(SecurityRoleDto dto) {
        if (dto == null || dto.getRoleType() == null) {
            throw new NullPointerException("roleType is null");
        }

        Long roleID = securityRoleRepository.getIDByRoleType(dto.getRoleType());
        if (roleID == null) {
            throw new EntityNotFoundException("Security role with role type = " + dto.getRoleType() + " does not exist");
        }

    }
}
