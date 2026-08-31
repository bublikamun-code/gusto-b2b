package by.gusto.admin.service;

import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.dto.CreateCompanyRequest;
import by.gusto.company.dto.UpdateCompanyRequest;
import by.gusto.company.entity.Company;
import by.gusto.company.mapper.CompanyMapper;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies() {
        return companyMapper.toResponseList(companyRepository.findAll());
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID id) {
        Company company = findCompany(id);
        return companyMapper.toResponse(company);
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        validateUnpUnique(request.getUnp(), null);
        validateManager(request.getManagerId());

        Company company = Company.builder()
                .name(request.getName())
                .shortName(request.getShortName())
                .unp(request.getUnp())
                .legalAddress(request.getLegalAddress())
                .actualAddress(request.getActualAddress())
                .bankAccount(request.getBankAccount())
                .bankName(request.getBankName())
                .bankBic(request.getBankBic())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .managerId(request.getManagerId())
                .status("ACTIVE")
                .build();

        return companyMapper.toResponse(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse updateCompany(UUID id, UpdateCompanyRequest request) {
        Company company = findCompany(id);

        if (request.getName() != null) {
            company.setName(request.getName());
        }
        if (request.getShortName() != null) {
            company.setShortName(request.getShortName());
        }
        if (request.getUnp() != null) {
            validateUnpUnique(request.getUnp(), id);
            company.setUnp(request.getUnp());
        }
        if (request.getLegalAddress() != null) {
            company.setLegalAddress(request.getLegalAddress());
        }
        if (request.getActualAddress() != null) {
            company.setActualAddress(request.getActualAddress());
        }
        if (request.getBankAccount() != null) {
            company.setBankAccount(request.getBankAccount());
        }
        if (request.getBankName() != null) {
            company.setBankName(request.getBankName());
        }
        if (request.getBankBic() != null) {
            company.setBankBic(request.getBankBic());
        }
        if (request.getContactPhone() != null) {
            company.setContactPhone(request.getContactPhone());
        }
        if (request.getContactEmail() != null) {
            company.setContactEmail(request.getContactEmail());
        }
        if (request.getManagerId() != null) {
            validateManager(request.getManagerId());
            company.setManagerId(request.getManagerId());
        }
        if (request.getStatus() != null) {
            company.setStatus(request.getStatus());
        }

        return companyMapper.toResponse(companyRepository.save(company));
    }

    @Transactional
    public void deactivateCompany(UUID id) {
        Company company = findCompany(id);
        company.setStatus("INACTIVE");
        companyRepository.save(company);
    }

    private Company findCompany(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Компания не найдена"));
    }

    private void validateUnpUnique(String unp, UUID excludeId) {
        if (unp == null || unp.isBlank()) {
            return;
        }
        companyRepository.findByUnp(unp)
                .filter(c -> !c.getId().equals(excludeId))
                .ifPresent(c -> {
                    throw new GustoException(ErrorCode.CONFLICT, "Компания с таким УНП уже существует");
                });
    }

    private void validateManager(UUID managerId) {
        if (managerId != null && !userRepository.existsById(managerId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Менеджер не найден");
        }
    }
}
