package product.productservice.generator;


import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import net.datafaker.providers.base.Name;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import product.productservice.dto.BookAuthorDto;
import product.productservice.dto.PublisherCompanyDto;
import product.productservice.repositories.BookAuthorRepository;
import product.productservice.repositories.ProductCategoryRepository;
import product.productservice.repositories.PublisherCompanyRepository;
import product.productservice.services.BookAuthorManager;
import product.productservice.services.ProductCategoryManager;
import product.productservice.services.PublisherCompanyManager;

@RequiredArgsConstructor
@Component
public class MainGenerator implements CommandLineRunner {

    private final PublisherCompanyManager publisherCompanyManager;
    private final PublisherCompanyRepository publisherCompanyRepository;

    private final BookAuthorManager bookAuthorManager;
    private final BookAuthorRepository bookAuthorRepository;

    private final ProductCategoryManager productCategoryManager;
    private final ProductCategoryRepository productCategoryRepository;

    @Override
    public void run(String... args) throws Exception {

      //  generatePublisherCompanyObjects();

    //    generateAuthorObjects();

      //  generateProductCategoryObjects();

    }

    private void generatePublisherCompanyObjects() {

        int x = 0;

        Faker faker = new Faker();
        final int LIMIT = 100;

        long i = publisherCompanyRepository.count();

        while (i < LIMIT) {
            String name = faker.book().publisher();
            Long id = publisherCompanyRepository.getIdByName(name);
            if (id == null) {

                PublisherCompanyDto dto = PublisherCompanyDto.builder()
                        .name(name)
                        .build();

                publisherCompanyManager.create(dto);
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

        long i = bookAuthorRepository.count();

        while (i < LIMIT) {
            Name n = faker.name();
            String firstName = n.firstName();
            String lastName = n.lastName();
            String middleName = "";

            Long id = bookAuthorRepository.getIdByNames(firstName, lastName, middleName);
            if (id == null) {

                BookAuthorDto dto = BookAuthorDto.builder()
                        .firstName(firstName)
                        .lastName(lastName)
                        .middleName(middleName)
                        .build();

                bookAuthorManager.create(dto);
                i++;
                x++;

            }

        }

        System.out.println("Created Author count = " + x);

    }


    private void generateProductCategoryObjects() {

        long c = productCategoryRepository.count();
        if (c > 0) {
            return;
        }

        productCategoryManager.createByFields("Fiction", null);
        productCategoryManager.createByFields("Non-Fiction", null);

// Level 2 under Fiction
        productCategoryManager.createByFields("Fantasy", "Fiction");
        productCategoryManager.createByFields("Science Fiction", "Fiction");
        productCategoryManager.createByFields("Mystery", "Fiction");
        productCategoryManager.createByFields("Romance", "Fiction");
        productCategoryManager.createByFields("Thriller", "Fiction");
        productCategoryManager.createByFields("Horror", "Fiction");
        productCategoryManager.createByFields("Classic", "Fiction");
        productCategoryManager.createByFields("Satire", "Fiction");
        productCategoryManager.createByFields("Poetry", "Fiction");
        productCategoryManager.createByFields("Adventure", "Fiction");
        productCategoryManager.createByFields("Crime", "Fiction");
        productCategoryManager.createByFields("Drama", "Fiction");
        productCategoryManager.createByFields("Comics", "Fiction");

// Level 3 under Fantasy
        productCategoryManager.createByFields("High Fantasy", "Fantasy");
        productCategoryManager.createByFields("Urban Fantasy", "Fantasy");
        productCategoryManager.createByFields("Dark Fantasy", "Fantasy");

// Level 3 under Science Fiction
        productCategoryManager.createByFields("Cyberpunk", "Science Fiction");
        productCategoryManager.createByFields("Space Opera", "Science Fiction");

// Level 2 under Non-Fiction
        productCategoryManager.createByFields("Biography", "Non-Fiction");
        productCategoryManager.createByFields("History", "Non-Fiction");
        productCategoryManager.createByFields("Self-Help", "Non-Fiction");
        productCategoryManager.createByFields("Health", "Non-Fiction");
        productCategoryManager.createByFields("Travel", "Non-Fiction");
        productCategoryManager.createByFields("Religion", "Non-Fiction");
        productCategoryManager.createByFields("Science", "Non-Fiction");
        productCategoryManager.createByFields("Art", "Non-Fiction");
        productCategoryManager.createByFields("Business", "Non-Fiction");
        productCategoryManager.createByFields("Education", "Non-Fiction");
        productCategoryManager.createByFields("Psychology", "Non-Fiction");
        productCategoryManager.createByFields("Cooking", "Non-Fiction");
        productCategoryManager.createByFields("Music", "Non-Fiction");

// Level 3 under History
        productCategoryManager.createByFields("Ancient History", "History");
        productCategoryManager.createByFields("Modern History", "History");

// Level 3 under Science
        productCategoryManager.createByFields("Physics", "Science");
        productCategoryManager.createByFields("Biology", "Science");
        productCategoryManager.createByFields("Mathematics", "Science");
        productCategoryManager.createByFields("Chemistry", "Science");
        productCategoryManager.createByFields("Informatics", "Science");

        System.out.println(" Product Categories have been created ");

    }
}
