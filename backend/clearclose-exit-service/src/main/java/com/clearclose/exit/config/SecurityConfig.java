
package com.clearclose.exit.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---------------------------------------
    // JWT DECODER
    // ---------------------------------------

    @Bean
    public JwtDecoder jwtDecoder() {

        byte[] bytes = jwtSecret.getBytes(
            StandardCharsets.UTF_8
        );

        if (bytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 bytes"
            );
        }

        SecretKey key = new SecretKeySpec(
            bytes,
            "HmacSHA256"
        );

        NimbusJwtDecoder decoder =
            NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(
                "clearclose-auth"
            )
        );

        return decoder;
    }

    // ---------------------------------------
    // READ BORROWER ROLE FROM JWT
    // ---------------------------------------

    @Bean
    public JwtAuthenticationConverter jwtConverter() {

        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String role = jwt.getClaimAsString("role");
            String accountType =
                jwt.getClaimAsString("accountType");

            if (!"BORROWER".equals(accountType)
                    || !"BORROWER".equals(role)) {
                return List.of();
            }

            return List.of(
                new SimpleGrantedAuthority("ROLE_BORROWER")
            );
        });

        return converter;
    }

    // ---------------------------------------
    // SECURE ELIGIBILITY ENDPOINT
    // ---------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter converter
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                .requestMatchers(
                    "/api/cooling-off/**"
                ).hasRole("BORROWER")

                .anyRequest().denyAll()
            )

            .oauth2ResourceServer(oauth ->
                oauth.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(converter)
                )
            );

        return http.build();
    }
}
