package product.productservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "BookAuthor", uniqueConstraints = {
        @UniqueConstraint(name = "uc_bookauthor_bookid_authorid", columnNames = {"bookId", "authorId"})
})
public class BookAuthor {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_author_seq")
    @SequenceGenerator(name = "book_author_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bookId", nullable = false)
    private Book book;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "authorId", nullable = false)
    private Author author;

}