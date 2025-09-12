package userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO for {@link userservice.entities.UserProfile}
 */
@Data
public class UserProfileDto implements Serializable {
    Long id;

    @NotBlank(message = "First Name should be specified")
    String firstName;

    @NotBlank(message = "Last Name should be specified")
    String lastName;

    String middleName;

    @NotBlank(message = "User Name Should be Specified")
    private String userName;

    @NotNull(message = "Address Should be Specified")
    private AddressDto address;

    @NotNull(message = "Date Of Birth Should Be Specified")
    LocalDate dateOfBirth;
}