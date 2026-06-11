package com.demo.product.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for product-service.
 *
 * Every microservice owns its own SecurityConfig because each service has
 * different endpoint paths and role requirements. The JWT validation
 * mechanism (JwtAuthConverter + Keycloak JWKS) is the same across services,
 * but the authorization rules here are product-specific.
 *
 * In a project with many services, extract JwtAuthConverter into a shared
 * Maven library (e.g. security-common) to avoid duplication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST — no sessions, no CSRF needed
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults())

            // Product-service specific authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // Read access — any authenticated user
                .requestMatchers(HttpMethod.GET, "/products/**").hasAnyRole("USER", "MODERATOR", "ADMIN")

                // Write access — moderators and admins
                .requestMatchers(HttpMethod.POST,   "/products").hasAnyRole("MODERATOR", "ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/products/**").hasAnyRole("MODERATOR", "ADMIN")

                // Delete — admin only
                .requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )

            // Validate Bearer JWTs against Keycloak's JWKS endpoint
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
            );

        return http.build();
    }
}
