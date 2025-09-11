package userservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import userservice.entities.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
}