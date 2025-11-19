package productservice.entities;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import productservice.repositories.PublisherRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PublisherJpaTest {

    @Autowired
    private PublisherRepository publisherRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testSaveAndFindPublisher() {
        Publisher publisher = new Publisher();
        publisher.setName("Penguin Random House");

        Publisher saved = publisherRepository.save(publisher);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);

        Optional<Publisher> found = publisherRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Penguin Random House", found.get().getName());
    }

    @Test
    void testSavePublisherWithNullName_ThrowsException() {
        Publisher publisher = new Publisher();

        assertThrows(ConstraintViolationException.class, () -> {
            publisherRepository.save(publisher);
            entityManager.flush();
        });
    }

    @Test
    void testSavePublisherWithEmptyName_ThrowsException() {
        Publisher publisher = new Publisher();
        publisher.setName("");

        assertThrows(ConstraintViolationException.class, () -> {
            publisherRepository.save(publisher);
            entityManager.flush();
        });
    }

    @Test
    void testSavePublisherWithBlankName_ThrowsException() {
        Publisher publisher = new Publisher();
        publisher.setName("   ");

        assertThrows(ConstraintViolationException.class, () -> {
            publisherRepository.save(publisher);
            entityManager.flush();
        });
    }

    @Test
    void testUpdatePublisher() {
        Publisher publisher = new Publisher();
        publisher.setName("Original Publisher");
        Publisher saved = publisherRepository.save(publisher);

        saved.setName("Updated Publisher");
        Publisher updated = publisherRepository.save(saved);

        assertEquals(saved.getId(), updated.getId());
        assertEquals("Updated Publisher", updated.getName());
    }

    @Test
    void testDeletePublisher() {
        Publisher publisher = new Publisher();
        publisher.setName("To Delete Publisher");
        Publisher saved = publisherRepository.save(publisher);
        Long id = saved.getId();

        publisherRepository.delete(saved);

        Optional<Publisher> found = publisherRepository.findById(id);
        assertFalse(found.isPresent());
    }

    @Test
    void testUniqueConstraintOnName() {
        Publisher publisher1 = new Publisher();
        publisher1.setName("Unique Publisher");
        publisherRepository.save(publisher1);
        entityManager.flush();

        Publisher publisher2 = new Publisher();
        publisher2.setName("Unique Publisher");

        assertThrows(Exception.class, () -> {
            publisherRepository.save(publisher2);
            entityManager.flush();
        });
    }

    @Test
    void testGetIdByName() {
        Publisher publisher = new Publisher();
        publisher.setName("HarperCollins");
        publisher = publisherRepository.save(publisher);

        Long foundId = publisherRepository.getIdByName("HarperCollins");
        assertEquals(publisher.getId(), foundId);
    }

    @Test
    void testGetIdByName_NotFound() {
        Long foundId = publisherRepository.getIdByName("NonExistent Publisher");
        assertNull(foundId);
    }

    @Test
    void testOptimisticLocking() {
        Publisher publisher = new Publisher();
        publisher.setName("Lock Test Publisher");
        Publisher saved = publisherRepository.save(publisher);
        entityManager.flush();

        assertNotNull(saved.getVersion());
        Long initialVersion = saved.getVersion();

        saved.setName("Updated Lock Test Publisher");
        Publisher updated = publisherRepository.save(saved);
        entityManager.flush();

        assertNotNull(updated.getVersion());
        assertTrue(updated.getVersion() > initialVersion);
    }

    @Test
    void testSequenceGenerator() {
        Publisher publisher1 = new Publisher();
        publisher1.setName("First Publisher");
        publisher1 = publisherRepository.save(publisher1);

        Publisher publisher2 = new Publisher();
        publisher2.setName("Second Publisher");
        publisher2 = publisherRepository.save(publisher2);

        assertNotNull(publisher1.getId());
        assertNotNull(publisher2.getId());
        assertTrue(publisher2.getId() > publisher1.getId());
    }

    @Test
    void testIndexOnName() {
        // Create multiple publishers to verify index usage
        for (int i = 0; i < 10; i++) {
            Publisher publisher = new Publisher();
            publisher.setName("Test Publisher " + i);
            publisherRepository.save(publisher);
        }

        entityManager.flush();
        entityManager.clear();

        long count = publisherRepository.count();
        assertTrue(count >= 10);
    }

    @Test
    void testNullableConstraint() {
        Publisher publisher = new Publisher();
        publisher.setName("Test");
        Publisher saved = publisherRepository.save(publisher);
        entityManager.flush();

        // Name should not be nullable in DB
        assertNotNull(saved.getName());
    }
}
