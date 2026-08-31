package by.gusto.health;

import by.gusto.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ApiResponse<Map<String, String>> healthz() {
        return ApiResponse.success(Map.of("status", "UP", "service", "gusto-b2b-backend"));
    }

    @GetMapping("/readyz")
    public ApiResponse<Map<String, String>> readyz() {
        return ApiResponse.success(Map.of("status", "READY"));
    }
}
