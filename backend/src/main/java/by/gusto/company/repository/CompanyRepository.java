package by.gusto.company.repository;

import by.gusto.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findAllByManagerId(UUID managerId);

    Optional<Company> findByIdAndManagerId(UUID id, UUID managerId);

    Optional<Company> findByUnp(String unp);
}
