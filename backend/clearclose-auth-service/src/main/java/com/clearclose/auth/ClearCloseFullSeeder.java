
package com.clearclose.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ClearCloseFullSeeder {

    @Bean
    @ConditionalOnProperty(
        name = "app.demo.seed-all",
        havingValue = "true"
    )
    public CommandLineRunner seedAll(
        JdbcTemplate jdbc,
        PasswordEncoder encoder
    ) {
        return args -> {

            // ========================================
            // 1. BORROWERS
            // ========================================

            Object[][] borrowers = {
                {1101, "Tara Desai", "tara_demo26",
                 "Tara@12345", "tara26@example.com",
                 "9110000101", "QJXTR4826L", "Mumbai"},

                {1102, "Nikhil Batra", "nikhil_demo26",
                 "Nikhil@12345", "nikhil26@example.com",
                 "9110000102", "VPLMK7315D", "Pune"},

                {1103, "Pooja Menon", "pooja_demo26",
                 "Pooja@12345", "pooja26@example.com",
                 "9110000103", "HRWQS2954N", "Chennai"},

                {1104, "Aditya Bose", "aditya_demo26",
                 "Aditya@12345", "aditya26@example.com",
                 "9110000104", "KZTNP6083F", "Delhi"},

                {1105, "Farah Khan", "farah_demo26",
                 "Farah@12345", "farah26@example.com",
                 "9110000105", "DMCVR1749P", "Hyderabad"}
            };

            for (Object[] b : borrowers) {
                insert(
                    jdbc, "BORROWERS", "USER_ID",
                    (int) b[0],
                    """
                    INSERT INTO BORROWERS
                    (USER_ID, NAME, USERNAME, PASSWORD_HASH,
                     EMAIL, PHONE, PAN_CARD, LOCATION)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    b[0], b[1], b[2],
                    encoder.encode((String) b[3]),
                    b[4], b[5], b[6], b[7]
                );
            }

            // ========================================
            // 2. OFFICER
            // ========================================

            Object[][] officers = {
                {2101, "Nitin Rao", "nitin_officer26",
                 "Nitin@12345", "nitin26@bank.example",
                 "LOAN_CLOSURE_OFFICER"},

                {2102, "Kavya Jain", "kavya_officer26",
                 "Kavya@12345", "kavya26@bank.example",
                 "GRIEVANCE_OFFICER"},

                {2103, "Om Prakash", "om_officer26",
                 "Om@12345", "om26@bank.example",
                 "COLLATERAL_OFFICER"}
            };

            for (Object[] o : officers) {
                insert(
                    jdbc, "OFFICER", "OFFICER_ID",
                    (int) o[0],
                    """
                    INSERT INTO OFFICER
                    (OFFICER_ID, FULL_NAME, USERNAME,
                     PASSWORD_HASH, EMAIL, ROLE)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    o[0], o[1], o[2],
                    encoder.encode((String) o[3]),
                    o[4], o[5]
                );
            }

            // ========================================
            // 3. LOAN_PRODUCT_RULE
            // ========================================

            insert(jdbc, "LOAN_PRODUCT_RULE", "RULE_ID", 3101,
                """
                INSERT INTO LOAN_PRODUCT_RULE
                (RULE_ID, PRODUCT_TYPE, COOLING_OFF_DAYS,
                 CLOSURE_CHARGE_TYPE, EFFECTIVE_FROM, COOLING_OFF_PROCESSING_FEE)
                VALUES
                (3101, 'PERSONAL_LOAN', 5,
                 'NONE', DATE '2026-01-01', 500)
                """
            );

            insert(jdbc, "LOAN_PRODUCT_RULE", "RULE_ID", 3102,
                """
                INSERT INTO LOAN_PRODUCT_RULE
                (RULE_ID, PRODUCT_TYPE, COOLING_OFF_DAYS,
                 CLOSURE_CHARGE_TYPE, CLOSURE_CHARGE_VALUE,
                 EFFECTIVE_FROM, COOLING_OFF_PROCESSING_FEE)
                VALUES
                (3102, 'CAR_LOAN', 5,
                 'PERCENTAGE', 2, DATE '2026-01-01', 500)
                """
            );

            // ========================================
            // 4. LOAN_ACCOUNT
            // ========================================

            // Tara: Active, cooling-off eligible
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4101,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS)
                VALUES
                (4101, 1101, 3101, 'CC-PL-4101',
                 120000, 120000, 12, 24,
                 DATE '2026-10-18', DATE '2028-10-18', 'ACTIVE')
                """
            );

            // Nikhil: Cooling-off exit completed
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4102,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE,
                 LOAN_STATUS, CLOSURE_DATE)
                VALUES
                (4102, 1102, 3101, 'CC-PL-4102',
                 150000, 0, 12, 24,
                 DATE '2026-10-10', DATE '2028-10-10',
                 'EXIT_COMPLETED', DATE '2026-10-12')
                """
            );

            // Pooja: Car loan closed early
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4103,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE,
                 LOAN_STATUS, CLOSURE_DATE)
                VALUES
                (4103, 1103, 3102, 'CC-CAR-4103',
                 900000, 0, 9, 60,
                 DATE '2024-03-01', DATE '2029-03-01',
                 'CLOSED', DATE '2026-10-15')
                """
            );

            // Aditya: Active personal loan
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4104,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS)
                VALUES
                (4104, 1104, 3101, 'CC-PL-4104',
                 250000, 180000, 11, 36,
                 DATE '2025-04-01', DATE '2028-04-01', 'ACTIVE')
                """
            );

            // Farah: Active car loan
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4105,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS)
                VALUES
                (4105, 1105, 3102, 'CC-CAR-4105',
                 650000, 430000, 8, 60,
                 DATE '2025-09-01', DATE '2030-09-01', 'ACTIVE')
                """
            );

            // ========================================
            // 5. COOLING_OFF_REQUEST
            // ========================================

            insert(jdbc, "COOLING_OFF_REQUEST",
                "EXIT_REQUEST_ID", 5101,
                """
                INSERT INTO COOLING_OFF_REQUEST
                (EXIT_REQUEST_ID, LOAN_ID, OFFICER_ID,
                 REQUEST_DATE, EXPIRY_DATE, STATUS, REASON)
                VALUES
                (5101, 4101, 2101,
                 DATE '2026-10-19', DATE '2026-10-23',
                 'PENDING', 'Loan no longer required')
                """
            );

            insert(jdbc, "COOLING_OFF_REQUEST",
                "EXIT_REQUEST_ID", 5102,
                """
                INSERT INTO COOLING_OFF_REQUEST
                (EXIT_REQUEST_ID, LOAN_ID, OFFICER_ID,
                 REQUEST_DATE, EXPIRY_DATE, STATUS,
                 REASON, DECIDED_AT)
                VALUES
                (5102, 4102, 2101,
                 DATE '2026-10-11', DATE '2026-10-15',
                 'COMPLETED', 'Changed financial decision',
                 TIMESTAMP '2026-10-12 14:00:00')
                """
            );

            // ========================================
            // 6. LOAN_CLOSURE_REQUEST
            // ========================================

            insert(jdbc, "LOAN_CLOSURE_REQUEST",
                "CLOSURE_REQUEST_ID", 6101,
                """
                INSERT INTO LOAN_CLOSURE_REQUEST
                (CLOSURE_REQUEST_ID, LOAN_ID, OFFICER_ID,
                 REQUEST_DATE, CLOSURE_TYPE, STATUS, REASON)
                VALUES
                (6101, 4103, 2101,
                 DATE '2026-10-14', 'FORECLOSURE',
                 'COMPLETED', 'Early settlement requested')
                """
            );

            // ========================================
            // 7. SETTLEMENT_QUOTE
            // ========================================

            // Tara: Available quote
            insert(jdbc, "SETTLEMENT_QUOTE",
                "QUOTE_ID", 7101,
                """
                INSERT INTO SETTLEMENT_QUOTE
                (QUOTE_ID, LOAN_ID, EXIT_REQUEST_ID,
                 QUOTE_TYPE, PRINCIPAL_AMOUNT,
                 INTEREST_AMOUNT, TOTAL_AMOUNT,
                 VALID_UNTIL, QUOTE_STATUS)
                VALUES
                (7101, 4101, 5101,
                 'COOLING_OFF', 120000,
                 39.45, 120039.45,
                 DATE '2026-10-20', 'AVAILABLE')
                """
            );

            // Nikhil: Paid cooling-off quote
            insert(jdbc, "SETTLEMENT_QUOTE",
                "QUOTE_ID", 7102,
                """
                INSERT INTO SETTLEMENT_QUOTE
                (QUOTE_ID, LOAN_ID, EXIT_REQUEST_ID,
                 QUOTE_TYPE, PRINCIPAL_AMOUNT,
                 INTEREST_AMOUNT, TOTAL_AMOUNT,
                 VALID_UNTIL, QUOTE_STATUS, ACCEPTED_AT)
                VALUES
                (7102, 4102, 5102,
                 'COOLING_OFF', 150000,
                 98.63, 150098.63,
                 DATE '2026-10-13', 'PAID',
                 TIMESTAMP '2026-10-11 11:00:00')
                """
            );

            // Pooja: Paid closure quote
            insert(jdbc, "SETTLEMENT_QUOTE",
                "QUOTE_ID", 7103,
                """
                INSERT INTO SETTLEMENT_QUOTE
                (QUOTE_ID, LOAN_ID, CLOSURE_REQUEST_ID,
                 QUOTE_TYPE, PRINCIPAL_AMOUNT,
                 INTEREST_AMOUNT, CHARGES_AMOUNT,
                 TAX_AMOUNT, TOTAL_AMOUNT,
                 VALID_UNTIL, QUOTE_STATUS, ACCEPTED_AT)
                VALUES
                (7103, 4103, 6101,
                 'CLOSURE', 350000,
                 1800, 7000, 1260, 360060,
                 DATE '2026-10-17', 'PAID',
                 TIMESTAMP '2026-10-14 10:00:00')
                """
            );

            // ========================================
            // 8. PAYMENT
            // ========================================

            insert(jdbc, "PAYMENT", "PAYMENT_ID", 8102,
                """
                INSERT INTO PAYMENT
                (PAYMENT_ID, QUOTE_ID, RAZORPAY_ORDER_ID,
                 RAZORPAY_PAYMENT_ID, AMOUNT, PAYMENT_STATUS,
                 PAYMENT_DATE, METHOD, CONFIRMED_AT)
                VALUES
                (8102, 7102, 'order_cc_8102',
                 'pay_cc_8102', 150098.63, 'SUCCESS',
                 DATE '2026-10-12', 'UPI',
                 TIMESTAMP '2026-10-12 12:00:00')
                """
            );

            insert(jdbc, "PAYMENT", "PAYMENT_ID", 8103,
                """
                INSERT INTO PAYMENT
                (PAYMENT_ID, QUOTE_ID, RAZORPAY_ORDER_ID,
                 RAZORPAY_PAYMENT_ID, AMOUNT, PAYMENT_STATUS,
                 PAYMENT_DATE, METHOD, CONFIRMED_AT)
                VALUES
                (8103, 7103, 'order_cc_8103',
                 'pay_cc_8103', 360060, 'SUCCESS',
                 DATE '2026-10-14', 'UPI',
                 TIMESTAMP '2026-10-14 13:00:00')
                """
            );

            // ========================================
            // 9. NOC
            // ========================================

            insert(jdbc, "NOC", "NOC_ID", 9101,
                """
                INSERT INTO NOC
                (NOC_ID, LOAN_ID, CERTIFICATE_NUMBER,
                 ISSUE_DATE, FILE_PATH)
                VALUES
                (9101, 4103, 'NOC-CC-9101',
                 DATE '2026-10-15',
                 '/docs/noc_cc_9101.pdf')
                """
            );

            // ========================================
            // 10. GRIEVANCES
            // ========================================

            insert(jdbc, "GRIEVANCES",
                "GRIEVANCE_ID", 10101,
                """
                INSERT INTO GRIEVANCES
                (GRIEVANCE_ID, LOAN_ACCOUNT_ID, BORROWER_ID,
                 CATEGORY, DESCRIPTION, STATUS, ASSIGNED_TO,
                 RESOLUTION_REMARKS, EXPECTED_RESOLUTION_AT)
                VALUES
                (10101, 4103, 1103,
                 'NOC_DELAY',
                 'Unable to access NOC after closure.',
                 'RESOLVED', 2102,
                 'NOC download issue resolved.',
                 TIMESTAMP '2026-10-20 18:00:00')
                """
            );

            insert(jdbc, "GRIEVANCES",
                "GRIEVANCE_ID", 10102,
                """
                INSERT INTO GRIEVANCES
                (GRIEVANCE_ID, LOAN_ACCOUNT_ID, BORROWER_ID,
                 CATEGORY, DESCRIPTION, STATUS, ASSIGNED_TO,
                 EXPECTED_RESOLUTION_AT)
                VALUES
                (10102, 4104, 1104,
                 'PAYMENT_QUERY',
                 'Need clarification on loan charges.',
                 'OPEN', 2102,
                 TIMESTAMP '2026-10-25 18:00:00')
                """
            );

            // ========================================
            // 11. GRIEVANCES_HISTORY
            // ========================================

            insert(jdbc, "GRIEVANCES_HISTORY",
                "HISTORY_ID", 11101,
                """
                INSERT INTO GRIEVANCES_HISTORY
                (HISTORY_ID, GRIEVANCE_ID, OLD_STATUS,
                 NEW_STATUS, ACTION_REMARKS,
                 ACTION_BY, ACTOR_TYPE, ACTION_AT)
                VALUES
                (11101, 10101, NULL, 'OPEN',
                 'Complaint submitted by borrower.',
                 1103, 'BORROWER',
                 TIMESTAMP '2026-10-16 09:00:00')
                """
            );

            insert(jdbc, "GRIEVANCES_HISTORY",
                "HISTORY_ID", 11102,
                """
                INSERT INTO GRIEVANCES_HISTORY
                (HISTORY_ID, GRIEVANCE_ID, OLD_STATUS,
                 NEW_STATUS, ACTION_REMARKS,
                 ACTION_BY, ACTOR_TYPE, ACTION_AT)
                VALUES
                (11102, 10101, 'OPEN', 'RESOLVED',
                 'NOC download issue fixed.',
                 2102, 'OFFICER',
                 TIMESTAMP '2026-10-17 15:00:00')
                """
            );

            insert(jdbc, "GRIEVANCES_HISTORY",
                "HISTORY_ID", 11103,
                """
                INSERT INTO GRIEVANCES_HISTORY
                (HISTORY_ID, GRIEVANCE_ID, OLD_STATUS,
                 NEW_STATUS, ACTION_REMARKS,
                 ACTION_BY, ACTOR_TYPE, ACTION_AT)
                VALUES
                (11103, 10102, NULL, 'OPEN',
                 'New payment query submitted.',
                 1104, 'BORROWER',
                 TIMESTAMP '2026-10-18 11:00:00')
                """
            );

            // ========================================
            // 12. COLLATERAL
            // ========================================

            insert(jdbc, "COLLATERAL",
                "COLLATERAL_ID", 12101,
                """
                INSERT INTO COLLATERAL
                (COLLATERAL_ID, LOAN_ACCOUNT_ID,
                 COLLATERAL_TYPE, DESCRIPTION,
                 FILE_NAME, STORAGE_PATH, CUSTODIAN, RELEASE_STATUS)
                VALUES
                (12101, 4103, 'VEHICLE',
                 'Demo vehicle registration documents',
                 'demo-vehicle-documents.pdf', 'demo/collateral/demo-vehicle-documents.pdf',
                 'Loan Operations', 'RELEASED')
                """
            );

            // ========================================
            // 13. COLLATERAL_RELEASE_REQUESTS
            // ========================================

            insert(jdbc, "COLLATERAL_RELEASE_REQUESTS",
                "REQUEST_ID", 13101,
                """
                INSERT INTO COLLATERAL_RELEASE_REQUESTS
                (REQUEST_ID, COLLATERAL_ID, LOAN_ACCOUNT_ID,
                 REQUESTED_BY, ASSIGNED_TO, STATUS,
                 REASON, EXPECTED_RELEASE_DATE,
                 RELEASED_AT, RELEASE_REFERENCE)
                VALUES
                (13101, 12101, 4103,
                 1103, 2103, 'RELEASED',
                 'Vehicle hypothecation release completed',
                 DATE '2026-10-22',
                 TIMESTAMP '2026-10-18 14:00:00',
                 'REL-CC-13101')
                """
            );

            // ========================================
            // 14. AUDIT_LOG
            // ========================================

            insert(jdbc, "AUDIT_LOG", "LOG_ID", 14101,
                """
                INSERT INTO AUDIT_LOG
                (LOG_ID, USER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, DETAILS, EXIT_REQUEST_ID)
                VALUES
                (14101, 1101, 5101, 'COOLING_OFF_REQUEST',
                 'EXIT_REQUEST_SUBMITTED',
                 'Tara submitted a cooling-off exit request',
                 5101)
                """
            );

            insert(jdbc, "AUDIT_LOG", "LOG_ID", 14102,
                """
                INSERT INTO AUDIT_LOG
                (LOG_ID, OFFICER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, DETAILS, PAYMENT_ID)
                VALUES
                (14102, 2101, 8102, 'PAYMENT',
                 'PAYMENT_VERIFIED',
                 'Cooling-off payment verified',
                 8102)
                """
            );

            insert(jdbc, "AUDIT_LOG", "LOG_ID", 14103,
                """
                INSERT INTO AUDIT_LOG
                (LOG_ID, OFFICER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, DETAILS, CLOSURE_REQUEST_ID)
                VALUES
                (14103, 2101, 6101, 'LOAN_CLOSURE_REQUEST',
                 'CLOSURE_APPROVED',
                 'Car loan closure approved',
                 6101)
                """
            );

            insert(jdbc, "AUDIT_LOG", "LOG_ID", 14104,
                """
                INSERT INTO AUDIT_LOG
                (LOG_ID, OFFICER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, DETAILS, GRIEVANCE_ID)
                VALUES
                (14104, 2102, 10101, 'GRIEVANCE',
                 'GRIEVANCE_RESOLVED',
                 'NOC grievance resolved',
                 10101)
                """
            );

            insert(jdbc, "AUDIT_LOG", "LOG_ID", 14105,
                """
                INSERT INTO AUDIT_LOG
                (LOG_ID, OFFICER_ID, ENTITY_ID, ENTITY_TYPE,
                 ACTION, DETAILS, RELEASE_REQUEST_ID)
                VALUES
                (14105, 2103, 13101,
                 'COLLATERAL_RELEASE_REQUEST',
                 'COLLATERAL_RELEASED',
                 'Vehicle collateral released',
                 13101)
                """
            );

            System.out.println(
                "Clear Close full demo seeding completed!"
            );
        };
    }

    // ========================================
    // HELPER: SKIP EXISTING PRIMARY KEYS
    // ========================================

    private void insert(
        JdbcTemplate jdbc,
        String table,
        String idColumn,
        int id,
        String sql,
        Object... values
    ) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table +
            " WHERE " + idColumn + " = ?",
            Integer.class,
            id
        );

        if (count != null && count > 0) {
            System.out.println(
                table + " ID " + id + " already exists. Skipping."
            );
            return;
        }

        jdbc.update(sql, values);

        System.out.println(
            table + " ID " + id + " inserted."
        );
    }
}
