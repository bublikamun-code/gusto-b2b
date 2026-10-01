package by.gusto.admin.service;

import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.dto.CreateCompanyRequest;
import by.gusto.company.dto.UpdateCompanyRequest;
import by.gusto.company.entity.Company;
import by.gusto.company.mapper.CompanyMapper;
import by.gusto.company.repository.CompanyRepository;
import by.gusto.common.api.Pages;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.audit.AuditService;
import by.gusto.auth.repository.UserRepository;
import by.gusto.auth.service.AuthContext;
import by.gusto.auth.service.RefreshTokenService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AdminCompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuthContext authContext;
    private final RefreshTokenService refreshTokenService;

    /** Серверный поиск и постраничность — см. замечание к AdminUserService.listUsers. */
    @Transactional(readOnly = true)
    public Page<CompanyResponse> listCompanies(String search, int page, int size) {
        Specification<Company> spec = (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                parts.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("unp")), like)));
            }
            return cb.and(parts.toArray(new Predicate[0]));
        };
        PageRequest pageable = Pages.of(page, size, Sort.by("name").ascending());
        return companyRepository.findAll(spec, pageable).map(companyMapper::toResponse);
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

        Company saved = companyRepository.save(company);
        auditService.append(actorId(), "company.create", "COMPANY", saved.getId(), null, snapshot(saved));
        return companyMapper.toResponse(saved);
    }

    @Transactional
    public CompanyResponse updateCompany(UUID id, UpdateCompanyRequest request) {
        Company company = findCompany(id);
        Map<String, Object> before = snapshot(company);

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

        Company saved = companyRepository.save(company);
        auditService.append(actorId(), "company.update", "COMPANY", saved.getId(),
                before, snapshot(saved));
        return companyMapper.toResponse(saved);
    }

    @Transactional
    public void deactivateCompany(UUID id) {
        Company company = findCompany(id);
        Map<String, Object> before = snapshot(company);
        company.setStatus("INACTIVE");
        companyRepository.save(company);
        // Статус компании сам по себе не закрывает доступ: сотрудники клиента продолжали бы
        // работать с живыми refresh-куками ещё до семи дней. Рвём их явно
        // (аудит 2026-09-30, группа «Админка»).
        int revoked = 0;
        for (by.gusto.auth.entity.User member : userRepository.findAllByCompanyIdAndDeletedAtIsNull(id)) {
            revoked += refreshTokenService.revokeAllUserTokens(member);
        }
        auditService.append(actorId(), "company.deactivate", "COMPANY", company.getId(),
                before, snapshot(company));
        log.info("COMPANY {} деактивирована, отозвано refresh-токенов: {}", id, revoked);
    }

    private UUID actorId() {
        return authContext.getCurrentUser().getId();
    }

    /**
     * Снапшот значимых полей. Банковские реквизиты — в журнал попадают: это персональные
     * и финансовые данные, и их правка обязана быть видна при разбирательстве
     * (аудит 2026-09-30: изменения компаний и учёток не писались в audit_log вовсе).
     */
    private Map<String, Object> snapshot(Company c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", c.getName());
        map.put("unp", c.getUnp());
        map.put("bankAccount", c.getBankAccount());
        map.put("bankName", c.getBankName());
        map.put("bankBic", c.getBankBic());
        map.put("managerId", c.getManagerId());
        map.put("status", c.getStatus());
        return map;
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
