package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import product.productservice.dto.BookAuthorDto;
import product.productservice.entities.Book;
import product.productservice.entities.BookAuthor;
import product.productservice.mappers.BookAuthorMapper;
import product.productservice.repositories.BookAuthorRepository;
import product.productservice.repositories.BookRepository;
import product.productservice.validators.CustomValidator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookAuthorManager {

    private final BookAuthorRepository repository;
    private final BookRepository bookRepository;
    private final BookAuthorMapper mapper;
    private final CustomValidator validator;

    @Transactional
    public BookAuthor create(BookAuthorDto dto) {
        return createOrUpdate(dto, true);
    }

    @Transactional
    public BookAuthor update(BookAuthorDto dto) {
        return createOrUpdate(dto, false);
    }

    private BookAuthor createOrUpdate(BookAuthorDto dto, boolean createFlag) {
        validator.validate(dto);

        BookAuthor entity;
        if (createFlag) {
            entity = new BookAuthor();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.partialUpdate(dto, entity);

        return repository.save(entity);
    }

    public Set<BookAuthor> getReferenceByIDs(Collection<Long> ids){
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids collection cannot be null or empty");
        }

        if (ids.size() != repository.countByIdIn(ids)){
            throw new EntityNotFoundException("Some of the authors with ids " + ids + " not found");
        }

        return ids.stream()
                .map(repository::getReferenceById)
                .collect(Collectors.toSet());
    }

    public BookAuthor findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("BookAuthor with id " + id + " not found"));
    }

    public void deleteById(Long id) {
        long bookCount = bookRepository.countBooksByAuthorsId(id);
        if (bookCount > 0) {
            throw new RuntimeException(bookCount +  " books use this author. Cannot delete book Author.");
        }

        repository.deleteById(id);
    }

    public List<Book> findBooksByAuthorId(Long id) {
        return bookRepository.findBooksByAuthorsId(id);
    }
}
