package com.demo.security.service;

import com.demo.security.dto.LoginRequest;
import com.demo.security.dto.SignupRequest;
import com.demo.security.dto.TokenResponse;
import com.demo.security.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakAdminService keycloakAdminService;
    private final RestTemplate         restTemplate;

    @Value("${keycloak.server-url}") private String serverUrl;
    @Value("${keycloak.realm}")      private String realm;
    @Value("${keycloak.client-id}")  private String clientId;
    @Value("${keycloak.client-secret}") private String clientSecret;

    public void signup(SignupRequest request) {
        keycloakAdminService.createUser(request);
        log.info("Signup successful: username={}", request.getUsername());
    }

    public TokenResponse login(LoginRequest request) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "password");
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("username",      request.getUsername());
        body.add("password",      request.getPassword());
        body.add("scope",         "openid profile email");

        try {
            ResponseEntity<TokenResponse> resp = restTemplate.exchange(
                    tokenUrl(), HttpMethod.POST,
                    new HttpEntity<>(body, formHeaders()),
                    TokenResponse.class);
            log.info("Login successful: username={}", request.getUsername());
            return resp.getBody();
        } catch (HttpClientErrorException e) {
            // Log the username but never the password
            log.warn("Login failed: username={} status={}", request.getUsername(), e.getStatusCode());
            // Generic message prevents username enumeration
            throw new AuthException("Invalid username or password");
        }
    }

    public TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "refresh_token");
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        try {
            ResponseEntity<TokenResponse> resp = restTemplate.exchange(
                    tokenUrl(), HttpMethod.POST,
                    new HttpEntity<>(body, formHeaders()),
                    TokenResponse.class);
            return resp.getBody();
        } catch (HttpClientErrorException e) {
            throw new AuthException("Invalid or expired refresh token");
        }
    }

    public void logout(String refreshToken) {
        String logoutUrl = String.format("%s/realms/%s/protocol/openid-connect/logout", serverUrl, realm);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        try {
            restTemplate.exchange(logoutUrl, HttpMethod.POST,
                    new HttpEntity<>(body, formHeaders()), Void.class);
            log.info("Logout successful");
        } catch (HttpClientErrorException e) {
            log.warn("Logout call failed (token may already be expired): {}", e.getStatusCode());
        }
    }

    private String tokenUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);
    }

    private HttpHeaders formHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }
}
