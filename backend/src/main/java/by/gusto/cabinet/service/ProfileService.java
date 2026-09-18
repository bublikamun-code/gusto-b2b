package by.gusto.cabinet.service;

import by.gusto.auth.entity.User;
import by.gusto.auth.repository.UserRepository;
import by.gusto.cabinet.dto.ProfileDtos.ChangePasswordRequest;
import by.gusto.cabinet.dto.ProfileDtos.ProfileResponse;
import by.gusto.cabinet.dto.ProfileDtos.UpdateProfileRequest;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Профиль клиента (S23.1): просмотр/правка имени и телефона, смена пароля
 * с проверкой текущего. Роли — клиенты кабинета (юрлицо и физлицо).
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(User user) {
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new GustoException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Текущий пароль неверен");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Новый пароль не должен совпадать с текущим");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private ProfileResponse toResponse(User user) {
        ProfileResponse response = new ProfileResponse();
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole().name());
        return response;
    }
}
