package by.gusto.admin.service;

import by.gusto.auth.dto.CreateUserRequest;
import by.gusto.auth.dto.TemporaryPasswordResponse;
import by.gusto.auth.dto.UpdateUserRequest;
import by.gusto.auth.dto.UserResponse;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.mapper.UserMapper;
import by.gusto.auth.repository.UserRepository;
import by.gusto.auth.service.RefreshTokenService;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int TEMPORARY_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userMapper.toResponseList(userRepository.findAllByDeletedAtIsNull());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User user = findActiveUser(id);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new GustoException(ErrorCode.CONFLICT, "Пользователь с таким email уже существует");
        }
        validateCompanyId(request.getCompanyId());
        validateRole(request.getRole());

        String temporaryPassword = RandomStringUtils.secure().nextAlphanumeric(TEMPORARY_PASSWORD_LENGTH);

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(request.getRole())
                .companyId(request.getCompanyId())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findActiveUser(id);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getRole() != null) {
            validateRole(request.getRole());
            user.setRole(request.getRole());
        }
        if (request.getCompanyId() != null) {
            validateCompanyId(request.getCompanyId());
            user.setCompanyId(request.getCompanyId());
        } else if (request.getCompanyId() == null && request.getRole() != null
                && request.getRole() != Role.CUSTOMER_LEGAL) {
            user.setCompanyId(null);
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findActiveUser(id);
        user.setDeletedAt(Instant.now());
        user.setActive(false);
        refreshTokenService.revokeAllUserTokens(user);
        userRepository.save(user);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID id) {
        User user = findActiveUser(id);
        String temporaryPassword = RandomStringUtils.secure().nextAlphanumeric(TEMPORARY_PASSWORD_LENGTH);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        refreshTokenService.revokeAllUserTokens(user);
        userRepository.save(user);
        return TemporaryPasswordResponse.builder()
                .userId(user.getId())
                .temporaryPassword(temporaryPassword)
                .build();
    }

    private User findActiveUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Пользователь не найден"));
    }

    private void validateCompanyId(UUID companyId) {
        if (companyId != null && !companyRepository.existsById(companyId)) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Компания не найдена");
        }
    }

    private void validateRole(Role role) {
        if (role == Role.CUSTOMER_INDIVIDUAL) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Роль CUSTOMER_INDIVIDUAL недоступна для создания администратором");
        }
    }
}
