package productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import productservice.dto.BookDto;
import productservice.dto.CategoryDto;
import productservice.entities.Book;
import productservice.entities.Category;
import productservice.mappers.BookMapper;
import productservice.mappers.CategoryMapper;
import productservice.services.CategoryManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/category")
public class BookCategoryController {


    private final CategoryManager manager;
    private final CategoryMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{categoryId}")
    private ResponseEntity<CategoryDto> getById(@PathVariable Long categoryId) {
        Category category = manager.findById(categoryId);
        return ResponseEntity.ok(mapper.toDto(category));
    }

    @GetMapping("/allbooks/{categoryId}")
    private ResponseEntity<List<BookDto>> getBooksByAuthorId(@PathVariable Long categoryId) {
        List<Book> books = manager.findBooksByCategoryId(categoryId);
        return ResponseEntity.ok(bookMapper.toDto(books));
    }

    @DeleteMapping("/{categoryId}")
    private ResponseEntity<Void> deleteById(@PathVariable Long categoryId) {
        manager.deleteById(categoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryDto dto) {
        Category c = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(c));
    }

    @PutMapping
    public ResponseEntity<CategoryDto> update(@RequestBody CategoryDto dto) {
        Category c = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(c));
    }
}
