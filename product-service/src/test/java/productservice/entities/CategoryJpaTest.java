package productservice.entities;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import productservice.repositories.CategoryRepository;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CategoryJpaTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testSaveAndFindCategory() {
        Category category = new Category();
        category.setName("Fiction");

        Category saved = categoryRepository.save(category);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);

        Optional<Category> found = categoryRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Fiction", found.get().getName());
        assertNull(found.get().getParent());
    }

    @Test
    void testSaveCategoryWithNullName_ThrowsException() {
        Category category = new Category();

        assertThrows(ConstraintViolationException.class, () -> {
            categoryRepository.save(category);
            entityManager.flush();
        });
    }

    @Test
    void testSaveCategoryWithEmptyName_ThrowsException() {
        Category category = new Category();
        category.setName("");

        assertThrows(ConstraintViolationException.class, () -> {
            categoryRepository.save(category);
            entityManager.flush();
        });
    }

    @Test
    void testSaveCategoryWithBlankName_ThrowsException() {
        Category category = new Category();
        category.setName("   ");

        assertThrows(ConstraintViolationException.class, () -> {
            categoryRepository.save(category);
            entityManager.flush();
        });
    }

    @Test
    void testUniqueConstraintOnName() {
        Category category1 = new Category();
        category1.setName("Unique Category");
        categoryRepository.save(category1);
        entityManager.flush();

        Category category2 = new Category();
        category2.setName("Unique Category");

        assertThrows(Exception.class, () -> {
            categoryRepository.save(category2);
            entityManager.flush();
        });
    }

    @Test
    void testSaveParentChildRelationship() {
        Category parent = new Category();
        parent.setName("Fiction");
        parent = categoryRepository.save(parent);

        Category child = new Category();
        child.setName("Science Fiction");
        child.setParent(parent);
        child = categoryRepository.save(child);

        entityManager.flush();
        entityManager.clear();

        Optional<Category> foundChild = categoryRepository.findById(child.getId());
        assertTrue(foundChild.isPresent());
        assertEquals("Science Fiction", foundChild.get().getName());
        assertNotNull(foundChild.get().getParent());
        assertEquals(parent.getId(), foundChild.get().getParent().getId());
        assertEquals("Fiction", foundChild.get().getParent().getName());
    }

    @Test
    void testThreeLevelHierarchy() {
        Category grandParent = new Category();
        grandParent.setName("Books");
        grandParent = categoryRepository.save(grandParent);

        Category parent = new Category();
        parent.setName("Fiction");
        parent.setParent(grandParent);
        parent = categoryRepository.save(parent);

        Category child = new Category();
        child.setName("Mystery");
        child.setParent(parent);
        child = categoryRepository.save(child);

        entityManager.flush();
        entityManager.clear();

        Optional<Category> foundChild = categoryRepository.findById(child.getId());
        assertTrue(foundChild.isPresent());
        assertEquals("Mystery", foundChild.get().getName());
        assertEquals("Fiction", foundChild.get().getParent().getName());
        assertEquals("Books", foundChild.get().getParent().getParent().getName());
    }

    @Test
    void testCountByParentId() {
        Category parent = new Category();
        parent.setName("Parent Category");
        parent = categoryRepository.save(parent);

        Category child1 = new Category();
        child1.setName("Child 1");
        child1.setParent(parent);
        categoryRepository.save(child1);

        Category child2 = new Category();
        child2.setName("Child 2");
        child2.setParent(parent);
        categoryRepository.save(child2);

        entityManager.flush();

        long count = categoryRepository.countByParentId(parent.getId());
        assertEquals(2, count);
    }

    @Test
    void testCountByParentId_NoChildren() {
        Category parent = new Category();
        parent.setName("Childless Parent");
        parent = categoryRepository.save(parent);

        long count = categoryRepository.countByParentId(parent.getId());
        assertEquals(0, count);
    }

    @Test
    void testCountByIdIn() {
        Category category1 = new Category();
        category1.setName("Category 1");
        category1 = categoryRepository.save(category1);

        Category category2 = new Category();
        category2.setName("Category 2");
        category2 = categoryRepository.save(category2);

        long count = categoryRepository.countByIdIn(Arrays.asList(category1.getId(), category2.getId()));
        assertEquals(2, count);

        count = categoryRepository.countByIdIn(Arrays.asList(category1.getId()));
        assertEquals(1, count);

        count = categoryRepository.countByIdIn(Arrays.asList(999L, 1000L));
        assertEquals(0, count);
    }

    @Test
    void testGetIdByName() {
        Category category = new Category();
        category.setName("Test Category");
        category = categoryRepository.save(category);

        Long foundId = categoryRepository.getIdByName("Test Category");
        assertEquals(category.getId(), foundId);
    }

    @Test
    void testGetIdByName_NotFound() {
        Long foundId = categoryRepository.getIdByName("NonExistent Category");
        assertNull(foundId);
    }

    @Test
    void testUpdateCategory() {
        Category category = new Category();
        category.setName("Original Name");
        Category saved = categoryRepository.save(category);

        saved.setName("Updated Name");
        Category updated = categoryRepository.save(saved);

        assertEquals(saved.getId(), updated.getId());
        assertEquals("Updated Name", updated.getName());
    }

    @Test
    void testDeleteCategory() {
        Category category = new Category();
        category.setName("To Delete");
        Category saved = categoryRepository.save(category);
        Long id = saved.getId();

        categoryRepository.delete(saved);

        Optional<Category> found = categoryRepository.findById(id);
        assertFalse(found.isPresent());
    }

    @Test
    void testDeleteParentWithChildren() {
        Category parent = new Category();
        parent.setName("Parent to Delete");
        parent = categoryRepository.save(parent);

        Category child = new Category();
        child.setName("Orphan Child");
        child.setParent(parent);
        child = categoryRepository.save(child);

        entityManager.flush();
        entityManager.clear();

        Long parentId = parent.getId();
        Long childId = child.getId();

        // First, remove the parent reference from child
        Category loadedChild = categoryRepository.findById(childId).get();
        loadedChild.setParent(null);
        categoryRepository.save(loadedChild);
        entityManager.flush();

        // Now delete the parent
        Category loadedParent = categoryRepository.findById(parentId).get();
        categoryRepository.delete(loadedParent);
        entityManager.flush();

        Optional<Category> foundParent = categoryRepository.findById(parentId);
        assertFalse(foundParent.isPresent());

        Optional<Category> foundChild = categoryRepository.findById(childId);
        assertTrue(foundChild.isPresent());
        assertNull(foundChild.get().getParent());
    }

    @Test
    void testOptimisticLocking() {
        Category category = new Category();
        category.setName("Lock Test");
        Category saved = categoryRepository.save(category);
        entityManager.flush();

        assertNotNull(saved.getVersion());
        Long initialVersion = saved.getVersion();

        saved.setName("Updated Lock Test");
        Category updated = categoryRepository.save(saved);
        entityManager.flush();

        assertNotNull(updated.getVersion());
        assertTrue(updated.getVersion() > initialVersion);
    }

    @Test
    void testSequenceGenerator() {
        Category category1 = new Category();
        category1.setName("First Category");
        category1 = categoryRepository.save(category1);

        Category category2 = new Category();
        category2.setName("Second Category");
        category2 = categoryRepository.save(category2);

        assertNotNull(category1.getId());
        assertNotNull(category2.getId());
        assertTrue(category2.getId() > category1.getId());
    }

    @Test
    void testLazyLoadingOfParent() {
        Category parent = new Category();
        parent.setName("Lazy Parent");
        parent = categoryRepository.save(parent);

        Category child = new Category();
        child.setName("Lazy Child");
        child.setParent(parent);
        child = categoryRepository.save(child);

        entityManager.flush();
        entityManager.clear();

        // Load child without parent
        Optional<Category> foundChild = categoryRepository.findById(child.getId());
        assertTrue(foundChild.isPresent());

        // Parent should be lazily loaded
        assertNotNull(foundChild.get().getParent());
        assertEquals("Lazy Parent", foundChild.get().getParent().getName());
    }
}
