package userservice.entities;

import jakarta.persistence.*;
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
public class User extends CoreEntity {

    @Column(name = "user_name", nullable = false, unique = true, updatable = false)
    private String userName;

    @Column(name = "password", nullable = false)
    private String password;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_role_id", nullable = false)
    private SecurityRole securityRole;

    public void setUserName(String userName) {
        if (this.userName != null) {
            throw new IllegalStateException("Username cannot be changed once set");
        }
        this.userName = userName;
    }

}