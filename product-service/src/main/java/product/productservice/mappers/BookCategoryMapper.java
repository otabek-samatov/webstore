package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookCategoryDto;
import product.productservice.entities.BookCategory;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookCategoryMapper {

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "category", ignore = true)
    BookCategory toEntity(BookCategoryDto bookCategoryDto);

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "book.id", target = "bookId")
    BookCategoryDto toDto(BookCategory bookCategory);

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "category", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookCategory partialUpdate(BookCategoryDto bookCategoryDto, @MappingTarget BookCategory bookCategory);
}