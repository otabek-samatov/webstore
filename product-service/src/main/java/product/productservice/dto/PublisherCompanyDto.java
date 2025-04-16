package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;
import product.productservice.entities.PublisherCompany;

import java.io.Serializable;

/**
 * DTO for {@link PublisherCompany}
 */
@Value
public class PublisherCompanyDto implements Serializable {
    Long id;

    @NotBlank(message = "Publisher name should be specified")
    String name;
}