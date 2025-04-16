package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.ProductCategory;
import product.productservice.dto.ProductCategoryDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductCategoryMapper {
    @Mapping(source = "parentCategoryId", target = "parentCategory.id")
    ProductCategory toEntity(ProductCategoryDto productCategoryDto);

    @Mapping(source = "parentCategory.id", target = "parentCategoryId")
    ProductCategoryDto toDto(ProductCategory productCategory);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "parentCategoryId", target = "parentCategory.id")
    ProductCategory partialUpdate(ProductCategoryDto productCategoryDto, @MappingTarget ProductCategory productCategory);
}