package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import product.productservice.entities.Book;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT count(b.id) FROM Book b join b.authors a WHERE a.id = :authorsId")
    long countBooksByAuthorsId(Long authorsId);

    @Query("SELECT b FROM Book b join fetch b.authors a WHERE a.id = :authorsId")
    List<Book>  findBooksByAuthorsId(Long authorId);

    long countBooksByPublisherId(Long publisherCompanyId);

    List<Book> findBooksByPublisherId(Long publisherCompanyId);

    @Query("SELECT count(b.id) FROM Book b join b.categories c WHERE c.id = :categoryId")
    long countOfBooksByCategoriesId(Long categoryId);

    @Query("SELECT b FROM Book b join fetch b.categories c WHERE c.id = :categoryId")
    List<Book> findBooksByCategoriesId(Long categoryId);

    @Query("SELECT b.id FROM Book b WHERE b.isbn = :isbn")
    Long getIdByISBN(String isbn);

}