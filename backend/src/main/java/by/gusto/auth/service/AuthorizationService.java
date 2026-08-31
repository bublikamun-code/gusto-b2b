package by.gusto.auth.service;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("authz")
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthContext authContext;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public boolean isAccountant() {
        return hasRole(Role.ACCOUNTANT);
    }

    public boolean isManager() {
        return hasRole(Role.MANAGER);
    }

    public boolean isCustomer() {
        User user = authContext.getCurrentUser();
        return user.getRole() == Role.CUSTOMER_LEGAL || user.getRole() == Role.CUSTOMER_INDIVIDUAL;
    }

    public boolean canAccessCompany(UUID companyId) {
        if (companyId == null) {
            return false;
        }
        User user = authContext.getCurrentUser();
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.ACCOUNTANT) {
            return true;
        }
        if (user.getRole() == Role.MANAGER) {
            return companyRepository.findByIdAndManagerId(companyId, user.getId()).isPresent();
        }
        return companyId.equals(user.getCompanyId());
    }

    public boolean canAccessUser(UUID userId) {
        if (userId == null) {
            return false;
        }
        User current = authContext.getCurrentUser();
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.ACCOUNTANT) {
            return true;
        }
        if (current.getId().equals(userId)) {
            return true;
        }
        if (current.getRole() == Role.MANAGER) {
            return userRepository.findById(userId)
                    .map(target -> target.getCompanyId() != null
                            && companyRepository.findByIdAndManagerId(target.getCompanyId(), current.getId()).isPresent())
                    .orElse(false);
        }
        return false;
    }

    private boolean hasRole(Role role) {
        return authContext.getCurrentUser().getRole() == role;
    }
}
