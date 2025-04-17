package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.ProductCategory;
import product.productservice.dto.ProductCategoryDto;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductCategoryMapper {
    @Mapping(source = "parentCategoryId", target = "parentCategory.id")
    ProductCategory toEntity(ProductCategoryDto productCategoryDto);

    @Mapping(source = "parentCategory.id", target = "parentCategoryId")
    ProductCategoryDto toDto(ProductCategory productCategory);

    @Mapping(source = "parentCategoryId", target = "parentCategory.id")
    ProductCategory update(ProductCategoryDto productCategoryDto, @MappingTarget ProductCategory productCategory);

    List<ProductCategory> toEntity(List<ProductCategoryDto> productCategoryDto);

    List<ProductCategoryDto> toDto(List<ProductCategory> productCategory);
}