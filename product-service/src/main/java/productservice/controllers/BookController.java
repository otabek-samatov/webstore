package productservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import productservice.dto.BookDto;
import productservice.entities.Book;
import productservice.mappers.BookMapper;
import productservice.services.BookManager;


@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/book")
public class BookController {

    private final BookManager manager;
    private final BookMapper mapper;

    @GetMapping("/{bookId}")
    private ResponseEntity<BookDto> getById(@PathVariable Long bookId) {
        Book book = manager.findById(bookId);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @DeleteMapping("/{bookId}")
    private ResponseEntity<Void> deleteById(@PathVariable Long bookId) {
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
