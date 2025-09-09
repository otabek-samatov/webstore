package userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Value;
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
    RoleType securityRoleRoleType;
    Boolean isActive;
}