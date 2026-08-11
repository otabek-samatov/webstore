package authservice.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    @Getter(AccessLevel.NONE)
    @ElementCollection
    @Column(name = "authority")
    @CollectionTable(name = "users_authorities", joinColumns = @JoinColumn(name = "owner_id"))
    private Set<String> authorities = new LinkedHashSet<>();

    public void setUserName(String userName) {
        if (this.userName != null) {
            throw new IllegalStateException("Username cannot be changed once set");
        }
        this.userName = userName;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = new LinkedHashSet<>(authorities);
    }

    public void addAuthority(String authority) {
        authorities.add(authority);
    }

    public void removeAuthority(String authority) {
        authorities.remove(authority);
    }

    public Set<String> getAuthorities() {
        return Set.copyOf(authorities);
    }

}