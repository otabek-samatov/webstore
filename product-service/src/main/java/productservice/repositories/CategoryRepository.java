package productservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import productservice.entities.Category;

import java.util.Collection;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  long countByParentId(Long parentCategoryId);

  long countByIdIn(Collection<Long> ids);


  @Query("SELECT p.id FROM Category p WHERE p.name = :companyName")
  Long getIdByName(String companyName);



}