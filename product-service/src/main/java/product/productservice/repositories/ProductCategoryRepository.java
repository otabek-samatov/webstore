package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.ProductCategory;

import java.util.Collection;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

  long countByParentCategoryId(Long parentCategoryId);

  long countByIdIn(Collection<Long> ids);


}