package cartservice.dto;

import cartservice.entities.CartStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link cartservice.entities.Cart}
 */
@Data
public class CartDto implements Serializable {
    Long id;

    @NotNull(message = "User ID should be specified")
    Long userId;

    LocalDateTime eventTime;

    @NotNull(message = "Status should be specified")
    CartStatus status;
}