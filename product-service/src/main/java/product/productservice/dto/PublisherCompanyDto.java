package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link product.productservice.entities.PublisherCompany}
 */

@Builder
@Value
public class PublisherCompanyDto implements Serializable {
    Long id;
    @NotBlank(message = "Publisher name should be specified")
    String name;
}