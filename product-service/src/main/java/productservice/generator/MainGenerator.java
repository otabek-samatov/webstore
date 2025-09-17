package productservice.generator;


import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import net.datafaker.providers.base.Book;
import net.datafaker.providers.base.Name;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import productservice.dto.AuthorDto;
import productservice.dto.BookDto;
import productservice.dto.PublisherDto;
import productservice.repositories.AuthorRepository;
import productservice.repositories.BookRepository;
import productservice.repositories.CategoryRepository;
import productservice.repositories.PublisherRepository;
import productservice.managers.AuthorManager;
import productservice.managers.BookManager;
import productservice.managers.CategoryManager;
import productservice.managers.PublisherManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class MainGenerator implements CommandLineRunner {

    private final PublisherManager publisherManager;
    private final PublisherRepository publisherRepository;

    private final AuthorManager authorManager;
    private final AuthorRepository authorRepository;

    private final CategoryManager categoryManager;
    private final CategoryRepository productCategoryRepository;

    private final BookManager bookManager;
    private final BookRepository bookRepository;

    private final JdbcClient jdbcClient;

    @Override
    public void run(String... args) throws Exception {

        generatePublisherCompanyObjects();

        generateAuthorObjects();

        generateProductCategoryObjects();

        generateBookObjects();

    }

    private void generatePublisherCompanyObjects() {

        int x = 0;

        Faker faker = new Faker();
        final int LIMIT = 100;

        long i = publisherRepository.count();

        while (i < LIMIT) {
            String name = faker.book().publisher();
            Long id = publisherRepository.getIdByName(name);
            if (id == null) {

                PublisherDto dto = PublisherDto.builder()
                        .name(name)
                        .build();

                publisherManager.create(dto);
                i++;
                x++;

            }

        }

        System.out.println("Created PublisherCompany count = " + x);
    }

    private void generateAuthorObjects() {
        final int LIMIT = 1000;

        int x = 0;

        Faker faker = new Faker();

        long i = authorRepository.count();

        while (i < LIMIT) {
            Name n = faker.name();
            String firstName = n.firstName();
            String lastName = n.lastName();
            String middleName = "";

            Long id = authorRepository.getIdByNames(firstName, lastName, middleName);
            if (id == null) {

                AuthorDto dto = AuthorDto.builder()
                        .firstName(firstName)
                        .lastName(lastName)
                        .middleName(middleName)
                        .build();

                authorManager.create(dto);
                i++;
                x++;

            }

        }

        System.out.println("Created Author count = " + x);

    }


    private void generateProductCategoryObjects() {

        long c = productCategoryRepository.count();
        if (c > 0) {
            System.out.println("Created Categories count = 0");
            return;
        }

        categoryManager.createByFields("Fiction", null);
        categoryManager.createByFields("Non-Fiction", null);

// Level 2 under Fiction
        categoryManager.createByFields("Fantasy", "Fiction");
        categoryManager.createByFields("Science Fiction", "Fiction");
        categoryManager.createByFields("Mystery", "Fiction");
        categoryManager.createByFields("Romance", "Fiction");
        categoryManager.createByFields("Thriller", "Fiction");
        categoryManager.createByFields("Horror", "Fiction");
        categoryManager.createByFields("Classic", "Fiction");
        categoryManager.createByFields("Satire", "Fiction");
        categoryManager.createByFields("Poetry", "Fiction");
        categoryManager.createByFields("Adventure", "Fiction");
        categoryManager.createByFields("Crime", "Fiction");
        categoryManager.createByFields("Drama", "Fiction");
        categoryManager.createByFields("Comics", "Fiction");

// Level 3 under Fantasy
        categoryManager.createByFields("High Fantasy", "Fantasy");
        categoryManager.createByFields("Urban Fantasy", "Fantasy");
        categoryManager.createByFields("Dark Fantasy", "Fantasy");

// Level 3 under Science Fiction
        categoryManager.createByFields("Cyberpunk", "Science Fiction");
        categoryManager.createByFields("Space Opera", "Science Fiction");

// Level 2 under Non-Fiction
        categoryManager.createByFields("Biography", "Non-Fiction");
        categoryManager.createByFields("History", "Non-Fiction");
        categoryManager.createByFields("Self-Help", "Non-Fiction");
        categoryManager.createByFields("Health", "Non-Fiction");
        categoryManager.createByFields("Travel", "Non-Fiction");
        categoryManager.createByFields("Religion", "Non-Fiction");
        categoryManager.createByFields("Science", "Non-Fiction");
        categoryManager.createByFields("Art", "Non-Fiction");
        categoryManager.createByFields("Business", "Non-Fiction");
        categoryManager.createByFields("Education", "Non-Fiction");
        categoryManager.createByFields("Psychology", "Non-Fiction");
        categoryManager.createByFields("Cooking", "Non-Fiction");
        categoryManager.createByFields("Music", "Non-Fiction");

// Level 3 under History
        categoryManager.createByFields("Ancient History", "History");
        categoryManager.createByFields("Modern History", "History");

// Level 3 under Science
        categoryManager.createByFields("Physics", "Science");
        categoryManager.createByFields("Biology", "Science");
        categoryManager.createByFields("Mathematics", "Science");
        categoryManager.createByFields("Chemistry", "Science");
        categoryManager.createByFields("Informatics", "Science");

        System.out.println(" Categories have been created ");

    }


    private void generateBookObjects() {
        final int LIMIT = 10000;

        int x = 0;

        Faker faker = new Faker();

        long i = bookRepository.count();

        LocalDate from = LocalDate.of(2000, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);

        while (i < LIMIT) {
            Book b = faker.book();
            double price = 10.0 + (1000.0 - 10.0) * faker.random().nextDouble();

            // Optional: round to 2 decimal places
            BigDecimal roundedPrice = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);

            BookDto dto = BookDto.builder()
                    .title(b.title())
                    .publicationDate(faker.date().birthdayLocalDate(1, 30))
                    .isbn(faker.code().isbn13())
                    .price(roundedPrice)
                    .language("English")
                    .publisherId(getRandomPublisherId())
                    .categoryIds(getRandomCategories(faker.random().nextInt(1, 3)))
                    .authorIds(getRandomAuthors(faker.random().nextInt(1, 3)))
                    .bookImages(getRandomImageUrls(faker, faker.random().nextInt(1, 3)))
                    .build();

            Long id = bookRepository.getIdByISBN(dto.getIsbn());
            if (id == null) {
                bookManager.create(dto);
                i++;
                x++;
            }
        }

        System.out.println("Created Book count = " + x);

    }

    private Long getRandomPublisherId() {
        return jdbcClient.sql("select p.id from publisher p order by RANDOM() limit 1").query(Long.class).single();
    }

    private Set<Long> getRandomCategories(int count) {
        return jdbcClient.sql("select p.id from Category p order by RANDOM() limit :c")
                .param("c", count)
                .query(Long.class)
                .set();
    }

    private Set<Long> getRandomAuthors(int count) {
        return jdbcClient.sql("select a.id from author a order by RANDOM() limit :c")
                .param("c", count)
                .query(Long.class)
                .set();
    }

    private Set<String> getRandomImageUrls(Faker f, int count) {
        Set<String> imgs = new HashSet<>();

        for (int i = 0; i < count; i++) {
            imgs.add(f.internet().image());
        }

        return imgs;
    }
}
