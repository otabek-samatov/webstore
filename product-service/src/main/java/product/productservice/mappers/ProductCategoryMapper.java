package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.ProductCategory;
import product.productservice.dto.ProductCategoryDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductCategoryMapper {
    ProductCategory toEntity(ProductCategoryDto productCategoryDto);

    ProductCategoryDto toDto(ProductCategory productCategory);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ProductCategory partialUpdate(ProductCategoryDto productCategoryDto, @MappingTarget ProductCategory productCategory);
}