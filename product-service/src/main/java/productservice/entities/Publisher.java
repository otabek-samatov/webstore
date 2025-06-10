package productservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "Publisher", indexes = {
        @Index(name = "idx_publisher_name", columnList = "name")
})
public class Publisher {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "publisher_seq")
    @SequenceGenerator(name = "publisher_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Integer version;

    @NotBlank(message = "Publisher name should be specified")
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Publisher that = (Publisher) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}