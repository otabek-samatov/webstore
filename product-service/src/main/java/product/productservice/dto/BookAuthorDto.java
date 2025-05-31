package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link product.productservice.entities.BookAuthor}
 */
@Builder
@Value
public class BookAuthorDto implements Serializable {
    Long id;
    String firstName;
    String middleName;
    @NotBlank(message = "Last Name should be specified")
    String lastName;
}