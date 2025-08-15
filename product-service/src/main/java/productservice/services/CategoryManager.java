package productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import productservice.dto.CategoryDto;
import productservice.entities.Book;
import productservice.entities.Category;
import productservice.mappers.CategoryMapper;
import productservice.repositories.BookRepository;
import productservice.repositories.CategoryRepository;
import productservice.validators.CustomValidator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CategoryManager {

    private final CategoryRepository repository;
    private final CategoryMapper mapper;
    private final CustomValidator validator;
    private final BookRepository bookRepository;

    public Category create(CategoryDto dto) {
        return createOrUpdate(dto, true);
    }

    public Category update(CategoryDto dto) {
        return createOrUpdate(dto, false);
    }

    private Category createOrUpdate(CategoryDto dto, boolean createFlag) {
        validator.validate(dto);

        Category entity;
        if (createFlag) {
            entity = new Category();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.partialUpdate(dto, entity);

        if (dto.getParentId() != null) {
            entity.setParent(repository.getReferenceById(dto.getParentId()));
        } else {
            entity.setParent(null);
        }

        return repository.save(entity);
    }


    public Set<Category> getReferenceByIDs(Collection<Long> ids) {
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

    public Category findById(Long id) {
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

        count = repository.countByParentId(id);
        if (count > 0) {
            throw new RuntimeException(count + " categories use this category. Cannot delete category.");
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

        CategoryDto dto = CategoryDto.builder()
                .name(categoryName)
                .parentId(parentId)
                .build();

        create(dto);

        return true;
    }

}