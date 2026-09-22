package by.gusto.inventory.repository;

import by.gusto.inventory.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByUnpAndActiveTrue(String unp);

    @Query("select s from Supplier s where "
            + "(:search = '' or lower(s.name) like lower(concat('%', :search, '%')) "
            + "or lower(s.contactPerson) like lower(concat('%', :search, '%'))) "
            + "and (:active is null or s.active = :active) "
            + "order by s.name asc")
    Page<Supplier> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);
}
