package userservice.managers;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import userservice.dto.SecurityRoleDto;
import userservice.entities.SecurityRole;
import userservice.entities.User;
import userservice.repositories.SecurityRoleRepository;
import userservice.repositories.UserRepository;
import userservice.validators.SecurityRoleValidator;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SecurityRoleManager {

    private final SecurityRoleRepository securityRoleRepository;
    private final UserRepository userRepository;
    private final SecurityRoleValidator validator;

    public List<SecurityRole> getRoles() {
        return securityRoleRepository.findAll();
    }

    @Transactional
    public void assignRole(Long userId, SecurityRoleDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is null");
        }

        validator.validate(dto);

        Long roleID = securityRoleRepository.getIDByRoleType(dto.getRoleType());
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User with id " + userId + " not found"));
        user.setSecurityRole(securityRoleRepository.getReferenceById(roleID));

        userRepository.save(user);
    }
}
