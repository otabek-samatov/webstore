package productservice.mappers;

import org.mapstruct.*;
import productservice.dto.CategoryDto;
import productservice.entities.Category;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {
    @Mapping(target = "parent", ignore = true)
    Category toEntity(CategoryDto categoryDto);

    @Mapping(source = "parent.id", target = "parentId")
    CategoryDto toDto(Category category);

    @Mapping(target = "parent", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Category partialUpdate(CategoryDto categoryDto, @MappingTarget Category category);

    List<Category> toEntity(List<CategoryDto> categoryDto);

    List<CategoryDto> toDto(List<Category> category);
}