package product.productservice.dto;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link product.productservice.entities.BookAuthorRelation}
 */

@Builder
@Value
public class BookAuthorDto implements Serializable {

    Long id;

    Long bookId;

    Long authorId;

}