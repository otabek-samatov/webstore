package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookDto;
import product.productservice.entities.Author;
import product.productservice.entities.Book;
import product.productservice.entities.Category;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookMapper {
    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "publisher", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Book toEntity(BookDto bookDto);

    @Mapping(target = "categoryIds", expression = "java(categoriesToCategoryIds(book.getCategories()))")
    @Mapping(target = "authorIds", expression = "java(authorsToAuthorIds(book.getAuthors()))")
    @Mapping(source = "publisher.id", target = "publisherId")
    BookDto toDto(Book book);

    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "publisher", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Book partialUpdate(BookDto bookDto, @MappingTarget Book book);

    default Set<Long> categoriesToCategoryIds(Set<Category> categories) {
        return categories != null ? categories.stream().map(Category::getId).collect(Collectors.toSet()) : null;
    }

    default Set<Long> authorsToAuthorIds(Set<Author> authors) {
        return authors != null ? authors.stream().map(Author::getId).collect(Collectors.toSet()) : null;
    }

    List<Book> toEntity(List<BookDto> bookDto);

    List<BookDto> toDto(List<Book> book);
}