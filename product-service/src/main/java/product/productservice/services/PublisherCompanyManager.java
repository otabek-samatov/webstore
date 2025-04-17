package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import product.productservice.dto.PublisherCompanyDto;
import product.productservice.entities.PublisherCompany;
import product.productservice.mappers.PublisherCompanyMapper;
import product.productservice.repositories.PublisherCompanyRepository;

@Validated
@RequiredArgsConstructor
@Service
public class PublisherCompanyManager {

    private final PublisherCompanyRepository repository;
    private final PublisherCompanyMapper mapper;

    public PublisherCompany create(@Valid PublisherCompanyDto dto) {
        return createOrUpdate(dto, true);
    }

    public PublisherCompany update(@Valid PublisherCompanyDto dto) {
        return createOrUpdate(dto, false);
    }

    private PublisherCompany createOrUpdate(@Valid PublisherCompanyDto dto, boolean createFlag) {
        PublisherCompany entity;
        if (createFlag) {
            entity = new PublisherCompany();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }
        mapper.update(dto, entity);
        return repository.save(entity);
    }
}
