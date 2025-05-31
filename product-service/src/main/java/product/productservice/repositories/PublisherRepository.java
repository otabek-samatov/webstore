package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import product.productservice.entities.Publisher;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    @Query("SELECT p.id FROM Publisher p WHERE p.name = :companyName")
    Long getIdByName(String companyName);
 }