
package com.clearclose.exit.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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

    // -----------------------------------------
    // JWT DECODER
    // -----------------------------------------

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

    // -----------------------------------------
    // JWT ROLE CONVERTER
    // -----------------------------------------

    @Bean
    public JwtAuthenticationConverter jwtConverter() {

        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String accountType =
                jwt.getClaimAsString("accountType");

            String role =
                jwt.getClaimAsString("role");

            // Borrower account
            if ("BORROWER".equals(accountType)
                    && "BORROWER".equals(role)) {

                return List.of(
                    new SimpleGrantedAuthority(
                        "ROLE_BORROWER"
                    )
                );
            }

            // Loan Closure Officer account
            if ("OFFICER".equals(accountType)
                    && "LOAN_CLOSURE_OFFICER".equals(role)) {

                return List.of(
                    new SimpleGrantedAuthority(
                        "ROLE_LOAN_CLOSURE_OFFICER"
                    )
                );
            }

            // All other roles
            return List.of();
        });

        return converter;
    }

    // -----------------------------------------
    // SECURITY RULES
    // -----------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter converter
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Allow the original error response to reach the client.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                // Officer Dashboard
                .requestMatchers(
                    "/api/officer/cooling-off/**"
                ).hasRole("LOAN_CLOSURE_OFFICER")

                // Borrower Cooling-Off APIs
                .requestMatchers(
                    "/api/cooling-off/**",
                    "/api/payments/**"
                ).hasRole("BORROWER")
                
             // Loan Pre-Closure: officer APIs
                .requestMatchers(
                    "/api/pre-closures/officer/**"
                ).hasRole("LOAN_CLOSURE_OFFICER")

                // Loan Pre-Closure: borrower APIs
                .requestMatchers(
                    "/api/pre-closures/**"
                ).hasRole("BORROWER")
                
                // Block everything else
                .anyRequest().denyAll()
            )

            .oauth2ResourceServer(oauth ->
                oauth.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        converter
                    )
                )
            );

        return http.build();
    }
}
