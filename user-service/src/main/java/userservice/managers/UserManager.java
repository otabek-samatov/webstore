package userservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import userservice.dto.UserDto;
import userservice.entities.User;
import userservice.mappers.UserMapper;
import userservice.repositories.SecurityRoleRepository;
import userservice.repositories.UserProfileRepository;
import userservice.repositories.UserRepository;
import userservice.validators.UserValidator;

@RequiredArgsConstructor
@Service
public class UserManager {
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SecurityRoleRepository securityRoleRepository;
    private final UserValidator validator;
    private final UserMapper mapper;


    public User getByID(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User with id " + id + " not found"));
    }

    @Transactional
    public User create(UserDto dto) {
        validator.validate(dto);

        User user = new User();
        mapper.partialUpdate(dto, user);

        if (dto.getSecurityRoleType() != null) {
            Long id = securityRoleRepository.getIDByRoleType(dto.getSecurityRoleType());
            user.setSecurityRole(securityRoleRepository.getReferenceById(id));
        }

        return userRepository.save(user);
    }

    @Transactional
    public User update(UserDto dto) {
        validator.validate(dto);

        User user = userRepository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto.getId() + " not found"));

        mapper.partialUpdate(dto, user);

        if (dto.getSecurityRoleType() != null) {
            Long id = securityRoleRepository.getIDByRoleType(dto.getSecurityRoleType());
            user.setSecurityRole(securityRoleRepository.getReferenceById(id));
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteById(Long id) {
        userProfileRepository.findUserProfileByUserId(id)
                .ifPresent(userProfileRepository::delete);
    }
}
