package by.gusto.cms.controller;

import by.gusto.common.settings.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Публичные тексты лендинга (S38): правятся в админке, на витрине —
 * значения из настроек, при отсутствии клиент использует свои дефолты.
 */
@RestController
@RequiredArgsConstructor
public class PublicLandingController {

    private final SettingsService settingsService;

    @GetMapping("/api/v1/cms/landing")
    public ResponseEntity<Map<String, JsonNode>> landing() {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        JsonNode hero = settingsService.getObject(SettingsService.LANDING_HERO);
        JsonNode delivery = settingsService.getObject(SettingsService.LANDING_DELIVERY);
        result.put("hero", hero == null ? objectMapperNullNode() : hero);
        result.put("delivery", delivery == null ? objectMapperNullNode() : delivery);
        return ResponseEntity.ok(result);
    }

    private JsonNode objectMapperNullNode() {
        return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    }
}
