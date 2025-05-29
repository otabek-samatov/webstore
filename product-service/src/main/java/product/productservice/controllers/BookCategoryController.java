package product.productservice.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import product.productservice.dto.BookDto;
import product.productservice.dto.ProductCategoryDto;
import product.productservice.entities.Book;
import product.productservice.entities.ProductCategory;
import product.productservice.mappers.BookMapper;
import product.productservice.mappers.ProductCategoryMapper;
import product.productservice.services.ProductCategoryManager;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "v1/books/category")
public class BookCategoryController {


    private final ProductCategoryManager manager;
    private final ProductCategoryMapper mapper;
    private final BookMapper bookMapper;

    @GetMapping("/{categoryId}")
    private ResponseEntity<ProductCategoryDto> getById(@PathVariable Long categoryId) {
        ProductCategory category = manager.findById(categoryId);
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
    public ResponseEntity<ProductCategoryDto> create(@RequestBody ProductCategoryDto dto) {
        ProductCategory c = manager.create(dto);
        return ResponseEntity.ok(mapper.toDto(c));
    }

    @PutMapping
    public ResponseEntity<ProductCategoryDto> update(@RequestBody ProductCategoryDto dto) {
        ProductCategory c = manager.update(dto);
        return ResponseEntity.ok(mapper.toDto(c));
    }
}
