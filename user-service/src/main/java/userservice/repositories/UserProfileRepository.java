package userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import userservice.entities.UserProfile;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Modifying
    @Query("DELETE FROM UserProfile up WHERE up.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);


}