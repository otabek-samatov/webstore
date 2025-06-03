package product.productservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "Book", indexes = {
        @Index(name = "idx_book_title", columnList = "title")
})
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_seq")
    @SequenceGenerator(name = "book_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @NotBlank(message = "Title should be specified")
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle")
    private String subtitle;

    @NotNull(message = "Publisher should be specified")
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "publisherId", nullable = false)
    private Publisher publisher;

    @NotNull(message = "Publication date should be specified")
    @Column(name = "publicationDate", nullable = false)
    private LocalDate publicationDate;

    @NotBlank(message = "ISBN should be specified")
    @Column(name = "isbn", nullable = false, unique = true)
    private String isbn;

    @Column(name = "description", length = 2000)
    private String description;

    @PositiveOrZero(message = "Price should be non negative")
    @Column(name = "price", nullable = false, precision = 6, scale = 2)
    private BigDecimal price;

    @NotBlank(message = "Language should be specified")
    @Column(name = "language", nullable = false)
    private String language;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ElementCollection
    @Column(name = "imageUrl")
    @CollectionTable(name = "Book_Images", joinColumns = @JoinColumn(name = "bookid"))
    private Set<String> bookImages = new HashSet<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinTable(name = "Book_Author",
            joinColumns = @JoinColumn(name = "bookId"),
            inverseJoinColumns = @JoinColumn(name = "authorId"))
    private Set<Author> authors = new HashSet<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinTable(name = "Book_Category",
            joinColumns = @JoinColumn(name = "bookId"),
            inverseJoinColumns = @JoinColumn(name = "categoryId"))
    private Set<Category> categories = new HashSet<>();

    public void addBookImage(String imageUrl) {
        bookImages.add(imageUrl);
    }

    public void addBookImages(Collection<String> bookImages) {
        this.bookImages.addAll(bookImages);
    }

    public void addAuthor(Author author) {
        authors.add(author);
    }

    public void addAuthors(Collection<Author> authors) {
        this.authors.addAll(authors);
    }

    public void addCategory(Category category) {
        categories.add(category);
    }

    public void addCategories(Collection<Category> categories) {
        this.categories.addAll(categories);
    }

    public void removeAuthor(Author author) {
        authors.remove(author);
    }

    public void removeCategory(Category category) {
        categories.remove(category);
    }

    public void removeBookImage(String imageUrl) {
        bookImages.remove(imageUrl);
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

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Book book = (Book) o;
        return getId() != null && Objects.equals(getId(), book.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
