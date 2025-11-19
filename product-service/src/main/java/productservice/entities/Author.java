package productservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Author", indexes = {
        @Index(name = "idx_lastName", columnList = "last_name")
})
public class Author extends BaseEntity {
    @Id
    @Getter(onMethod_ = @Override)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "author_seq")
    @SequenceGenerator(name = "author_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @NotBlank(message = "Last Name should be specified")
    @Column(name = "last_name", nullable = false)
    private String lastName;

}