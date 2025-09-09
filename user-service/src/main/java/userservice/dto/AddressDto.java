package userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import userservice.entities.Address;

import java.io.Serializable;

/**
 * DTO for {@link Address}
 */
@Data
public class AddressDto implements Serializable {
    Long id;

    @NotBlank(message = "Country cannot be blank")
    String country;

    @NotBlank(message = "Region cannot be blank")
    String region;

    @NotBlank(message = "City cannot be blank")
    String city;

    @NotBlank(message = "Street cannot be blank")
    String street;
}