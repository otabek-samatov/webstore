package userservice.validators;

import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import userservice.dto.UserProfileDto;
import userservice.repositories.AddressRepository;
import userservice.repositories.UserProfileRepository;
import userservice.repositories.UserRepository;

@Service
public class UserProfileValidator extends BaseValidator {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public UserProfileValidator(Validator validator, UserRepository userRepository, AddressRepository addressRepository) {
        super(validator);
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    public void validate(UserProfileDto dto) {
        super.validate(dto);

        if (dto.getUserId() != null) {
            boolean f = userRepository.existsById(dto.getUserId());
            if (!f) {
                throw new IllegalArgumentException("User with id " + dto.getUserId() + " not found");
            }
        }

        if (dto.getAddressId() != null) {
            boolean f = addressRepository.existsById(dto.getAddressId());
            if (!f) {
                throw new IllegalArgumentException("Address with id " + dto.getAddressId() + " not found");
            }
        }

    }
}
