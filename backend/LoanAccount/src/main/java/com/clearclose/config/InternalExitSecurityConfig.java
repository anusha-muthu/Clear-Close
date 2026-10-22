package com.clearclose.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** A separate JWT identity for Exit Service; ordinary user tokens are not accepted. */
@Configuration
public class InternalExitSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain internalExitSecurity(HttpSecurity http,
            @Value("${clearclose.internal.exit-secret}") String secret) throws Exception {
        JwtDecoder decoder;
        if (secret.isBlank() || secret.contains("REPLACE")) {
            decoder = token -> { throw new BadJwtException("Internal service identity is not configured."); };
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) throw new IllegalStateException("EXIT_LOAN_SERVICE_SECRET requires at least 32 bytes.");
            var jwtDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
            jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("clearclose-exit-service"),
                new JwtClaimValidator<List<String>>("aud", audiences -> audiences != null
                    && audiences.contains("clearclose-loan-account-internal"))));
            decoder = jwtDecoder;
        }
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
            "clearclose-exit-service".equals(jwt.getSubject())
                && "EXIT_SERVICE".equals(jwt.getClaimAsString("serviceRole"))
                ? List.of(new SimpleGrantedAuthority("ROLE_EXIT_SERVICE")) : List.of());
        http.securityMatcher("/api/internal/loans/**")
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/internal/loans/*/complete-cooling-off-exit")
                .hasRole("EXIT_SERVICE")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)
                .jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
