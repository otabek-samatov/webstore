package inventoryservice.dto;

import inventoryservice.entities.InventoryChange;
import inventoryservice.entities.ReasonType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link InventoryChange}
 */
@Data
public class InventoryChangeDto implements Serializable {
    @NotNull
    Long id;

    @NotNull
    @PastOrPresent
    LocalDateTime eventTime;

    @NotNull
    Long changeAmount;

    @NotNull
    ReasonType eventType;

    @NotNull
    @PositiveOrZero
    Long eventID = 0L;

    @NotNull
    Long inventoryId;
}