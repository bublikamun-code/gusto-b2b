package by.gusto.auth.service;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
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
        return canAccessCompany(companyId, authContext.getCurrentUser());
    }

    /**
     * Матрица 2.1 «менеджер по своим клиентам» в виде единственной точки проверки.
     * Списки фильтруются через findAllVisibleTo, а detail-пути и мутации обязаны
     * звать этот же метод — иначе изоляция теряется по id (S44).
     */
    public boolean canAccessCompany(UUID companyId, User actor) {
        if (companyId == null || actor == null) {
            return false;
        }
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.ACCOUNTANT) {
            return true;
        }
        if (actor.getRole() == Role.MANAGER) {
            return companyRepository.findByIdAndManagerId(companyId, actor.getId()).isPresent();
        }
        return companyId.equals(actor.getCompanyId());
    }

    /** Вызывать перед любой мутацией документа/заказа от имени компании. */
    public void requireCompanyAccess(UUID companyId, User actor) {
        if (!canAccessCompany(companyId, actor)) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * Заказ виден, если видна его компания. Розничный заказ (companyId = null)
     * доступен только ADMIN/ACCOUNTANT и самому покупателю.
     */
    public boolean canAccessOrder(UUID orderCompanyId, UUID orderCustomerUserId, UUID orderManagerId, User actor) {
        if (actor == null) {
            return false;
        }
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.ACCOUNTANT) {
            return true;
        }
        if (orderCompanyId != null) {
            return canAccessCompany(orderCompanyId, actor);
        }
        if (actor.getRole() == Role.MANAGER) {
            return actor.getId().equals(orderManagerId);
        }
        return orderCustomerUserId != null && orderCustomerUserId.equals(actor.getId());
    }

    public void requireOrderAccess(UUID orderCompanyId, UUID orderCustomerUserId, UUID orderManagerId, User actor) {
        if (!canAccessOrder(orderCompanyId, orderCustomerUserId, orderManagerId, actor)) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
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
