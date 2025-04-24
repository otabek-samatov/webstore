package product.productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import product.productservice.dto.PublisherCompanyDto;
import product.productservice.dto.BookDto;
import product.productservice.entities.Book;
import product.productservice.entities.PublisherCompany;
import product.productservice.mappers.BookMapper;
import product.productservice.mappers.PublisherCompanyMapper;
import product.productservice.services.PublisherCompanyManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/publisher")
public class Publisher {

    private final PublisherCompanyManager manager;
    private final PublisherCompanyMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{publisherId}")
    private ResponseEntity<PublisherCompanyDto> getById(@PathVariable Long publisherId) {
        PublisherCompany book = manager.findById(publisherId);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @GetMapping("/allbooks/{publisherId}")
    private ResponseEntity<List<BookDto>> getBooksByPublisherId(@PathVariable Long publisherId) {
        List<Book> books = manager.findBooksByPublisherId(publisherId);
        return ResponseEntity.ok(bookMapper.toDto(books));
    }

    @DeleteMapping("/{publisherId}")
    private ResponseEntity<Void> deleteById(@PathVariable Long publisherId) {
        manager.deleteById(publisherId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<PublisherCompanyDto> create(@RequestBody PublisherCompanyDto dto) {
        PublisherCompany book = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @PutMapping
    public ResponseEntity<PublisherCompanyDto> update(@RequestBody PublisherCompanyDto dto) {
        PublisherCompany book = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }
}
