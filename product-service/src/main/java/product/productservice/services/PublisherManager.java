package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import product.productservice.dto.PublisherDto;
import product.productservice.entities.Book;
import product.productservice.entities.Publisher;
import product.productservice.mappers.PublisherMapper;
import product.productservice.repositories.BookRepository;
import product.productservice.repositories.PublisherRepository;
import product.productservice.validators.CustomValidator;

import java.util.List;

@RequiredArgsConstructor
@Service
public class PublisherManager {

    private final PublisherRepository repository;
    private final BookRepository bookRepository;
    private final PublisherMapper mapper;
    private final CustomValidator validator;

    public Publisher create(PublisherDto dto) {
        return createOrUpdate(dto, true);
    }

    public Publisher update(PublisherDto dto) {
        return createOrUpdate(dto, false);
    }

    private Publisher createOrUpdate(PublisherDto dto, boolean createFlag) {
        validator.validate(dto);

        Publisher entity;
        if (createFlag) {
            entity = new Publisher();
        } else {
            entity = repository.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(dto + " not found"));
        }
        mapper.partialUpdate(dto, entity);
        return repository.save(entity);
    }

    public Publisher getReferenceByID(Long id){
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null ");
        }

        if (!repository.existsById(id)){
            throw new EntityNotFoundException("Publisher Company with id " + id + " not found");
        }

        return repository.getReferenceById(id);
    }

    public Publisher findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Publisher with id " + id + " not found"));
    }

    public void deleteById(Long id) {
        long bookCount = bookRepository.countBooksByPublisherId(id);
        if (bookCount > 0) {
            throw new RuntimeException(bookCount +  " books use this publisher company. Cannot delete Publisher Company.");
        }

        repository.deleteById(id);
    }

    public List<Book> findBooksByPublisherId(Long id) {
        return bookRepository.findBooksByPublisherId(id);
    }
}
