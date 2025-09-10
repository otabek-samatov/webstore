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
import userservice.validators.CustomValidator;

@RequiredArgsConstructor
@Service
public class UserManager {
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SecurityRoleRepository securityRoleRepository;
    private final CustomValidator validator;
    private final UserMapper mapper;


    public User getUserByID(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User with id " + id + " not found"));
    }

    public User createUser(UserDto dto) {
        validator.validate(dto);

        User user = new User();
        mapper.partialUpdate(dto, user);

        if (dto.getSecurityRoleId() != null) {
            user.setSecurityRole(securityRoleRepository.getReferenceById(dto.getSecurityRoleId()));
        }

        return userRepository.save(user);
    }

    public User update(UserDto dto) {
        validator.validate(dto);

        User user = userRepository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));

        mapper.partialUpdate(dto, user);

        if (dto.getSecurityRoleId() != null) {
            user.setSecurityRole(securityRoleRepository.getReferenceById(dto.getSecurityRoleId()));
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteById(Long id) {

        userProfileRepository.deleteByUserId(id);
        userRepository.deleteById(id);

    }
}
