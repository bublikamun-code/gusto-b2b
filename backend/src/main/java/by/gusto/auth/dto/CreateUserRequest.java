package by.gusto.auth.dto;

import by.gusto.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 2, max = 255)
    private String fullName;

    @Size(max = 50)
    private String phone;

    @NotNull
    private Role role;

    private UUID companyId;

    /**
     * Необязательный пароль. Раньше такого поля не было вовсе, хотя UI его отправлял:
     * значение молча отбрасывалось, а пользователь создавался со случайным паролем,
     * которого администратору никто не показывал — учётку было невозможно разблокировать
     * (аудит 2026-09-30, группа «Админка»).
     */
    @Size(min = 8, max = 100, message = "Пароль должен быть не короче 8 символов")
    private String password;
}
