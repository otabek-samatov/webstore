package product.productservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import product.productservice.dto.BookDto;
import product.productservice.services.BookManager;


@RequiredArgsConstructor
@RestController
@RequestMapping(value="v1/books/book")
public class BookController {

    private final BookManager bookmanager;

    @GetMapping("/{bookId}")
    private ResponseEntity<BookDto> findById(@PathVariable Long bookId) {
       return null;

    }
}
