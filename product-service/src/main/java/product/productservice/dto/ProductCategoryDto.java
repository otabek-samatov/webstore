package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link product.productservice.entities.ProductCategory}
 */

@Builder
@Value
public class ProductCategoryDto implements Serializable {
    Long id;

    @NotBlank(message = "Category Name should be specified")
    String name;

    Long parentId;
}