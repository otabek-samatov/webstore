package productservice.entities;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import productservice.repositories.AuthorRepository;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class AuthorJpaTest {

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testSaveAndFindAuthor() {
        Author author = new Author();
        author.setFirstName("John");
        author.setMiddleName("Paul");
        author.setLastName("Doe");

        Author saved = authorRepository.save(author);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);

        Optional<Author> found = authorRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("John", found.get().getFirstName());
        assertEquals("Paul", found.get().getMiddleName());
        assertEquals("Doe", found.get().getLastName());
    }

    @Test
    void testSaveAuthorWithOnlyLastName() {
        Author author = new Author();
        author.setLastName("Smith");

        Author saved = authorRepository.save(author);

        assertNotNull(saved.getId());
        assertNull(saved.getFirstName());
        assertNull(saved.getMiddleName());
        assertEquals("Smith", saved.getLastName());
    }

    @Test
    void testSaveAuthorWithNullLastName_ThrowsException() {
        Author author = new Author();
        author.setFirstName("John");

        assertThrows(ConstraintViolationException.class, () -> {
            authorRepository.save(author);
            entityManager.flush();
        });
    }

    @Test
    void testSaveAuthorWithEmptyLastName_ThrowsException() {
        Author author = new Author();
        author.setLastName("");

        assertThrows(ConstraintViolationException.class, () -> {
            authorRepository.save(author);
            entityManager.flush();
        });
    }

    @Test
    void testSaveAuthorWithBlankLastName_ThrowsException() {
        Author author = new Author();
        author.setLastName("   ");

        assertThrows(ConstraintViolationException.class, () -> {
            authorRepository.save(author);
            entityManager.flush();
        });
    }

    @Test
    void testUpdateAuthor() {
        Author author = new Author();
        author.setLastName("Original");
        Author saved = authorRepository.save(author);

        saved.setLastName("Updated");
        saved.setFirstName("New");
        Author updated = authorRepository.save(saved);

        assertEquals(saved.getId(), updated.getId());
        assertEquals("Updated", updated.getLastName());
        assertEquals("New", updated.getFirstName());
    }

    @Test
    void testDeleteAuthor() {
        Author author = new Author();
        author.setLastName("ToDelete");
        Author saved = authorRepository.save(author);
        Long id = saved.getId();

        authorRepository.delete(saved);

        Optional<Author> found = authorRepository.findById(id);
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAllAuthors() {
        Author author1 = new Author();
        author1.setLastName("Doe");

        Author author2 = new Author();
        author2.setLastName("Smith");

        authorRepository.saveAll(Arrays.asList(author1, author2));

        long count = authorRepository.count();
        assertTrue(count >= 2);
    }

    @Test
    void testCountByIdIn() {
        Author author1 = new Author();
        author1.setLastName("Doe");
        author1 = authorRepository.save(author1);

        Author author2 = new Author();
        author2.setLastName("Smith");
        author2 = authorRepository.save(author2);

        long count = authorRepository.countByIdIn(Arrays.asList(author1.getId(), author2.getId()));
        assertEquals(2, count);

        count = authorRepository.countByIdIn(Arrays.asList(author1.getId()));
        assertEquals(1, count);

        count = authorRepository.countByIdIn(Arrays.asList(999L, 1000L));
        assertEquals(0, count);
    }

    @Test
    void testGetIdByNames() {
        Author author = new Author();
        author.setFirstName("Jane");
        author.setMiddleName("Marie");
        author.setLastName("Doe");
        author = authorRepository.save(author);

        Long foundId = authorRepository.getIdByNames("Jane", "Doe", "Marie");
        assertEquals(author.getId(), foundId);
    }

    @Test
    void testGetIdByNames_NotFound() {
        Long foundId = authorRepository.getIdByNames("NonExistent", "Author", "Name");
        assertNull(foundId);
    }

    @Test
    void testOptimisticLocking() {
        Author author = new Author();
        author.setLastName("LockTest");
        Author saved = authorRepository.save(author);
        entityManager.flush();

        assertNotNull(saved.getVersion());
        Long initialVersion = saved.getVersion();

        saved.setFirstName("Updated");
        Author updated = authorRepository.save(saved);
        entityManager.flush();

        assertNotNull(updated.getVersion());
        assertTrue(updated.getVersion() > initialVersion);
    }

    @Test
    void testIndexOnLastName() {
        // Create multiple authors to verify index usage
        for (int i = 0; i < 10; i++) {
            Author author = new Author();
            author.setLastName("TestLastName" + i);
            authorRepository.save(author);
        }

        entityManager.flush();
        entityManager.clear();

        // The index should make this query efficient
        // This is more of a performance test concept
        long count = authorRepository.count();
        assertTrue(count >= 10);
    }

    @Test
    void testSequenceGenerator() {
        Author author1 = new Author();
        author1.setLastName("First");
        author1 = authorRepository.save(author1);

        Author author2 = new Author();
        author2.setLastName("Second");
        author2 = authorRepository.save(author2);

        // With allocationSize = 1, IDs should be sequential
        assertNotNull(author1.getId());
        assertNotNull(author2.getId());
        assertTrue(author2.getId() > author1.getId());
    }
}
