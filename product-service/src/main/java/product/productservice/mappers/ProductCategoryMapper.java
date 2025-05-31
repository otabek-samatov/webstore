package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.ProductCategoryDto;
import product.productservice.entities.ProductCategory;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductCategoryMapper {
    @Mapping(source = "parentId", target = "parent.id")
    ProductCategory toEntity(ProductCategoryDto productCategoryDto);

    @Mapping(source = "parent.id", target = "parentId")
    ProductCategoryDto toDto(ProductCategory productCategory);

    @Mapping(source = "parentId", target = "parent.id")
    ProductCategory update(ProductCategoryDto productCategoryDto, @MappingTarget ProductCategory productCategory);

    List<ProductCategory> toEntity(List<ProductCategoryDto> productCategoryDto);

    List<ProductCategoryDto> toDto(List<ProductCategory> productCategory);
}