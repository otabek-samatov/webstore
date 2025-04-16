package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import product.productservice.entities.ProductCategory;

import java.io.Serializable;

/**
 * DTO for {@link ProductCategory}
 */
@Builder
@Value
public class ProductCategoryDto implements Serializable {
    Long id;

    @NotBlank(message = "Category Name should be specified")
    String name;

    Long parentCategoryId;
}