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
@SequenceGenerator(name = "entity_seq", sequenceName = "author_seq", allocationSize = 50, initialValue = 1)
public class Author extends CoreEntity {

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @NotBlank(message = "Last Name should be specified")
    @Column(name = "last_name", nullable = false)
    private String lastName;

}