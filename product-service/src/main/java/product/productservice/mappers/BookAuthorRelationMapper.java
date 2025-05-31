package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.BookAuthorRelationDto;
import product.productservice.entities.BookAuthorRelation;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookAuthorRelationMapper {

    @Mapping(source = "bookId", target = "book.id")
    @Mapping(source = "bookAuthorId", target = "bookAuthor.id")
    BookAuthorRelation toEntity(BookAuthorRelationDto bookAuthorRelationDto);

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "bookAuthor.id", target = "bookAuthorId")
    BookAuthorRelationDto toDto(BookAuthorRelation bookAuthorRelation);

    @Mapping(source = "bookId", target = "book.id")
    @Mapping(source = "bookAuthorId", target = "bookAuthor.id")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    BookAuthorRelation update(BookAuthorRelationDto bookAuthorRelationDto, @MappingTarget BookAuthorRelation bookAuthorRelation);
}