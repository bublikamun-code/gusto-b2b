package by.gusto.auth.service;

import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthContext {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new GustoException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new GustoException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new GustoException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return authentication.getName();
    }
}
