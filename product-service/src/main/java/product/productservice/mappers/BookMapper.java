package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.Book;
import product.productservice.entities.BookAuthor;
import product.productservice.dto.BookDto;
import product.productservice.entities.ProductCategory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookMapper {
    @Mapping(source = "publisherCompanyId", target = "publisherCompany.id")
    Book toEntity(BookDto bookDto);

    @Mapping(target = "categoryIds", expression = "java(categoriesToCategoryIds(book.getCategories()))")
    @Mapping(target = "authorIds", expression = "java(authorsToAuthorIds(book.getAuthors()))")
    @Mapping(source = "publisherCompany.id", target = "publisherCompanyId")
    BookDto toDto(Book book);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "publisherCompanyId", target = "publisherCompany.id")
    Book partialUpdate(BookDto bookDto, @MappingTarget Book book);

    default Set<Long> categoriesToCategoryIds(Set<ProductCategory> categories) {
        return categories.stream().map(ProductCategory::getId).collect(Collectors.toSet());
    }

    default Set<Long> authorsToAuthorIds(Set<BookAuthor> authors) {
        return authors.stream().map(BookAuthor::getId).collect(Collectors.toSet());
    }

    List<Book> toEntity(List<BookDto> bookDto);

    List<BookDto> toDto(List<Book> book);
}