package userservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "security_role")
@SequenceGenerator(name = "entity_seq", sequenceName = "security_role_seq", allocationSize = 50, initialValue = 1)
public class SecurityRole extends CoreEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, unique = true)
    private RoleType roleType;

}