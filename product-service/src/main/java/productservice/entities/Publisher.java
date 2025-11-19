package productservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Publisher", indexes = {
        @Index(name = "idx_publisher_name", columnList = "name")
})
public class Publisher extends BaseEntity {
    @Id
    @Getter(onMethod_ = @Override)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "publisher_seq")
    @SequenceGenerator(name = "publisher_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotBlank(message = "Publisher name should be specified")
    @Column(name = "name", nullable = false, unique = true)
    private String name;

}