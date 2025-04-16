package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;
import product.productservice.entities.BookAuthor;

import java.io.Serializable;

/**
 * DTO for {@link BookAuthor}
 */
@Value
public class BookAuthorDto implements Serializable {
    Long id;
    String firstName;
    String middleName;
    @NotBlank(message = "Name should be specified")
    String lastName;
}