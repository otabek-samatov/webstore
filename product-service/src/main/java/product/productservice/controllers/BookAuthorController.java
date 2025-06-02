package product.productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import product.productservice.dto.AuthorDto;
import product.productservice.dto.BookDto;
import product.productservice.entities.Author;
import product.productservice.entities.Book;
import product.productservice.mappers.AuthorMapper;
import product.productservice.mappers.BookMapper;
import product.productservice.services.AuthorManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/author")
public class BookAuthorController {

    private final AuthorManager manager;
    private final AuthorMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{authorId}")
    private ResponseEntity<AuthorDto> getById(@PathVariable Long authorId) {
        Author author = manager.findById(authorId);
        return ResponseEntity.ok(mapper.toDto(author));
    }

    @GetMapping("/allbooks/{authorId}")
    private ResponseEntity<List<BookDto>> getBooksByAuthorId(@PathVariable Long authorId) {
        List<Book> books = manager.findBooksByAuthorId(authorId);
        return ResponseEntity.ok(bookMapper.toDto(books));
    }

    @DeleteMapping("/{authorId}")
    private ResponseEntity<Void> deleteById(@PathVariable Long authorId) {
        manager.deleteById(authorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<AuthorDto> create(@RequestBody AuthorDto dto) {
        Author author = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(author));
    }

    @PutMapping
    public ResponseEntity<AuthorDto> update(@RequestBody AuthorDto dto) {
        Author author = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(author));
    }
}
