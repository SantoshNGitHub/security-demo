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
@RequestMapping("/api/moderator")
// ADMIN inherits moderator access via this annotation; no separate role hierarchy needed
@PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
public class ModeratorController {

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> reports(@AuthenticationPrincipal Jwt jwt) {
        log.info("Moderation reports accessed by username={}", jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<List<Map<String, String>>>builder()
                .success(true)
                .message("Moderation reports — accessible by MODERATOR and ADMIN")
                .data(List.of(
                        Map.of("id", "1", "type", "content_review", "status", "pending",  "reporter", "user1"),
                        Map.of("id", "2", "type", "user_report",    "status", "resolved", "reporter", "user2")
                ))
                .build());
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> managedUsers() {
        return ResponseEntity.ok(ApiResponse.<List<Map<String, String>>>builder()
                .success(true)
                .message("User management — accessible by MODERATOR and ADMIN")
                .data(List.of(
                        Map.of("username", "user1", "status", "active",    "joinedDate", "2024-01-01"),
                        Map.of("username", "user2", "status", "suspended", "joinedDate", "2024-01-05")
                ))
                .build());
    }
}
