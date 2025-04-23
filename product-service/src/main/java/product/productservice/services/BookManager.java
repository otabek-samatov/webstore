package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import product.productservice.dto.BookDto;
import product.productservice.entities.Book;
import product.productservice.mappers.BookMapper;
import product.productservice.repositories.BookRepository;
import product.productservice.validators.CustomValidator;

@RequiredArgsConstructor
@Service
public class BookManager {

    private final BookRepository repository;
    private final BookMapper mapper;
    private final ProductCategoryManager productCategoryManager;
    private final PublisherCompanyManager publisherCompanyManager;
    private final BookAuthorManager bookAuthorManager;
    private final CustomValidator validator;

    @Transactional
    public Book createOrUpdate(BookDto dto) {

        validator.validate(dto);

        Book entity;

        if (dto.getId() == null) {
            entity = new Book();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.update(dto, entity);

        entity.setPublisherCompany(publisherCompanyManager.getReferenceByID(dto.getPublisherCompanyId()));

        entity.setAuthors(bookAuthorManager.getReferenceByIDs(dto.getAuthorIds()));

        entity.setCategories(productCategoryManager.getReferenceByIDs(dto.getCategoryIds()));

        return repository.save(entity);
    }

    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Book with id " + id + " not found"));
    }


    public void deleteById(Long id) {
        repository.deleteById(id);
    }


}
