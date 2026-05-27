package com.demo.security.controller;

import com.demo.security.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(@AuthenticationPrincipal Jwt jwt) {
        log.info("Admin dashboard accessed by username={}", jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Admin dashboard — ADMIN only")
                .data(Map.of(
                        "adminUser",      jwt.getClaimAsString("preferred_username"),
                        "totalUsers",     150,
                        "activeUsers",    120,
                        "suspendedUsers", 10,
                        "systemStatus",   "HEALTHY"
                ))
                .build());
    }

    @GetMapping("/system")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> systemInfo() {
        return ResponseEntity.ok(ApiResponse.<List<Map<String, String>>>builder()
                .success(true)
                .message("System info — ADMIN only")
                .data(List.of(
                        Map.of("component", "database", "status", "UP", "latencyMs", "5"),
                        Map.of("component", "keycloak", "status", "UP", "latencyMs", "10"),
                        Map.of("component", "cache",    "status", "UP", "latencyMs", "1")
                ))
                .build());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> auditLogs() {
        return ResponseEntity.ok(ApiResponse.<List<Map<String, String>>>builder()
                .success(true)
                .message("Audit logs — ADMIN only")
                .data(List.of(
                        Map.of("action", "LOGIN",           "user", "user1", "ip", "10.0.0.1", "ts", "2024-01-15T10:00:00"),
                        Map.of("action", "LOGOUT",          "user", "user2", "ip", "10.0.0.2", "ts", "2024-01-15T10:05:00"),
                        Map.of("action", "RESOURCE_ACCESS", "user", "user1", "ip", "10.0.0.1", "ts", "2024-01-15T10:01:00")
                ))
                .build());
    }
}
