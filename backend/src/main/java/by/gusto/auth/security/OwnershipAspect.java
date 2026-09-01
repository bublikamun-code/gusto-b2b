package by.gusto.auth.security;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final AuthContext authContext;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Around("@annotation(requireOwnership)")
    public Object checkOwnership(ProceedingJoinPoint joinPoint, RequireOwnership requireOwnership) throws Throwable {
        User current = authContext.getCurrentUser();
        UUID resourceId = extractId(joinPoint, requireOwnership.idParam());

        boolean allowed = switch (requireOwnership.resource()) {
            case "company" -> canAccessCompany(current, resourceId);
            case "user" -> canAccessUser(current, resourceId);
            default -> throw new GustoException(ErrorCode.INTERNAL, "Unknown ownership resource: " + requireOwnership.resource());
        };

        if (!allowed) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
        return joinPoint.proceed();
    }

    private boolean canAccessCompany(User user, UUID companyId) {
        if (companyId == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.ACCOUNTANT) {
            return true;
        }
        if (user.getRole() == Role.MANAGER) {
            return companyRepository.findByIdAndManagerId(companyId, user.getId()).isPresent();
        }
        return companyId.equals(user.getCompanyId());
    }

    private boolean canAccessUser(User current, UUID userId) {
        if (userId == null) {
            return false;
        }
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

    private UUID extractId(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName)) {
                Object value = args[i];
                if (value instanceof UUID uuid) {
                    return uuid;
                }
                if (value instanceof String s) {
                    return UUID.fromString(s);
                }
                throw new GustoException(ErrorCode.INTERNAL, "Ownership parameter '" + paramName + "' must be UUID or String");
            }
        }
        throw new GustoException(ErrorCode.INTERNAL, "Ownership parameter '" + paramName + "' not found in method " + method.getName());
    }
}
