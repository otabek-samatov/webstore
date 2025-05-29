package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.BookAuthor;

import java.util.Collection;

public interface BookAuthorRepository extends JpaRepository<BookAuthor, Long> {

    long countByIdIn(Collection<Long> ids);
}
