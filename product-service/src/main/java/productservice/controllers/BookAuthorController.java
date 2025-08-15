package productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import productservice.dto.AuthorDto;
import productservice.dto.BookDto;
import productservice.entities.Author;
import productservice.entities.Book;
import productservice.mappers.AuthorMapper;
import productservice.mappers.BookMapper;
import productservice.services.AuthorManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/author")
public class BookAuthorController {

    private final AuthorManager manager;
    private final AuthorMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{authorId}")
    public ResponseEntity<AuthorDto> getById(@PathVariable Long authorId) {
        Author author = manager.findById(authorId);
        return ResponseEntity.ok(mapper.toDto(author));
    }

    @GetMapping("/allbooks/{authorId}")
    public ResponseEntity<List<BookDto>> getBooksByAuthorId(@PathVariable Long authorId) {
        List<Book> books = manager.findBooksByAuthorId(authorId);
        return ResponseEntity.ok(bookMapper.toDto(books));
    }

    @DeleteMapping("/{authorId}")
    public ResponseEntity<Void> deleteById(@PathVariable Long authorId) {
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
