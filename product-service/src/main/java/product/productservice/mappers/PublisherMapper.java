package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.PublisherDto;
import product.productservice.entities.Publisher;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PublisherMapper {
    Publisher toEntity(PublisherDto publisherDto);

    PublisherDto toDto(Publisher publisher);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void partialUpdate(PublisherDto publisherDto, @MappingTarget Publisher publisher);

    List<Publisher> toEntity(List<PublisherDto> publisherDto);

    List<PublisherDto> toDto(List<Publisher> publisher);
}