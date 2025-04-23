package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import product.productservice.dto.PublisherCompanyDto;
import product.productservice.entities.PublisherCompany;
import product.productservice.mappers.PublisherCompanyMapper;
import product.productservice.repositories.PublisherCompanyRepository;
import product.productservice.validators.CustomValidator;

@RequiredArgsConstructor
@Service
public class PublisherCompanyManager {

    private final PublisherCompanyRepository repository;
    private final PublisherCompanyMapper mapper;
    private final CustomValidator validator;

    public PublisherCompany create(PublisherCompanyDto dto) {
        return createOrUpdate(dto, true);
    }

    public PublisherCompany update(PublisherCompanyDto dto) {
        return createOrUpdate(dto, false);
    }

    private PublisherCompany createOrUpdate(PublisherCompanyDto dto, boolean createFlag) {
        validator.validate(dto);

        PublisherCompany entity;
        if (createFlag) {
            entity = new PublisherCompany();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }
        mapper.update(dto, entity);
        return repository.save(entity);
    }

    public PublisherCompany getReferenceByID(Long id){
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null ");
        }

        if (!repository.existsById(id)){
            throw new EntityNotFoundException("Publisher Company with id " + id + " not found");
        }

        return repository.getReferenceById(id);
    }
}
