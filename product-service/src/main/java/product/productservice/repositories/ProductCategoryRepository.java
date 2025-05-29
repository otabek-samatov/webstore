package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import product.productservice.entities.ProductCategory;

import java.util.Collection;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {



  @Query("SELECT COUNT(pc.id) FROM ProductCategory pc WHERE pc.parentCategory.id = :parentId")
  long countByParentIds(@Param("parentId") Long parentId);
  }
}