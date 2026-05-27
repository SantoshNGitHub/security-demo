package com.demo.security.controller;

import com.demo.security.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @GetMapping("/hello")
    public ResponseEntity<ApiResponse<Map<String, String>>> hello() {
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .message("Public endpoint — no authentication required")
                .data(Map.of("greeting", "Hello, World!", "note", "Anyone can call this endpoint"))
                .build());
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .message("Service is healthy")
                .data(Map.of("status", "UP"))
                .build());
    }
}
