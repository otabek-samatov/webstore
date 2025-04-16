package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.BookAuthor;
import product.productservice.dto.BookAuthorDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookAuthorMapper {
    BookAuthor toEntity(BookAuthorDto bookAuthorDto);

    BookAuthorDto toDto(BookAuthor bookAuthor);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookAuthor partialUpdate(BookAuthorDto bookAuthorDto, @MappingTarget BookAuthor bookAuthor);
}