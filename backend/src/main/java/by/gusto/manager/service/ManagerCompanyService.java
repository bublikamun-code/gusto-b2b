package by.gusto.manager.service;

import by.gusto.auth.security.RequireOwnership;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.dto.CompanyResponse;
import by.gusto.company.dto.UpdateCompanyRequest;
import by.gusto.company.entity.Company;
import by.gusto.company.mapper.CompanyMapper;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagerCompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final AuthContext authContext;

    @Transactional
    @RequireOwnership(resource = "company", idParam = "id")
    public CompanyResponse updateCompany(UUID id, UpdateCompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Компания не найдена"));

        applyUpdate(company, request);
        return companyMapper.toResponse(companyRepository.save(company));
    }

    private void applyUpdate(Company company, UpdateCompanyRequest request) {
        if (request.getName() != null) {
            company.setName(request.getName());
        }
        if (request.getShortName() != null) {
            company.setShortName(request.getShortName());
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
    }
}
