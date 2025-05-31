package product.productservice.dto;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link product.productservice.entities.BookCategoryRelation}
 */
@Builder
@Value
public class BookCategoryRelationDto implements Serializable {

    Long id;

    Long bookId;
    Long productCategoryId;
}