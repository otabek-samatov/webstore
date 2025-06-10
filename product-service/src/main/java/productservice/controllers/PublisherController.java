package productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import productservice.dto.BookDto;
import productservice.dto.PublisherDto;
import productservice.entities.Book;
import productservice.entities.Publisher;
import productservice.mappers.BookMapper;
import productservice.mappers.PublisherMapper;
import productservice.services.PublisherManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/publisher")
public class PublisherController {

    private final PublisherManager manager;
    private final PublisherMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{publisherId}")
    private ResponseEntity<PublisherDto> getById(@PathVariable Long publisherId) {
        Publisher book = manager.findById(publisherId);
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
    public ResponseEntity<PublisherDto> create(@RequestBody PublisherDto dto) {
        Publisher book = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }

    @PutMapping
    public ResponseEntity<PublisherDto> update(@RequestBody PublisherDto dto) {
        Publisher book = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(book));
    }
}
