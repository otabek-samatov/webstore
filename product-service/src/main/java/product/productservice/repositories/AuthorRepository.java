package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import product.productservice.entities.Author;

import java.util.Collection;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    long countByIdIn(Collection<Long> ids);

    @Query("SELECT p.id FROM Author p WHERE p.firstName = :firstName AND p.lastName = :lastName AND p.middleName = :middleName")
    Long getIdByNames(String firstName, String lastName, String middleName);


}
