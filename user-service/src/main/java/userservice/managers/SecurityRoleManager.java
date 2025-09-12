package userservice.managers;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import userservice.entities.RoleType;
import userservice.entities.SecurityRole;
import userservice.entities.User;
import userservice.repositories.SecurityRoleRepository;
import userservice.repositories.UserRepository;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SecurityRoleManager {

    private final SecurityRoleRepository securityRoleRepository;
    private final UserRepository userRepository;

    public List<SecurityRole> getRoles() {
        return securityRoleRepository.findAll();
    }

    @Transactional
    public void assignRole(Long userId, RoleType roleType) {
        if (userId == null) {
            throw new NullPointerException("userId is null");
        }

        if (roleType == null) {
            throw new NullPointerException("roleType is null");
        }

        Long roleID = securityRoleRepository.getIDByRoleType(roleType);
        if (roleID == null) {
            throw new NullPointerException("Security role with role type = " + roleType + " does not exist");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User with id " + userId + " not found"));
        user.setSecurityRole(securityRoleRepository.getReferenceById(roleID));

        userRepository.save(user);
    }
}
