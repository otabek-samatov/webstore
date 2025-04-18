package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import product.productservice.dto.ProductCategoryDto;
import product.productservice.entities.ProductCategory;
import product.productservice.mappers.ProductCategoryMapper;
import product.productservice.repositories.ProductCategoryRepository;
import product.productservice.validators.CustomValidator;

@RequiredArgsConstructor
@Service
public class ProductCategoryManager {

    private final ProductCategoryRepository repository;
    private final ProductCategoryMapper mapper;
    private final CustomValidator validator;

    public ProductCategory create(ProductCategoryDto dto) {
        return createOrUpdate(dto, true);
    }

    public ProductCategory update(ProductCategoryDto dto) {
        return createOrUpdate(dto, false);
    }

    private ProductCategory createOrUpdate(ProductCategoryDto dto, boolean createFlag) {
        validator.validate(dto);

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