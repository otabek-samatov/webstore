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
@SequenceGenerator(name = "entity_seq", sequenceName = "publisher_seq", allocationSize = 50, initialValue = 1)
public class Publisher extends CoreEntity {

    @NotBlank(message = "Publisher name should be specified")
    @Column(name = "name", nullable = false, unique = true)
    private String name;

}