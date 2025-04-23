package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import product.productservice.entities.ProductCategory;

import java.util.Collection;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

  @Query("SELECT COUNT(pc.id) FROM ProductCategory pc WHERE pc.id IN :ids")
  long countByIds(@Param("ids") Collection<Long> ids);
  }