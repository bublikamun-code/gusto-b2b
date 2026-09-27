package by.gusto.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Email
    @Size(max = 254, message = "Слишком длинный e-mail")
    private String email;

    // Без верхней границы тело логина не ограничено — длинный пароль уходил
    // в BCrypt и хешировался как есть (S44).
    @NotBlank
    @Size(max = 128, message = "Пароль длиннее 128 символов")
    private String password;

    // Поле принимает и TOTP (шесть цифр), и recovery-код (10–11 букв и цифр)
    // — иначе вход по recovery-коду отбивался бы валидацией до проверки (S44).
    @Pattern(regexp = "\\d{6}|[A-Za-z0-9]{10,11}", message = "Код 2FA: шесть цифр или код восстановления")
    private String totpCode;
}
