
package com.clearclose.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoBorrowerSeeder {

    @Bean
    @ConditionalOnProperty(
        name = "app.demo.seed-borrowers",
        havingValue = "true"
    )
    public CommandLineRunner seedBorrowers(
        JdbcTemplate jdbc,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {

            // Borrower 1
            addBorrower(
                jdbc, passwordEncoder,
                "Aarav Verma",
                "aarav",
                "Aarav@12345",
                "aarav.demo@example.com",
                "9000000101",
                "ABCDE1234F",
                "Mumbai"
            );

            // Borrower 2
            addBorrower(
                jdbc, passwordEncoder,
                "Neha Kapoor",
                "neha",
                "Neha@12345",
                "neha.demo@example.com",
                "9000000102",
                "FGHIJ5678K",
                "Pune"
            );

            // Borrower 3
            addBorrower(
                jdbc, passwordEncoder,
                "Vikram Singh",
                "vikram",
                "Vikram@12345",
                "vikram.demo@example.com",
                "9000000103",
                "KLMNO1234P",
                "Hyderabad"
            );

            // Borrower 4
            addBorrower(
                jdbc, passwordEncoder,
                "Ananya Rao",
                "ananya",
                "Ananya@12345",
                "ananya.demo@example.com",
                "9000000104",
                "PQRST5678U",
                "Bangalore"
            );

            // Borrower 5
            addBorrower(
                jdbc, passwordEncoder,
                "Kunal Shah",
                "kunal",
                "Kunal@12345",
                "kunal.demo@example.com",
                "9000000105",
                "UVWXY1234Z",
                "Delhi"
            );

            System.out.println("Demo borrower seeding completed!");
        };
    }

    private void addBorrower(
        JdbcTemplate jdbc,
        PasswordEncoder passwordEncoder,
        String name,
        String username,
        String password,
        String email,
        String phone,
        String panCard,
        String location
    ) {

        // Check whether borrower already exists
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM BORROWERS WHERE USERNAME = ?",
            Integer.class,
            username
        );

        if (count != null && count > 0) {
            System.out.println(username + " already exists. Skipping.");
            return;
        }

        // Convert password to BCrypt hash
        String hashedPassword = passwordEncoder.encode(password);

        // Insert borrower into Oracle Database
        String sql = """
            INSERT INTO BORROWERS
            (
                NAME,
                USERNAME,
                PASSWORD_HASH,
                EMAIL,
                PHONE,
                PAN_CARD,
                LOCATION,
                ACCOUNT_STATUS
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """;

        jdbc.update(
            sql,
            name,
            username,
            hashedPassword,
            email,
            phone,
            panCard,
            location
        );

        System.out.println(username + " created successfully!");
    }
}