
package com.clearclose.apigateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Flux;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // ---------------------------------------
    // JWT DECODER
    // ---------------------------------------

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {

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

        NimbusReactiveJwtDecoder decoder =
            NimbusReactiveJwtDecoder
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
    // CONVERT JWT ROLE INTO SPRING AUTHORITY
    // ---------------------------------------

    @Bean
    public ReactiveJwtAuthenticationConverter jwtConverter() {

        ReactiveJwtAuthenticationConverter converter =
            new ReactiveJwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return Flux.empty();
            }

            return Flux.just(
                new SimpleGrantedAuthority(
                    "ROLE_" + role
                )
            );
        });

        return converter;
    }

    // ---------------------------------------
    // SECURITY RULES
    // ---------------------------------------

    @Bean
    public SecurityWebFilterChain securityFilterChain(
        ServerHttpSecurity http,
        ReactiveJwtAuthenticationConverter jwtConverter
    ) {

        return http

            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            .cors(cors ->
                cors.configurationSource(
                    corsConfigurationSource()
                )
            )

            .authorizeExchange(auth -> auth

                // Browser preflight requests
                .pathMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                // Public authentication endpoints
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/auth/borrower/signup",
                    "/api/auth/borrower/login",
                    "/api/auth/officer/login"
                ).permitAll()

                // Loan Closure Officers
                .pathMatchers(
                    "/api/officer/cooling-off/**",
                    "/api/officer/closure/**"
                ).hasRole("LOAN_CLOSURE_OFFICER")

                // Grievance Officers
                .pathMatchers(
                    "/api/officer/grievances/**"
                ).hasRole("GRIEVANCE_OFFICER")

                // Collateral Officers
                .pathMatchers(
                    "/api/officer/collateral/**"
                ).hasRole("COLLATERAL_OFFICER")

                // Borrower collateral release actions
                .pathMatchers(HttpMethod.POST,
                    "/api/collateral-release/requests/submit"
                ).hasRole("BORROWER")
                .pathMatchers(HttpMethod.GET,
                    "/api/collateral-release/requests/my-requests"
                ).hasRole("BORROWER")

                // Collateral officer release actions
                .pathMatchers(
                    "/api/collateral-release/requests/**"
                ).hasAnyRole("COLLATERAL_OFFICER", "ADMIN")

                // Audit history is restricted to authorized officers
                .pathMatchers(HttpMethod.GET, "/api/audit/entity/**")
                .hasAnyRole("ADMIN", "LOAN_CLOSURE_OFFICER")
                .pathMatchers(HttpMethod.GET, "/api/audit", "/api/audit/user/**")
                .hasRole("ADMIN")

                // Borrower endpoints
                .pathMatchers(
                    "/api/borrower/**",
                    "/api/cooling-off/**",
                    "/api/payments/**"
                ).hasRole("BORROWER")

                // Shared authenticated endpoints
                .pathMatchers(
                    "/api/grievances/**",
                    "/api/noc/**",
                    "/api/collateral/**",
                    "/api/auth/me"
                ).authenticated()

                // Block unknown endpoints
                .anyExchange().denyAll()
            )

            .oauth2ResourceServer(oauth ->
                oauth.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtConverter
                    )
                )
            )

            .build();
    }

    // ---------------------------------------
    // CORS CONFIGURATION
    // ---------------------------------------

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config =
            new CorsConfiguration();

        config.setAllowedOrigins(
            List.of(
                "http://localhost:5173",
                "http://localhost:5174"
            )
        );

        config.setAllowedMethods(
            List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
            )
        );

        config.setAllowedHeaders(
            List.of(
                "Authorization",
                "Content-Type"
            )
        );

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            config
        );

        return source;
    }
}
