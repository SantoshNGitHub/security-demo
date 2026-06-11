package com.demo.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Keycloak JWT into a Spring Security authentication token.
 *
 * Keycloak stores realm roles inside "realm_access.roles" — not in the standard
 * "scope" claim that Spring's default converter reads — so we extract them
 * manually and prefix each with "ROLE_" to satisfy hasRole() checks.
 *
 * NOTE: This class is identical to the one in security-demo-app.
 * In a multi-service project, move this to a shared "security-common" library.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Value("${app.security.jwt.principal-attribute:preferred_username}")
    private String principalAttribute;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultConverter.convert(jwt).stream(),
                extractRealmRoles(jwt).stream()
        ).collect(Collectors.toSet());

        String principal = jwt.getClaimAsString(
                principalAttribute != null ? principalAttribute : JwtClaimNames.SUB
        );

        return new JwtAuthenticationToken(jwt, authorities, principal);
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptySet();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");

        // Skip Keycloak's internal default roles
        Set<String> internalRoles = Set.of("default-roles-demo-realm", "offline_access", "uma_authorization");

        return roles.stream()
                .filter(role -> !internalRoles.contains(role))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
    }
}
