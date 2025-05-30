package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import product.productservice.entities.PublisherCompany;

public interface PublisherCompanyRepository extends JpaRepository<PublisherCompany, Long> {

    @Query("SELECT p.id FROM PublisherCompany p WHERE p.name = :companyName")
    Long getIdByName(String companyName);
 }