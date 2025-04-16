package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.ProductCategory;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
  }