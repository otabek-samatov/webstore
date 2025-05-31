package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.BookAuthor;

public interface BookAuthorRepository extends JpaRepository<BookAuthor, Long> {
}