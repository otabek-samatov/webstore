package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.dto.PublisherCompanyDto;
import product.productservice.entities.PublisherCompany;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PublisherCompanyMapper {
    PublisherCompany toEntity(PublisherCompanyDto publisherCompanyDto);

    PublisherCompanyDto toDto(PublisherCompany publisherCompany);

    void update(PublisherCompanyDto publisherCompanyDto, @MappingTarget PublisherCompany publisherCompany);

    List<PublisherCompany> toEntity(List<PublisherCompanyDto> publisherCompanyDto);

    List<PublisherCompanyDto> toDto(List<PublisherCompany> publisherCompany);
}