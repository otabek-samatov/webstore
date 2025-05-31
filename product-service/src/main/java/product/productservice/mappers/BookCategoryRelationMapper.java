package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookCategoryRelationDto;
import product.productservice.entities.BookCategoryRelation;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookCategoryRelationMapper {

    @Mapping(source = "bookId", target = "book.id")
    @Mapping(source = "productCategoryId", target = "productCategory.id")
    BookCategoryRelation toEntity(BookCategoryRelationDto bookCategoryRelationDto);

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "productCategory.id", target = "productCategoryId")
    BookCategoryRelationDto toDto(BookCategoryRelation bookCategoryRelation);

    @Mapping(source = "bookId", target = "book.id")
    @Mapping(source = "productCategoryId", target = "productCategory.id")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookCategoryRelation partialUpdate(BookCategoryRelationDto bookCategoryRelationDto, @MappingTarget BookCategoryRelation bookCategoryRelation);
}