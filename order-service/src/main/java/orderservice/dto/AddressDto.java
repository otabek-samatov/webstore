package orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import orderservice.entities.Address;

import java.io.Serializable;

/**
 * DTO for {@link Address}
 */
@Data
public class AddressDto implements Serializable {
    @NotBlank(message = "Country should be specified")
    String country;
    @NotBlank(message = "Region should be specified")
    String region;
    @NotBlank(message = "City should be specified")
    String city;
    @NotBlank(message = "Street should be specified")
    String street;
    @NotBlank
    String addressLine;
}