
package com.clearclose.auth;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ClearCloseFullSeeder {

    @Bean
    @ConditionalOnProperty(
        name = "app.demo.seed-all",
        havingValue = "true"
    )
    public CommandLineRunner seedAll(
        JdbcTemplate jdbc,
        PasswordEncoder encoder,
        PlatformTransactionManager transactionManager
    ) {
        return args -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            requireUpdatedSchema(jdbc);

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
                 "LOAN_CLOSURE_OFFICER", "NTRPR4826L", "Mumbai"},

                {2102, "Kavya Jain", "kavya_officer26",
                 "Kavya@12345", "kavya26@bank.example",
                 "GRIEVANCE_OFFICER", "KVYJN7315D", "Pune"},

                {2103, "Om Prakash", "om_officer26",
                 "Om@12345", "om26@bank.example",
                 "COLLATERAL_OFFICER", "OMPRA2954N", "Mumbai"}
            };

            for (Object[] o : officers) {
                insert(
                    jdbc, "OFFICER", "OFFICER_ID",
                    (int) o[0],
                    """
                    INSERT INTO OFFICER
                    (OFFICER_ID, FULL_NAME, USERNAME,
                     PASSWORD_HASH, EMAIL, ROLE, PAN_CARD, LOCATION, ACCOUNT_STATUS)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    o[0], o[1], o[2],
                    encoder.encode((String) o[3]),
                    o[4], o[5], o[6], o[7]
                );

                // Repair earlier incomplete demo seeds without resetting the DB.
                // Match BOTH ID and username, and preserve any existing identity.
                // USER_ID stays NULL: these officers are not linked to a borrower.
                // Distinct synthetic PANs let self-review checks compare identity.
                jdbc.update("""
                    UPDATE OFFICER
                    SET PAN_CARD = COALESCE(PAN_CARD, ?), LOCATION = COALESCE(LOCATION, ?)
                    WHERE OFFICER_ID = ? AND USERNAME = ?
                      AND (PAN_CARD IS NULL OR LOCATION IS NULL)
                    """, o[6], o[7], o[0], o[2]);
            }

            // ========================================
            // 3. LOAN_PRODUCT_RULE
            // ========================================

            insert(jdbc, "LOAN_PRODUCT_RULE", "RULE_ID", 3101,
                """
                INSERT INTO LOAN_PRODUCT_RULE
                (RULE_ID, PRODUCT_TYPE, COOLING_OFF_DAYS,
                 CLOSURE_CHARGE_TYPE, EFFECTIVE_FROM, COOLING_OFF_PROCESSING_FEE, CLOSURE_TAX_RATE)
                VALUES
                (3101, 'PERSONAL_LOAN', 5,
                 'NONE', DATE '2026-01-01', 500, 18)
                """
            );

            insert(jdbc, "LOAN_PRODUCT_RULE", "RULE_ID", 3102,
                """
                INSERT INTO LOAN_PRODUCT_RULE
                (RULE_ID, PRODUCT_TYPE, COOLING_OFF_DAYS,
                 CLOSURE_CHARGE_TYPE, CLOSURE_CHARGE_VALUE,
                 EFFECTIVE_FROM, COOLING_OFF_PROCESSING_FEE, CLOSURE_TAX_RATE)
                VALUES
                (3102, 'CAR_LOAN', 5,
                 'PERCENTAGE', 2, DATE '2026-01-01', 500, 18)
                """
            );

            // Dedicated example rule: applying its fee still requires loan-level permission.
            insert(jdbc, "LOAN_PRODUCT_RULE", "RULE_ID", 3103,
                """
                INSERT INTO LOAN_PRODUCT_RULE
                (RULE_ID, PRODUCT_TYPE, COOLING_OFF_DAYS, EARLY_CLOSURE_ALLOWED,
                 CLOSURE_CHARGE_TYPE, CLOSURE_CHARGE_VALUE, CLOSURE_TAX_RATE, EFFECTIVE_FROM,
                 COOLING_OFF_PROCESSING_FEE)
                VALUES (3103, 'PERSONAL_LOAN', 5, 'Y', 'PERCENTAGE', 2, 18, DATE '2026-01-02', 500)
                """);

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
                 DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS,
                 INTEREST_PAID_THROUGH, INTEREST_RATE_TYPE, LOAN_PURPOSE, PREPAYMENT_CHARGE_ALLOWED)
                VALUES
                (4101, 1101, 3101, 'CC-PL-4101',
                 120000, 120000, 12, 24,
                 TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 24), 'ACTIVE',
                 TRUNC(SYSDATE), 'FLOATING', 'NON_BUSINESS', 'N')
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
                 LOAN_STATUS, CLOSURE_DATE,
                 INTEREST_PAID_THROUGH, INTEREST_RATE_TYPE, LOAN_PURPOSE, PREPAYMENT_CHARGE_ALLOWED)
                VALUES
                (4102, 1102, 3101, 'CC-PL-4102',
                 150000, 0, 12, 24,
                 DATE '2026-10-10', DATE '2028-10-10',
                 'CLOSED', DATE '2026-10-12',
                 DATE '2026-10-12', 'FLOATING', 'NON_BUSINESS', 'N')
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
                 LOAN_STATUS, CLOSURE_DATE,
                 INTEREST_PAID_THROUGH, INTEREST_RATE_TYPE, LOAN_PURPOSE, PREPAYMENT_CHARGE_ALLOWED)
                VALUES
                (4103, 1103, 3102, 'CC-CAR-4103',
                 900000, 0, 9, 60,
                 DATE '2024-03-01', DATE '2029-03-01',
                 'CLOSED', DATE '2026-10-15',
                 DATE '2026-10-15', 'FIXED', 'NON_BUSINESS', 'Y')
                """
            );

            // Aditya: Active personal loan
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4104,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS,
                 INTEREST_PAID_THROUGH, INTEREST_RATE_TYPE, LOAN_PURPOSE, PREPAYMENT_CHARGE_ALLOWED)
                VALUES
                (4104, 1104, 3101, 'CC-PL-4104',
                 250000, 180000, 11, 36,
                 ADD_MONTHS(TRUNC(SYSDATE), -12), ADD_MONTHS(TRUNC(SYSDATE), 24), 'ACTIVE',
                 TRUNC(SYSDATE) - 10, 'FLOATING', 'NON_BUSINESS', 'N')
                """
            );

            // Farah: Active car loan
            insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", 4105,
                """
                INSERT INTO LOAN_ACCOUNT
                (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                 PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT,
                 INTEREST_RATE, TENURE_MONTHS,
                 DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS,
                 INTEREST_PAID_THROUGH, INTEREST_RATE_TYPE, LOAN_PURPOSE, PREPAYMENT_CHARGE_ALLOWED)
                VALUES
                (4105, 1105, 3102, 'CC-CAR-4105',
                 650000, 430000, 8, 60,
                 ADD_MONTHS(TRUNC(SYSDATE), -12), ADD_MONTHS(TRUNC(SYSDATE), 48), 'ACTIVE',
                 TRUNC(SYSDATE) - 10, 'FIXED', 'NON_BUSINESS', 'Y')
                """
            );

            // Tara: three fresh pre-closure cases. No requests, quotes or payments
            // are pre-created: exercise approval, quotation, payment and NOC via UI.
            // Principal is unchanged since the stated interest paid-through date.
            for (int loanId : new int[] {4106, 4107, 4108}) {
                boolean floating = loanId == 4107;
                boolean sameDay = loanId == 4108;
                insert(jdbc, "LOAN_ACCOUNT", "LOAN_ID", loanId,
                    """
                    INSERT INTO LOAN_ACCOUNT
                    (LOAN_ID, BORROWER_ID, RULE_ID, LOAN_REFERENCE,
                     PRINCIPAL_AMOUNT, OUTSTANDING_AMOUNT, INTEREST_RATE, TENURE_MONTHS,
                     DISBURSEMENT_DATE, MATURITY_DATE, LOAN_STATUS,
                     INTEREST_PAID_THROUGH, INTEREST_RATE_TYPE, LOAN_PURPOSE, PREPAYMENT_CHARGE_ALLOWED)
                    VALUES (?, 1101, 3103, ?, 50000, 25000, 12, 20,
                        ADD_MONTHS(TRUNC(SYSDATE) - 10, -15), ADD_MONTHS(TRUNC(SYSDATE) - 10, 5), 'ACTIVE',
                        TRUNC(SYSDATE) - ?, ?, 'NON_BUSINESS', ?)
                    """, loanId, "CC-PC-" + loanId, sameDay ? 0 : 10,
                    floating ? "FLOATING" : "FIXED", floating || sameDay ? "N" : "Y");
            }

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
                 TRUNC(SYSDATE), TRUNC(SYSDATE) + 5,
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

            // Tara's pending cooling-off request has no quote until officer approval.

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

            // Do not insert fake PDF paths or GENERATION_PENDING placeholders.
            // Running NOC Service generates real demo PDFs for CLOSED loans.

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

            // One unreleased asset for every active test loan, including Tara's
            // cooling-off and the three new pre-closure cases.
            int[][] assets = {{12102, 4101}, {12103, 4104}, {12104, 4105},
                              {12106, 4106}, {12107, 4107}, {12108, 4108}};
            for (int[] asset : assets) {
                boolean vehicle = asset[1] == 4105;
                insert(jdbc, "COLLATERAL", "COLLATERAL_ID", asset[0],
                    """
                    INSERT INTO COLLATERAL
                    (COLLATERAL_ID, LOAN_ACCOUNT_ID, COLLATERAL_TYPE, DESCRIPTION,
                     FILE_NAME, STORAGE_PATH, CUSTODIAN, RELEASE_STATUS)
                    VALUES (?, ?, ?, ?,
                        ?, ?, 'Loan Operations', 'NOT_ELIGIBLE')
                    """, asset[0], asset[1], vehicle ? "VEHICLE" : "PROPERTY",
                    vehicle ? "Synthetic vehicle registration documents" : "Synthetic property documents for frontend testing",
                    "asset-" + asset[1] + ".pdf", "demo/collateral/asset-" + asset[1] + ".pdf");
            }

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
                "Clear Close full demo seed prepared; transaction will commit on successful return."
            );
            System.out.println("Tara: tara_demo26 / Tara@12345; cooling-off loan 4101.");
            System.out.println("Tara pre-closure: 4106 permitted fee, 4107 floating exemption, 4108 interest already paid through today.");
        });
    }

    // ========================================
    // HELPER: SKIP EXISTING PRIMARY KEYS
    // ========================================

    private void requireUpdatedSchema(JdbcTemplate jdbc) {
        requireColumns(jdbc, "OFFICER", "PAN_CARD", "LOCATION", "ACCOUNT_STATUS");
        requireColumns(jdbc, "LOAN_PRODUCT_RULE", "COOLING_OFF_PROCESSING_FEE", "CLOSURE_TAX_RATE");
        requireColumns(jdbc, "LOAN_ACCOUNT", "INTEREST_PAID_THROUGH", "INTEREST_RATE_TYPE",
            "LOAN_PURPOSE", "PREPAYMENT_CHARGE_ALLOWED");
        requireColumns(jdbc, "SETTLEMENT_QUOTE", "CALCULATION_VERSION", "INTEREST_PAID_THROUGH",
            "INTEREST_AS_OF_DATE", "ACCRUED_INTEREST_DAYS", "QUOTED_ANNUAL_RATE", "APPLIED_CHARGE_TYPE",
            "APPLIED_CHARGE_VALUE", "APPLIED_TAX_RATE", "CHARGE_POLICY_REASON");
    }

    private void requireColumns(JdbcTemplate jdbc, String table, String... required) {
        List<String> columns = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?", String.class, table);
        List<String> missing = List.of(required).stream().filter(column -> !columns.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Full demo seeder requires the updated consolidated.db schema. Missing "
                + table + " columns: " + String.join(", ", missing) + ". Run consolidated.db in the Auth Service database schema first.");
        }
    }

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
