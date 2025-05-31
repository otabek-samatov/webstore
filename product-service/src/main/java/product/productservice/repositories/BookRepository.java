package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import product.productservice.entities.Book;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT count(ba.id) FROM BookAuthor ba WHERE ba.author.id = :authorsId")
    long countBooksByAuthorsId(Long authorsId);

    @Query("SELECT ba.book FROM BookAuthor ba WHERE ba.author.id = :authorsId")
    List<Book>  findBooksByAuthorsId(Long authorId);

    long countBooksByPublisherId(Long publisherCompanyId);

    List<Book> findBooksByPublisherId(Long publisherCompanyId);

    @Query("SELECT count(bc.id) FROM BookCategory bc WHERE bc.category.id = :categoryId")
    long countOfBooksByCategoriesId(Long categoryId);

    @Query("SELECT bc.book FROM BookCategory bc WHERE bc.category.id = :categoryId")
    List<Book> findBooksByCategoriesId(Long categoryId);

}