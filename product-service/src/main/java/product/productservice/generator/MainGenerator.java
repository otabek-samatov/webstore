package product.productservice.generator;


import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import net.datafaker.providers.base.Name;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import product.productservice.dto.AuthorDto;
import product.productservice.dto.PublisherDto;
import product.productservice.repositories.AuthorRepository;
import product.productservice.repositories.CategoryRepository;
import product.productservice.repositories.PublisherRepository;
import product.productservice.services.AuthorManager;
import product.productservice.services.CategoryManager;
import product.productservice.services.PublisherManager;

@RequiredArgsConstructor
@Component
public class MainGenerator implements CommandLineRunner {

    private final PublisherManager publisherManager;
    private final PublisherRepository publisherRepository;

    private final AuthorManager authorManager;
    private final AuthorRepository authorRepository;

    private final CategoryManager categoryManager;
    private final CategoryRepository productCategoryRepository;

    @Override
    public void run(String... args) throws Exception {

        generatePublisherCompanyObjects();

        generateAuthorObjects();

        generateProductCategoryObjects();

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
}
