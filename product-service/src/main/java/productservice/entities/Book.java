package productservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "Book", indexes = {
        @Index(name = "idx_book_title", columnList = "title")
})
public class Book extends BaseEntity {
    @Id
    @Getter(onMethod_ = @Override)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_seq")
    @SequenceGenerator(name = "book_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotBlank(message = "Title should be specified")
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle")
    private String subtitle;

    @NotNull(message = "Publisher should be specified")
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @NotNull(message = "Publication date should be specified")
    @Column(name = "publication_date", nullable = false)
    private LocalDate publicationDate;

    @NotBlank(message = "ISBN should be specified")
    @Column(name = "isbn", nullable = false, unique = true)
    private String isbn;

    @Column(name = "description", length = 2000)
    private String description;

    @NotNull
    @PositiveOrZero(message = "Price should be non negative")
    @Column(name = "price", nullable = false, precision = 6, scale = 2)
    private BigDecimal price;

    @NotBlank(message = "Language should be specified")
    @Column(name = "language", nullable = false)
    private String language;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ElementCollection
    @Column(name = "image_url")
    @CollectionTable(name = "book_images", joinColumns = @JoinColumn(name = "book_id"))
    private Set<String> bookImages = new HashSet<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinTable(name = "book_author",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    private Set<Author> authors = new HashSet<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinTable(name = "book_category",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    public void addBookImage(String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            bookImages.add(imageUrl);
        }
    }

    public void addBookImages(Collection<String> bookImages) {
        if (bookImages != null) {
            this.bookImages.addAll(bookImages);
        }
    }

    public void setBookImages(Collection<String> bookImages) {
        this.bookImages.clear();
        addBookImages(bookImages);
    }

    public void addAuthor(Author author) {
        if (author != null) {
            authors.add(author);
        }
    }

    public void addAuthors(Collection<Author> authors) {
        if (authors != null) {
            this.authors.addAll(authors);
        }
    }

    public void setAuthors(Collection<Author> authors) {
        this.authors.clear();
        addAuthors(authors);
    }

    public void addCategory(Category category) {
        if (category != null) {
            categories.add(category);
        }
    }

    public void addCategories(Collection<Category> categories) {
        if (categories != null) {
            this.categories.addAll(categories);
        }
    }

    public void setCategories(Collection<Category> categories) {
        this.categories.clear();
        addCategories(categories);
    }

    public void removeAuthor(Author author) {
        if (author != null) {
            authors.remove(author);
        }
    }

    public void removeCategory(Category category) {
        if (category != null) {
            categories.remove(category);
        }
    }

    public void removeBookImage(String imageUrl) {
        if (imageUrl != null) {
            bookImages.remove(imageUrl);
        }
    }

    public Set<Author> getAuthors() {
        return Set.copyOf(authors);
    }

    public Set<Category> getCategories() {
        return Set.copyOf(categories);
    }

    public Set<String> getBookImages() {
        return Set.copyOf(bookImages);
    }

}
