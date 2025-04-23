package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import product.productservice.dto.BookAuthorDto;
import product.productservice.entities.BookAuthor;
import product.productservice.mappers.BookAuthorMapper;
import product.productservice.repositories.BookAuthorRepository;
import product.productservice.validators.CustomValidator;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookAuthorManager {

    private final BookAuthorRepository repository;
    private final BookAuthorMapper mapper;
    private final CustomValidator validator;

    public BookAuthor create(BookAuthorDto dto) {
        return createOrUpdate(dto, true);
    }

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

        mapper.update(dto, entity);

        return repository.save(entity);
    }

    public Set<BookAuthor> getReferenceByIDs(Collection<Long> ids){
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids collection cannot be null or empty");
        }

        if (ids.size() != repository.countByIds(ids)){
            throw new EntityNotFoundException("Some of the authors with ids " + ids + " not found");
        }

        return ids.stream()
                .map(repository::getReferenceById)
                .collect(Collectors.toSet());
    }
}
