package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import product.productservice.entities.Book;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    long countBooksByAuthorsId(Long authorsId);

    List<Book>  findBooksByAuthorsId(@Param("authorId") Long authorId);

    long countBooksByPublisherCompanyId(Long publisherCompanyId);


}