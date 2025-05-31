package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookAuthorRelationDto;
import product.productservice.entities.BookAuthorRelation;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookAuthorRelationMapper {

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "bookAuthor", ignore = true)
    BookAuthorRelation toEntity(BookAuthorRelationDto bookAuthorRelationDto);

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "bookAuthor.id", target = "bookAuthorId")
    BookAuthorRelationDto toDto(BookAuthorRelation bookAuthorRelation);

    @Mapping(target = "book", ignore = true)
    @Mapping(target = "bookAuthor", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookAuthorRelation partialUpdate(BookAuthorRelationDto bookAuthorRelationDto, @MappingTarget BookAuthorRelation bookAuthorRelation);
}