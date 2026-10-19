
package com.clearclose.auth;

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
                "Ishaan Nair",
                "ishaan_cc26",
                "Ishaan@12345",
                "ishaan.cc26@example.com",
                "9000000201",
                "LMQRS2468T",
                "Mumbai"
            );

            // Borrower 2
            addBorrower(
                jdbc, passwordEncoder,
                "Meera Iyer",
                "meera_cc26",
                "Meera@12345",
                "meera.cc26@example.com",
                "9000000202",
                "NPTUV1357W",
                "Chennai"
            );

            // Borrower 3
            addBorrower(
                jdbc, passwordEncoder,
                "Dev Malhotra",
                "dev_cc26",
                "Dev@12345",
                "dev.cc26@example.com",
                "9000000203",
                "BCDFG8642H",
                "Delhi"
            );

            // Borrower 4
            addBorrower(
                jdbc, passwordEncoder,
                "Sana Kulkarni",
                "sana_cc26",
                "Sana@12345",
                "sana.cc26@example.com",
                "9000000204",
                "JKMNP9753Q",
                "Pune"
            );

            // Borrower 5
            addBorrower(
                jdbc, passwordEncoder,
                "Kabir Sethi",
                "kabir_cc26",
                "Kabir@12345",
                "kabir.cc26@example.com",
                "9000000205",
                "RSTVW4206X",
                "Hyderabad"
            );

            System.out.println(
                "Demo borrower seeding completed!"
            );
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

        // Check username, email and PAN duplicates
        String checkSql = """
            SELECT COUNT(*)
            FROM BORROWERS
            WHERE USERNAME = ?
               OR EMAIL = ?
               OR PAN_CARD = ?
            """;

        Integer count = jdbc.queryForObject(
            checkSql,
            Integer.class,
            username,
            email,
            panCard
        );

        if (count != null && count > 0) {
            System.out.println(
                username + " already exists or has " +
                "conflicting details. Skipping."
            );
            return;
        }

        // Convert password into BCrypt hash
        String hashedPassword =
            passwordEncoder.encode(password);

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

        System.out.println(
            username + " created successfully!"
        );
    }
}
