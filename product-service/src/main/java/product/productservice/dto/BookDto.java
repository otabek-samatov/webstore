package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Value;
import product.productservice.entities.Book;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * DTO for {@link Book}
 */
@Value
public class BookDto implements Serializable {
    Long id;
    @NotBlank(message = "Title should be specified")
    String title;
    String subtitle;
    LocalDate publicationDate;
    @NotBlank(message = "ISBN should be specified")
    String isbn;
    String description;
    @PositiveOrZero(message = "Price should be non negative")
    BigDecimal price;
    @NotBlank(message = "Language should be specified")
    String language;
    Set<String> bookImages;
}