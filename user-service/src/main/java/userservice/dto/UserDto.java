package userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * DTO for {@link userservice.entities.User}
 */
@Data
public class UserDto implements Serializable {
    Long id;
    @NotBlank(message = "userName cannot be blank")
    String userName;
    Boolean isActive;
    private Long securityRoleId;
}