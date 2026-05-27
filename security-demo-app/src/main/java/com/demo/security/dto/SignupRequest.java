package com.demo.security.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$",
             message = "Username may only contain letters, numbers and underscores")
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$",
        message = "Password must contain at least one digit, lowercase letter, uppercase letter, and special character"
    )
    private String password;

    @NotBlank
    @Size(min = 1, max = 50)
    private String firstName;

    @NotBlank
    @Size(min = 1, max = 50)
    private String lastName;

    // Users can self-register as USER or MODERATOR; ADMIN is granted only via Keycloak console
    @Pattern(regexp = "^(USER|MODERATOR)$", message = "Role must be USER or MODERATOR")
    private String role = "USER";
}
