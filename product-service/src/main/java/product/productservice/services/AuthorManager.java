package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import product.productservice.dto.AuthorDto;
import product.productservice.entities.Author;
import product.productservice.entities.Book;
import product.productservice.mappers.AuthorMapper;
import product.productservice.repositories.AuthorRepository;
import product.productservice.repositories.BookRepository;
import product.productservice.validators.CustomValidator;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AuthorManager {

    private final AuthorRepository repository;
    private final BookRepository bookRepository;
    private final AuthorMapper mapper;
    private final CustomValidator validator;

    @Transactional
    public Author create(AuthorDto dto) {
        return createOrUpdate(dto, true);
    }

    @Transactional
    public Author update(AuthorDto dto) {
        return createOrUpdate(dto, false);
    }

    private Author createOrUpdate(AuthorDto dto, boolean createFlag) {
        validator.validate(dto);

        Author entity;
        if (createFlag) {
            entity = new Author();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }

        mapper.partialUpdate(dto, entity);

        return repository.save(entity);
    }

    public Set<Author> getReferenceByIDs(Collection<Long> ids){
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

    public Author findById(Long id) {
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
