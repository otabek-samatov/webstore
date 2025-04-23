package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import product.productservice.entities.BookAuthor;

import java.util.Collection;

public interface BookAuthorRepository extends JpaRepository<BookAuthor, Long> {

    @Query("SELECT COUNT(ba.id) FROM BookAuthor ba WHERE ba.id IN :ids")
    long countByIds(@Param("ids") Collection<Long> ids);
}