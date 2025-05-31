package product.productservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "BookCategory", uniqueConstraints = {
        @UniqueConstraint(name = "uc_bookcategory_bookid", columnNames = {"bookId", "categoryId"})
})
public class BookCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_category_seq")
    @SequenceGenerator(name = "book_category_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bookId", nullable = false)
    private Book book;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "categoryId", nullable = false)
    private Category category;

}