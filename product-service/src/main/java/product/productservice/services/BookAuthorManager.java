package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import product.productservice.dto.BookAuthorDto;
import product.productservice.entities.BookAuthor;
import product.productservice.mappers.BookAuthorMapper;
import product.productservice.repositories.BookAuthorRepository;

@Validated
@RequiredArgsConstructor
@Service
public class BookAuthorManager {

    private final BookAuthorRepository repository;
    private final BookAuthorMapper mapper;

    public BookAuthor create(@Valid BookAuthorDto dto) {
        return repository.save(mapper.toEntity(dto));
    }

    public BookAuthor update(@Valid BookAuthorDto dto) {
        BookAuthor entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        mapper.update(dto, entity);
        return repository.save(entity);
    }
}
