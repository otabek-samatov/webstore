package userservice.managers;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import userservice.dto.UserDto;
import userservice.dto.UserProfileDto;
import userservice.entities.User;
import userservice.entities.UserProfile;
import userservice.mappers.UserProfileMapper;
import userservice.repositories.AddressRepository;
import userservice.repositories.UserProfileRepository;
import userservice.repositories.UserRepository;
import userservice.validators.UserProfileValidator;

@RequiredArgsConstructor
@Service
public class UserProfileManager {
    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserProfileValidator validator;
    private final UserProfileMapper mapper;


    public UserProfile getUserByID(Long id) {
        return profileRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User Profile with id " + id + " not found"));
    }

    public UserProfile createUser(UserProfileDto dto) {
        validator.validate(dto);

        UserProfile profile = new UserProfile();
        mapper.partialUpdate(dto, profile);

        if (dto.getUserId() != null) {
            profile.setUser(userRepository.getReferenceById(dto.getUserId()));
        }

        if (dto.getAddressId() != null) {
            profile.setAddress(addressRepository.getReferenceById(dto.getAddressId()));
        }

        return profileRepository.save(profile);
    }

    public UserProfile update(UserProfileDto dto) {
        validator.validate(dto);

        UserProfile profile = profileRepository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));

        mapper.partialUpdate(dto, profile);

        if (dto.getUserId() != null) {
            profile.setUser(userRepository.getReferenceById(dto.getUserId()));
        }

        if (dto.getAddressId() != null) {
            profile.setAddress(addressRepository.getReferenceById(dto.getAddressId()));
        }

        return profileRepository.save(profile);
    }

    @Transactional
    public void deleteById(Long id) {
        profileRepository.deleteByUserId(id);
    }
}
