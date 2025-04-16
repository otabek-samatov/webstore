package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import product.productservice.entities.Book;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
}