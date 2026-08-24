package authservice.repositories;

import authservice.entities.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUserNameIgnoreCase(String userName);

    boolean existsAppUserByUserNameIgnoreCase(String userName);
}
