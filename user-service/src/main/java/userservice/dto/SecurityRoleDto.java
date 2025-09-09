package userservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Value;
import userservice.entities.RoleType;
import userservice.entities.SecurityRole;

import java.io.Serializable;

/**
 * DTO for {@link SecurityRole}
 */
@Data
public class SecurityRoleDto implements Serializable {

    @NotNull
    RoleType roleType;
}