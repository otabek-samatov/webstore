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
    public ResponseEntity<PublisherDto> getById(@PathVariable Long publisherId) {
        Publisher publisher = manager.findById(publisherId);
        return ResponseEntity.ok(mapper.toDto(publisher));
    }

    @GetMapping("/allbooks/{publisherId}")
    public ResponseEntity<List<BookDto>> getBooksByPublisherId(@PathVariable Long publisherId) {
        List<Book> books = manager.findBooksByPublisherId(publisherId);
        return ResponseEntity.ok(bookMapper.toDto(books));
    }

    @DeleteMapping("/{publisherId}")
    public ResponseEntity<Void> deleteById(@PathVariable Long publisherId) {
        manager.deleteById(publisherId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<PublisherDto> create(@RequestBody PublisherDto dto) {
        Publisher publisher = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(publisher));
    }

    @PutMapping
    public ResponseEntity<PublisherDto> update(@RequestBody PublisherDto dto) {
        Publisher publisher = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(publisher));
    }
}
