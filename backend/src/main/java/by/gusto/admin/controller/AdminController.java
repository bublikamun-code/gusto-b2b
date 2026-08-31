package by.gusto.admin.controller;

import by.gusto.auth.mapper.UserMapper;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<?>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(userMapper.toResponseList(userRepository.findAll())));
    }
}
