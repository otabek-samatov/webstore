package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import product.productservice.dto.BookDto;
import product.productservice.entities.Author;
import product.productservice.entities.Book;
import product.productservice.entities.Category;
import product.productservice.entities.Publisher;
import product.productservice.mappers.BookMapper;
import product.productservice.repositories.BookRepository;
import product.productservice.validators.CustomValidator;

import java.util.Set;

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

        Publisher p = publisherManager.getReferenceByID(dto.getPublisherId());
        if (p != null) {
            book.setPublisher(p);
        }

        Set<Author> authors = authorManager.getReferenceByIDs(dto.getAuthorIds());
        if (authors != null) {
            book.setAuthors(authors);
        }

        Set<Category> categories = categoryManager.getReferenceByIDs(dto.getCategoryIds());
        if (categories != null) {
            book.setCategories(categories);
        }

        Set<String> images = dto.getBookImages();
        if (images != null) {
            book.setBookImages(images);
        }

        return repository.save(book);
    }

    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Book with id " + id + " not found"));
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
