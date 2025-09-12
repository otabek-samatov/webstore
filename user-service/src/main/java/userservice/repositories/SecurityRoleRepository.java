package userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import userservice.entities.RoleType;
import userservice.entities.SecurityRole;

@Repository
public interface SecurityRoleRepository extends JpaRepository<SecurityRole, Long> {

    @Query("select r.id from SecurityRole r where r.roleType = :roleType")
    Long getIDByRoleType(@Param("roleType") RoleType roleType);
}