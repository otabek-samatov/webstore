package userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO for {@link userservice.entities.UserProfile}
 */
@Data
public class UserProfileDto implements Serializable {
    Long id;


    @NotNull(message = "User ID should be specified")
    Long userId;

    @NotBlank(message = "First Name should be specified")
    String firstName;

    @NotBlank(message = "Last Name should be specified")
    String lastName;

    String middleName;

    @NotNull(message = "Address ID should be specified")
    Long addressId;

    @NotNull(message = "Date Of Birth Should Be Specified")
    LocalDate dateOfBirth;
}