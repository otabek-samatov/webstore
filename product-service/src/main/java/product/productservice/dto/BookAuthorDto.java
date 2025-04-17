package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import product.productservice.entities.BookAuthor;

import java.io.Serializable;

/**
 * DTO for {@link BookAuthor}
 */
@Builder
@Value
public class BookAuthorDto implements Serializable {
    Long id;
    String firstName;
    String middleName;

    @NotBlank(message = "Name should be specified")
    String lastName;

    @Override
    public String toString() {
        return "BookAuthorDto{" +
                "id=" + id +
                ", lastName='" + lastName + '\'' +
                '}';
    }
}