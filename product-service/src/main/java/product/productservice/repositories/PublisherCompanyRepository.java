package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.PublisherCompany;

public interface PublisherCompanyRepository extends JpaRepository<PublisherCompany, Long> {
}