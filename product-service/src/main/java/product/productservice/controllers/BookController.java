package product.productservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import product.productservice.dto.BookDto;
import product.productservice.entities.Book;
import product.productservice.mappers.BookMapper;
import product.productservice.services.BookManager;


@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/book")
public class BookController {

    private final BookManager manager;
    private final BookMapper mapper;

    @GetMapping("/{bookId}")
    private ResponseEntity<BookDto> getBookById(@PathVariable Long bookId) {
        Book book = manager.findById(bookId);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @DeleteMapping("/{bookId}")
    private ResponseEntity<Void> deleteBookById(@PathVariable Long bookId) {
        manager.deleteById(bookId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<BookDto> create(@RequestBody BookDto dto) {
        Book book = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @PutMapping
    public ResponseEntity<BookDto> update(@RequestBody BookDto dto) {
        Book book = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

}
