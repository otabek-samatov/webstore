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
    private final PublisherManager publisherManager;
    private final CustomValidator validator;
    private final AuthorManager authorManager;
    private final CategoryManager categoryManager;

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

        Book book;

        if (createFlag) {
            book = new Book();
        } else {
            book = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.partialUpdate(dto, book);

        book.setPublisher(publisherManager.getReferenceByID(dto.getPublisherId()));
        book.addAuthors(authorManager.getReferenceByIDs(dto.getAuthorIds()));
        book.addCategories(categoryManager.getReferenceByIDs(dto.getCategoryIds()));
        book.addBookImages(dto.getBookImages());

        return repository.save(book);
    }

    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Book with id " + id + " not found"));
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
