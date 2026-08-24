package authservice.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_user_name", columnList = "user_name")
})
@SequenceGenerator(name = "entity_seq", sequenceName = "user_seq", allocationSize = 50, initialValue = 1)
public class AppUser extends CoreEntity {

    @Setter(AccessLevel.NONE)
    @Column(name = "user_name", nullable = false, unique = true, updatable = false)
    private String userName;

    @Column(name = "password", nullable = false)
    private String password;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active")
    private Boolean isActive;

    /**
     * The user's role — the only authorization state an account carries, and exactly one of them.
     *
     * <p>A plain column rather than a collection or a FK to a role table: a user is never both
     * {@code ADMIN} and {@code CUSTOMER}, and a role has no attributes of its own worth joining for
     * on every login. Promote it to a {@code @ManyToOne} only if roles ever gain metadata.
     *
     * <p>Stored as {@code STRING}, not ordinal — the DB holds {@code ADMIN}, so reordering
     * {@link RoleType} cannot silently reassign anyone. Being a column on {@code users}, it is
     * always loaded with the row, so {@code SecurityUserDetails} stays usable in the filter chain
     * after {@code loadUserByUsername}'s transaction closes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private RoleType role;

    public void setUserName(String userName) {
        if (this.userName != null) {
            throw new IllegalStateException("Username cannot be changed once set");
        }
        this.userName = userName;
    }

}