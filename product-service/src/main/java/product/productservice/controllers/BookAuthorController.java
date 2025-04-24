package product.productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import product.productservice.dto.BookAuthorDto;
import product.productservice.dto.BookDto;
import product.productservice.entities.Book;
import product.productservice.entities.BookAuthor;
import product.productservice.mappers.BookAuthorMapper;
import product.productservice.mappers.BookMapper;
import product.productservice.services.BookAuthorManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/author")
public class BookAuthorController {

    private final BookAuthorManager manager;
    private final BookAuthorMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{authorId}")
    private ResponseEntity<BookAuthorDto> getBookById(@PathVariable Long authorId) {
        BookAuthor book = manager.findById(authorId);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @GetMapping("/allbooks/{authorId}")
    private ResponseEntity<List<BookDto>> getBooksByAuthorId(@PathVariable Long authorId) {
        List<Book> books = manager.findBooksByAuthorId(authorId);
        return ResponseEntity.ok(bookMapper.toDto(books));
    }

    @DeleteMapping("/{authorId}")
    private ResponseEntity<Void> deleteBookById(@PathVariable Long authorId) {
        manager.deleteById(authorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<BookAuthorDto> create(@RequestBody BookAuthorDto dto) {
        BookAuthor book = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @PutMapping
    public ResponseEntity<BookAuthorDto> update(@RequestBody BookAuthorDto dto) {
        BookAuthor book = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }
}
