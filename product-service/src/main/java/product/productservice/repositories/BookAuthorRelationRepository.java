package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.BookAuthorRelation;

public interface BookAuthorRelationRepository extends JpaRepository<BookAuthorRelation, Long> {
}