package userservice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "user_profile")
@SequenceGenerator(name = "entity_seq", sequenceName = "user_profile_seq", allocationSize = 50, initialValue = 1)
public class UserProfile extends CoreEntity {

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false, orphanRemoval = true)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;


    @NotNull(message = "Address Should be specified")
    @Embedded
    private Address address;

}