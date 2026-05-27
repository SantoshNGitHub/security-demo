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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
// All three roles inherit user-level access
@PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
public class UserController {

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> profile(@AuthenticationPrincipal Jwt jwt) {
        log.info("Profile accessed by username={}", jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("User profile")
                .data(Map.of(
                        "username",  jwt.getClaimAsString("preferred_username"),
                        "email",     nullSafe(jwt.getClaimAsString("email")),
                        "firstName", nullSafe(jwt.getClaimAsString("given_name")),
                        "lastName",  nullSafe(jwt.getClaimAsString("family_name")),
                        "roles",     jwt.getClaim("realm_access")
                ))
                .build());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, String>>> dashboard(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .message("User dashboard — accessible by USER, MODERATOR, ADMIN")
                .data(Map.of(
                        "welcome",     "Welcome, " + jwt.getClaimAsString("preferred_username"),
                        "accessLevel", "USER"
                ))
                .build());
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
