
package com.clearclose.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoOfficerSeeder {

    @Bean
    @ConditionalOnProperty(
        name = "app.demo.seed-officers",
        havingValue = "true"
    )
    public CommandLineRunner seedOfficers(
        JdbcTemplate jdbc,
        PasswordEncoder encoder,
        @Value("${app.demo.officer-password}") String password
    ) {
        return args -> {

            if (password.length() < 8) {
                throw new IllegalStateException(
                    "Set DEMO_OFFICER_PASSWORD to at least 8 characters"
                );
            }

            createOfficer(
                jdbc, encoder, password,
                "Amit Patel", "demo.loanofficer",
                "amit.demo@example.com",
                "LOAN_CLOSURE_OFFICER"
            );

            createOfficer(
                jdbc, encoder, password,
                "Priya Shah", "demo.grievance",
                "priya.demo@example.com",
                "GRIEVANCE_OFFICER"
            );

            createOfficer(
                jdbc, encoder, password,
                "Rahul Mehta", "demo.collateral",
                "rahul.demo@example.com",
                "COLLATERAL_OFFICER"
            );
        };
    }

    private void createOfficer(
        JdbcTemplate jdbc,
        PasswordEncoder encoder,
        String password,
        String name,
        String username,
        String email,
        String role
    ) {

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM OFFICER WHERE USERNAME = ?",
            Integer.class,
            username
        );

        if (count != null && count > 0) {
            return;
        }

        jdbc.update(
            """
            INSERT INTO OFFICER
            (FULL_NAME, USERNAME, PASSWORD_HASH, EMAIL, ROLE)
            VALUES (?, ?, ?, ?, ?)
            """,
            name,
            username,
            encoder.encode(password),
            email,
            role
        );
    }
}
