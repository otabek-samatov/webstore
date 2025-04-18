package product.productservice.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import product.productservice.dto.BookDto;
import product.productservice.entities.Book;
import product.productservice.entities.BookAuthor;
import product.productservice.entities.ProductCategory;
import product.productservice.mappers.BookMapper;
import product.productservice.repositories.BookAuthorRepository;
import product.productservice.repositories.BookRepository;
import product.productservice.repositories.ProductCategoryRepository;
import product.productservice.repositories.PublisherCompanyRepository;
import product.productservice.validators.CustomValidator;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookManager {

    private final BookRepository repository;
    private final BookMapper mapper;
    private final BookAuthorRepository bookAuthorRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final PublisherCompanyRepository publisherCompanyRepository;
    private final CustomValidator validator;

    public Book create(BookDto dto) {
        return createOrUpdate(dto, true);
    }

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

        mapper.update(dto, entity);

        Set<BookAuthor> authors = dto.getAuthorIds().stream()
                .map(bookAuthorRepository::getReferenceById)
                .collect(Collectors.toSet());

        entity.setAuthors(authors);

        Set<ProductCategory> categories = dto.getCategoryIds().stream()
                .map(productCategoryRepository::getReferenceById)
                .collect(Collectors.toSet());

        entity.setCategories(categories);

        entity.setPublisherCompany(publisherCompanyRepository.getReferenceById(dto.getPublisherCompanyId()));

        return repository.save(entity);

    }


}
