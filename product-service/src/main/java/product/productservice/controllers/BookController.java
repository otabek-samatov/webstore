package product.productservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    private final BookManager bookManager;
    private final BookMapper bookMapper;

    @GetMapping("/{bookId}")
    private ResponseEntity<BookDto> getBookById(@PathVariable Long bookId) {
        try {
            Book book = bookManager.findById(bookId);
            return ResponseEntity.ok(bookMapper.toDto(book));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{bookId}")
    private ResponseEntity<Void> deleteBookById(@PathVariable Long bookId) {
        bookManager.deleteById(bookId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<BookDto> createOrUpdate(@RequestBody BookDto dto) {
        try {
            Book book = bookManager.createOrUpdate(dto);
            return ResponseEntity.ok(bookMapper.toDto(book));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

}
