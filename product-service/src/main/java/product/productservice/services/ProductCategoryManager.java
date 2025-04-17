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
        return repository.save(mapper.toEntity(dto));
    }

    public ProductCategory update(@Valid ProductCategoryDto dto) {
        ProductCategory entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        mapper.update(dto, entity);
        return repository.save(entity);
    }
}