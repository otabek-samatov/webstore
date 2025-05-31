package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookAuthorDto;
import product.productservice.entities.BookAuthor;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookAuthorMapper {
    BookAuthor toEntity(BookAuthorDto bookAuthorDto);

    BookAuthorDto toDto(BookAuthor bookAuthor);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookAuthor update(BookAuthorDto bookAuthorDto, @MappingTarget BookAuthor bookAuthor);

    List<BookAuthor> toEntity(List<BookAuthorDto> bookAuthorDto);

    List<BookAuthorDto> toDto(List<BookAuthor> bookAuthor);
}