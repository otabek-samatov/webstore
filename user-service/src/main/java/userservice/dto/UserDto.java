package userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import userservice.entities.RoleType;

import java.io.Serializable;

/**
 * DTO for {@link userservice.entities.User}
 */
@Data
public class UserDto implements Serializable {
    Long id;

    @NotBlank(message = "userName cannot be blank")
    String userName;

    @NotBlank(message = "password cannot be blank")
    String password;

    Boolean isActive;

    @NotNull(message = "Security Role cannot be blank")
    private RoleType securityRoleType;
}