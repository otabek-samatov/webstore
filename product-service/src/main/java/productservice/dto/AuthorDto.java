package productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import productservice.entities.Author;

import java.io.Serializable;

/**
 * DTO for {@link Author}
 */
@Builder
@Value
public class AuthorDto implements Serializable {
    Long id;
    String firstName;
    String middleName;
    @NotBlank(message = "Last Name should be specified")
    String lastName;
}