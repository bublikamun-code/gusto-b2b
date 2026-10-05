package by.gusto.admin.service;

import by.gusto.auth.dto.CreateUserRequest;
import by.gusto.auth.dto.TemporaryPasswordResponse;
import by.gusto.auth.dto.UpdateUserRequest;
import by.gusto.auth.dto.UserResponse;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.mapper.UserMapper;
import by.gusto.auth.repository.UserRepository;
import by.gusto.audit.AuditService;
import by.gusto.auth.service.AuthContext;
import by.gusto.auth.service.RefreshTokenService;
import by.gusto.common.api.Pages;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.company.repository.CompanyRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AuditService auditService;
    private final AuthContext authContext;

    /**
     * Список пользователей с серверным поиском и постраничностью.
     * Раньше метод игнорировал всё, что приходило от UI: контроллер не принимал ни
     * фильтров, ни page/size, а форма «Поиск» и клики по страницам были декоративными —
     * таблица всегда приезжала целиком (аудит 2026-09-30, группа «Админка»).
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, Role role, Boolean active, int page, int size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            parts.add(cb.isNull(root.get("deletedAt")));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                parts.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), like)));
            }
            if (role != null) {
                parts.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                parts.add(cb.equal(root.get("active"), active));
            }
            return cb.and(parts.toArray(new Predicate[0]));
        };
        PageRequest pageable = Pages.of(page, size, Sort.by("fullName").ascending());
        return userRepository.findAll(spec, pageable).map(userMapper::toResponse);
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

        // Если админ задал пароль в форме — используем его. Иначе генерируем и ОТДАЁМ
        // его в ответе: раньше он уходил в хеш и терялся, из-за чего созданного
        // пользователя нельзя было ни разблокировать, ни узнать пароль (аудит 2026-09-30).
        String temporaryPassword = null;
        String rawPassword = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword()
                : RandomStringUtils.secure().nextAlphanumeric(TEMPORARY_PASSWORD_LENGTH);
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            temporaryPassword = rawPassword;
        }

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(request.getRole())
                .companyId(request.getCompanyId())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        auditService.append(actorId(), "user.create", "USER", saved.getId(), null, snapshot(saved));
        UserResponse response = userMapper.toResponse(saved);
        response.setTemporaryPassword(temporaryPassword);
        return response;
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findActiveUser(id);
        Map<String, Object> before = snapshot(user);
        boolean roleChanged = false;
        boolean accessRevoked = false;

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getRole() != null) {
            // Проверяем роль ТОЛЬКО при реальном её изменении. Раньше валидатор отвергал
            // CUSTOMER_INDIVIDUAL всегда, а форма отправляет роль при каждом сохранении —
            // саморегистрировавшееся физлицо было нередактируемым, любое сохранение давало 400
            // (аудит 2026-09-30, группа «Админка»).
            if (request.getRole() != user.getRole()) {
                validateRole(request.getRole());
            }
            if (user.getRole() == Role.ADMIN && request.getRole() != Role.ADMIN) {
                guardLastAdmin(user, "понизить до " + request.getRole());
            }
            roleChanged = request.getRole() != user.getRole();
            user.setRole(request.getRole());
        }
        if (request.getCompanyId() != null) {
            validateCompanyId(request.getCompanyId());
            user.setCompanyId(request.getCompanyId());
        } else if (request.getCompanyId() == null && request.getRole() != null
                && request.getRole() != Role.CUSTOMER_LEGAL
                && request.getRole() != user.getRole()) {
            // Компанию снимаем только при реальной смене роли на «не юрлицо»;
            // иначе сохранение физлица обнуляло бы его (и не сделало бы ничего полезного).
            user.setCompanyId(null);
        }
        if (request.getActive() != null) {
            if (!request.getActive() && user.isActive()) {
                if (user.getRole() == Role.ADMIN) {
                    guardLastAdmin(user, "деактивировать");
                }
                accessRevoked = true;
            }
            user.setActive(request.getActive());
        }

        User saved = userRepository.save(user);
        // Смена роли и снятие активности обрывают живую сессию: иначе refresh-кука
        // продлевала бы доступ ещё семь дней (S44).
        if (accessRevoked || roleChanged) {
            refreshTokenService.revokeAllUserTokens(saved);
        }
        auditService.append(actorId(), "user.update", "USER", saved.getId(), before, snapshot(saved));
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findActiveUser(id);
        if (user.getRole() == Role.ADMIN) {
            guardLastAdmin(user, "удалить");
        }
        Map<String, Object> before = snapshot(user);
        user.setDeletedAt(Instant.now());
        user.setActive(false);
        refreshTokenService.revokeAllUserTokens(user);
        userRepository.save(user);
        auditService.append(actorId(), "user.delete", "USER", user.getId(), before, null);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID id) {
        User user = findActiveUser(id);
        String temporaryPassword = RandomStringUtils.secure().nextAlphanumeric(TEMPORARY_PASSWORD_LENGTH);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        refreshTokenService.revokeAllUserTokens(user);
        userRepository.save(user);
        // В журнал НЕ пишем ни временный пароль, ни хеш: пароль утекает администратору
        // в ответе и показывается один раз, а журнал читают шире, чем ответ.
        auditService.append(actorId(), "user.reset_password", "USER", user.getId(),
                null, Map.of("email", String.valueOf(user.getEmail()), "emailSent", false));
        return TemporaryPasswordResponse.builder()
                .userId(user.getId())
                .temporaryPassword(temporaryPassword)
                .build();
    }

    /**
     * Нельзя снять активность, удалить или понизить последнего активного администратора.
     * В сиде админ ровно один, поэтому штатный сценарий — «заблокировать сам себя и
     * потерять доступ ко всей системе», а восстановление возможно только руками в базе
     * (аудит 2026-09-30, группа «Админка»).
     */
    private void guardLastAdmin(User user, String action) {
        if (userRepository.countByRoleAndActiveTrueAndDeletedAtIsNull(Role.ADMIN) <= 1) {
            throw new GustoException(ErrorCode.CONFLICT,
                    "Нельзя " + action + " единственного администратора — доступ к системе будет потерян. "
                            + "Сначала назначьте другого администратора.");
        }
    }

    /** Кто именно изменил учётку — без этого журнал при скомпрометированной админке бесполезен. */
    private UUID actorId() {
        return authContext.getCurrentUser().getId();
    }

    /** Снапшот полей, значимых для безопасности. Хеш пароля сюда не попадает намеренно. */
    private Map<String, Object> snapshot(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("role", user.getRole());
        map.put("companyId", user.getCompanyId());
        map.put("active", user.isActive());
        return map;
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
