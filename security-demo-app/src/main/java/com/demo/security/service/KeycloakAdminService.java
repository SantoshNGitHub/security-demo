package com.demo.security.service;

import com.demo.security.dto.SignupRequest;
import com.demo.security.exception.AuthException;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${keycloak.server-url}")      private String serverUrl;
    @Value("${keycloak.realm}")           private String realm;
    @Value("${keycloak.admin-client-id}") private String adminClientId;
    @Value("${keycloak.admin-username}")  private String adminUsername;
    @Value("${keycloak.admin-password}")  private String adminPassword;

    private Keycloak keycloakAdmin;

    @PostConstruct
    public void init() {
        keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    public void createUser(SignupRequest request) {
        RealmResource  realmResource = keycloakAdmin.realm(realm);
        UsersResource  usersResource = realmResource.users();

        // Reject duplicate usernames early (before hitting Keycloak 409)
        List<UserRepresentation> existing = usersResource.search(request.getUsername(), true);
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());

        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCredentials(Collections.singletonList(credential));

        try (Response response = usersResource.create(user)) {
            if (response.getStatus() != 201) {
                log.error("Keycloak rejected user creation — status={}", response.getStatus());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user");
            }
            String userId = extractUserId(response);
            assignRealmRole(realmResource, userId, request.getRole() != null ? request.getRole() : "USER");
            log.info("User created in Keycloak: username={} role={}", request.getUsername(), request.getRole());
        }
    }

    private String extractUserId(Response response) {
        String location = response.getHeaderString("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void assignRealmRole(RealmResource realm, String userId, String roleName) {
        try {
            RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
            realm.users().get(userId).roles().realmLevel().add(List.of(role));
        } catch (Exception e) {
            // Non-fatal: user was created but role assignment failed — admin can fix via console
            log.error("Could not assign role '{}' to user id={}: {}", roleName, userId, e.getMessage());
        }
    }
}
