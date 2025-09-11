package userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import userservice.entities.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsUserByUserName(String userName);

    User findByUserName(String userName);
}