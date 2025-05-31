package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookAuthorDto;
import product.productservice.entities.BookAuthor;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookAuthorMapper {
    @Mapping(target = "book", ignore = true)
    @Mapping(target = "author", ignore = true)
    BookAuthor toEntity(BookAuthorDto bookAuthorDto);

    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "book.id", target = "bookId")
    BookAuthorDto toDto(BookAuthor bookAuthor);

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "author", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookAuthor partialUpdate(BookAuthorDto bookAuthorDto, @MappingTarget BookAuthor bookAuthor);
}