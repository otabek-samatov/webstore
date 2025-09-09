package userservice.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "First Name cannot be blank")
    String firstName;

    @NotBlank(message = "First Name cannot be blank")
    String lastName;

    private String userUserName;
    private Long addressId;
    String middleName;
    LocalDate dateOfBirth;
}