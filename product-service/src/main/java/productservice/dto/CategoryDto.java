package productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import productservice.entities.Category;

import java.io.Serializable;

/**
 * DTO for {@link Category}
 */

@Builder
@Value
public class CategoryDto implements Serializable {
    Long id;

    @NotBlank(message = "Category Name should be specified")
    String name;

    Long parentId;
}