package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import product.productservice.dto.ProductCategoryDto;
import product.productservice.entities.ProductCategory;
import product.productservice.mappers.ProductCategoryMapper;
import product.productservice.repositories.ProductCategoryRepository;

@Validated
@RequiredArgsConstructor
@Service
public class ProductCategoryManager {

    private final ProductCategoryRepository repository;
    private final ProductCategoryMapper mapper;

    public ProductCategory create(@Valid ProductCategoryDto dto) {
        return createOrUpdate(dto, true);
    }

    public ProductCategory update(@Valid ProductCategoryDto dto) {
        return createOrUpdate(dto, false);
    }

    private ProductCategory createOrUpdate(@Valid ProductCategoryDto dto, boolean createFlag) {
        ProductCategory entity;
        if (createFlag) {
            entity = new ProductCategory();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.update(dto, entity);
        entity.setParentCategory(repository.getReferenceById(dto.getParentCategoryId()));

        return repository.save(entity);
    }
}