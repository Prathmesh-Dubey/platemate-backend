package com.mess.app.controller;

import com.mess.app.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness check for the hosting platform (for example the Render health check path).
 * It is public (see SecurityConfig) and deliberately does not touch the database or any
 * external service, so it answers HTTP 200 as soon as the application is up.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP"), "Service is running"));
    }
}
