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
    private final PublisherCompanyManager publisherCompanyManager;
    private final CustomValidator validator;

    @Transactional
    public Book create(BookDto dto) {
        return createOrUpdate(dto, true);
    }

    @Transactional
    public Book update(BookDto dto) {
        return createOrUpdate(dto, false);
    }

    private Book createOrUpdate(BookDto dto, boolean createFlag) {

        validator.validate(dto);

        Book entity;

        if (createFlag) {
            entity = new Book();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.partialUpdate(dto, entity);

        entity.setPublisher(publisherCompanyManager.getReferenceByID(dto.getPublisherId()));

        return repository.save(entity);
    }

    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Book with id " + id + " not found"));
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
