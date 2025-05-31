package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookCategoryRelationDto;
import product.productservice.entities.BookCategoryRelation;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookCategoryRelationMapper {

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "productCategory", ignore = true)
    BookCategoryRelation toEntity(BookCategoryRelationDto bookCategoryRelationDto);

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "productCategory.id", target = "productCategoryId")
    BookCategoryRelationDto toDto(BookCategoryRelation bookCategoryRelation);

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "productCategory", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookCategoryRelation partialUpdate(BookCategoryRelationDto bookCategoryRelationDto, @MappingTarget BookCategoryRelation bookCategoryRelation);
}