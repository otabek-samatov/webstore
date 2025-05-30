package product.productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import product.productservice.entities.ProductCategory;

import java.util.Collection;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

  long countByParentCategoryId(Long parentCategoryId);

  long countByIdIn(Collection<Long> ids);


  @Query("SELECT p.id FROM ProductCategory p WHERE p.name = :companyName")
  Long getIdByName(String companyName);



}