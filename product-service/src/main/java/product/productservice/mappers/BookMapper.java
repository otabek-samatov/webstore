package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.Book;
import product.productservice.dto.BookDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookMapper {
    Book toEntity(BookDto bookDto);

    BookDto toDto(Book book);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Book partialUpdate(BookDto bookDto, @MappingTarget Book book);
}