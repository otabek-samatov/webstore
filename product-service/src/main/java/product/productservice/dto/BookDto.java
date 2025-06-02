package product.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * DTO for {@link product.productservice.entities.Book}
 */
@Builder
@Value
public class BookDto implements Serializable {
    Long id;

    @NotBlank(message = "Title should be specified")
    String title;

    String subtitle;

    Long publisherId;

    @NotNull(message = "Publication date should be specified")
    LocalDate publicationDate;

    @NotBlank(message = "ISBN should be specified")
    String isbn;

    String description;

    @PositiveOrZero(message = "Price should be non negative")
    BigDecimal price;

    @NotBlank(message = "Language should be specified")
    String language;

    Set<Long> authorIds;
    Set<Long> categoryIds;
    Set<String> bookImages;
}