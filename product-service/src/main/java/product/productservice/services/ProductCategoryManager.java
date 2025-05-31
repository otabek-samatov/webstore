package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import product.productservice.dto.ProductCategoryDto;
import product.productservice.entities.Book;
import product.productservice.entities.ProductCategory;
import product.productservice.mappers.ProductCategoryMapper;
import product.productservice.repositories.BookRepository;
import product.productservice.repositories.ProductCategoryRepository;
import product.productservice.validators.CustomValidator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductCategoryManager {

    private final ProductCategoryRepository repository;
    private final ProductCategoryMapper mapper;
    private final CustomValidator validator;
    private final BookRepository bookRepository;

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

        if (dto.getParentId() != null) {
            entity.setParent(repository.getReferenceById(dto.getParentId()));
        } else {
            entity.setParent(null);
        }

        return repository.save(entity);
    }


    public Set<ProductCategory> getReferenceByIDs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids collection cannot be null or empty");
        }

        if (ids.size() != repository.countByIdIn(ids)) {

            throw new EntityNotFoundException("Some of the categories with ids " + ids + " not found");
        }

        return ids.stream()
                .map(repository::getReferenceById)
                .collect(Collectors.toSet());
    }

    public ProductCategory findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Product Category with id " + id + " not found"));
    }

    public List<Book> findBooksByCategoryId(Long id) {
        return bookRepository.findBooksByCategoriesId(id);
    }

    public void deleteById(Long id) {
        long count = bookRepository.countOfBooksByCategoriesId(id);
        if (count > 0) {
            throw new RuntimeException(count + " books use this category. Cannot delete category.");
        }

        count = repository.countByParentCategoryId(id);
        if (count > 0) {
            throw new RuntimeException(count + " categories use this author. Cannot delete category.");
        }

        repository.deleteById(id);
    }

    public boolean createByFields(String categoryName, String parentCategoryName) {

        Long id = repository.getIdByName(categoryName);
        if (id != null) {
            return false;
        }

        Long parentId = null;
        if (StringUtils.hasText(parentCategoryName)){
            parentId = repository.getIdByName(parentCategoryName);
        }

        ProductCategoryDto dto = ProductCategoryDto.builder()
                .name(categoryName)
                .parentId(parentId)
                .build();

        create(dto);

        return true;
    }

}