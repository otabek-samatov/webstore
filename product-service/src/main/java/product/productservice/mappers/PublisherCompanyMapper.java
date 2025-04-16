package product.productservice.mappers;

import org.mapstruct.*;
import product.productservice.entities.PublisherCompany;
import product.productservice.dto.PublisherCompanyDto;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PublisherCompanyMapper {
    PublisherCompany toEntity(PublisherCompanyDto publisherCompanyDto);

    PublisherCompanyDto toDto(PublisherCompany publisherCompany);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    PublisherCompany partialUpdate(PublisherCompanyDto publisherCompanyDto, @MappingTarget PublisherCompany publisherCompany);
}