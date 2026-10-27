package com.ofss.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;
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

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }

        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer("clearclose-auth"));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String accountType = jwt.getClaimAsString("accountType");
            String role = jwt.getClaimAsString("role");

            if ("BORROWER".equals(accountType) && "BORROWER".equals(role)) {
                return List.of(new SimpleGrantedAuthority("ROLE_BORROWER"));
            }

            if ("OFFICER".equals(accountType)
                    && role != null
                    && List.of(
                            "LOAN_CLOSURE_OFFICER",
                            "GRIEVANCE_OFFICER",
                            "SENIOR_GRIEVANCE_OFFICER",
                            "COLLATERAL_OFFICER"
                    ).contains(role)) {
                return List.of(new SimpleGrantedAuthority("ROLE_" + role));
            }

            return List.of();
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174"));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/grievances")
                        .hasRole("BORROWER")
                        .requestMatchers(HttpMethod.GET, "/api/grievances/borrower/**")
                        .hasRole("BORROWER")

                        // Broad lists and manual assignment are administrator-only.
                        .requestMatchers(HttpMethod.GET,
                                "/api/grievances",
                                "/api/grievances/status/**",
                                "/api/grievances/loan/**",
                                "/api/grievances/overdue",
                                "/api/grievances/senior/overdue-notifications")
                        .hasRole("SENIOR_GRIEVANCE_OFFICER")
                        .requestMatchers(HttpMethod.PATCH, "/api/grievances/*/assign")
                        .hasRole("SENIOR_GRIEVANCE_OFFICER")

                        // Controller/service must still check the authenticated
                        // officer identity and the record's ASSIGNED_TO value.
                        .requestMatchers(HttpMethod.GET, "/api/grievances/officer/**")
                        .hasAnyRole(
                                "LOAN_CLOSURE_OFFICER", "GRIEVANCE_OFFICER",
                                "COLLATERAL_OFFICER","SENIOR_GRIEVANCE_OFFICER")
                        
                        .requestMatchers(HttpMethod.PATCH, "/api/grievances/*/status")
                        .hasAnyRole(
                                "LOAN_CLOSURE_OFFICER", "GRIEVANCE_OFFICER",
                                "COLLATERAL_OFFICER","SENIOR_GRIEVANCE_OFFICER")

                        .requestMatchers(HttpMethod.GET,
                                "/api/grievances/*", "/api/grievances/*/history")
                        .hasAnyRole(
                                "BORROWER", "LOAN_CLOSURE_OFFICER",
                                "GRIEVANCE_OFFICER", "COLLATERAL_OFFICER","SENIOR_GRIEVANCE_OFFICER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
