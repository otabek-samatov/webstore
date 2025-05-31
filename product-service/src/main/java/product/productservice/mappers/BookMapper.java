package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookDto;
import product.productservice.entities.Book;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookMapper {
    @Mapping(target = "publisher", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Book toEntity(BookDto bookDto);

    @Mapping(source = "publisher.id", target = "publisherId")
    BookDto toDto(Book book);

    @Mapping(target = "publisher", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Book partialUpdate(BookDto bookDto, @MappingTarget Book book);

    List<Book> toEntity(List<BookDto> bookDto);

    List<BookDto> toDto(List<Book> book);
}