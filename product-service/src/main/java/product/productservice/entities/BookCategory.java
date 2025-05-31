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

    @ManyToOne
    @JoinColumn(name = "bookId")
    private Book book;

    @ManyToOne
    @JoinColumn(name = "categoryId")
    private Category category;

}