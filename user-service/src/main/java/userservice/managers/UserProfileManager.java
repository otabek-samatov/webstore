package userservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import userservice.dto.UserProfileDto;
import userservice.entities.UserProfile;
import userservice.mappers.UserProfileMapper;
import userservice.repositories.UserProfileRepository;
import userservice.repositories.UserRepository;
import userservice.validators.UserProfileValidator;

@RequiredArgsConstructor
@Service
public class UserProfileManager {
    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final UserProfileValidator validator;
    private final UserProfileMapper mapper;


    public UserProfile getByID(Long id) {
        return profileRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User Profile with id " + id + " not found"));
    }

    @Transactional
    public UserProfile create(UserProfileDto dto) {
        validator.validate(dto);

        UserProfile profile = new UserProfile();
        mapper.partialUpdate(dto, profile);

        Long id = userRepository.getIdByUserName(dto.getUserName());
        profile.setUser(userRepository.getReferenceById(id));

        return profileRepository.save(profile);
    }

    @Transactional
    public UserProfile update(UserProfileDto dto) {
        validator.validate(dto);

        UserProfile profile = profileRepository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto.getId() + " not found"));

        mapper.partialUpdate(dto, profile);

        Long id = userRepository.getIdByUserName(dto.getUserName());
        profile.setUser(userRepository.getReferenceById(id));

        return profileRepository.save(profile);
    }

    @Transactional
    public void deleteById(Long id) {
        profileRepository.findById(id)
                .ifPresent(profileRepository::delete);
    }
}
