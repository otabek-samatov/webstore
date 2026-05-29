package productservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Category", indexes = {
        @Index(name = "idx_category_name", columnList = "name")
})
@SequenceGenerator(name = "entity_seq", sequenceName = "category_seq", allocationSize = 50, initialValue = 1)
public class Category extends CoreEntity {

    @NotBlank(message = "Category Name should be specified")
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

}