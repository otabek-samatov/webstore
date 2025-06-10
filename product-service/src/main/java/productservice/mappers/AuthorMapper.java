package productservice.mappers;

import org.mapstruct.*;
import productservice.dto.AuthorDto;
import productservice.entities.Author;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthorMapper {
    Author toEntity(AuthorDto authorDto);

    AuthorDto toDto(Author author);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Author partialUpdate(AuthorDto authorDto, @MappingTarget Author author);

    List<Author> toEntity(List<AuthorDto> authorDto);

    List<AuthorDto> toDto(List<Author> author);
}