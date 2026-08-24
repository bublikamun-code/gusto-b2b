package by.gusto.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        return envelope(Map.of("status", "UP", "service", "gusto-b2b-backend"));
    }

    @GetMapping("/readyz")
    public Map<String, Object> readyz() {
        return envelope(Map.of("status", "READY"));
    }

    private Map<String, Object> envelope(Object data) {
        return Map.of("data", data, "meta", Map.of(), "error", Map.of());
    }
}
