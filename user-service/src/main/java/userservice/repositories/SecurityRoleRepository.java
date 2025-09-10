package userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import userservice.entities.SecurityRole;

@Repository
public interface SecurityRoleRepository extends JpaRepository<SecurityRole, Long> {
}