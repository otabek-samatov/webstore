package userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import userservice.entities.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsUserByUserName(String userName);

    @Query("select u.id from User u where u.userName = :userName")
    Long getIdByUserName(String userName);


}