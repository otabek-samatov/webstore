package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import product.productservice.entities.Publisher;

import java.io.Serializable;

/**
 * DTO for {@link Publisher}
 */

@Builder
@Value
public class PublisherDto implements Serializable {
    Long id;
    @NotBlank(message = "Publisher name should be specified")
    String name;
}