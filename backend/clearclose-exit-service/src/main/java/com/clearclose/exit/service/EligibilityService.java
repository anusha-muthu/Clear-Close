
package com.clearclose.exit.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.exit.dto.EligibilityDto;

@Service
public class EligibilityService {

    private final JdbcTemplate jdbc;

    public EligibilityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Internal record for database results
    private record LoanInfo(
        Long loanId,
        String status,
        LocalDateTime disbursementDate,
        int coolingOffDays,
        LocalDateTime currentTime
    ) {}

    // ---------------------------------------
    // CHECK COOLING-OFF ELIGIBILITY
    // ---------------------------------------

    public EligibilityDto checkEligibility(
        Long loanId,
        Long borrowerId
    ) {

        // 1. Retrieve loan and product rules
        String sql = """
            SELECT
                l.LOAN_ID,
                l.LOAN_STATUS,
                l.DISBURSEMENT_DATE,
                r.COOLING_OFF_DAYS,
                SYSDATE AS CURRENT_DB_TIME
            FROM LOAN_ACCOUNT l
            JOIN LOAN_PRODUCT_RULE r
                ON l.RULE_ID = r.RULE_ID
            WHERE l.LOAN_ID = ?
              AND l.BORROWER_ID = ?
            """;

        List<LoanInfo> loans = jdbc.query(
            sql,
            (rs, rowNum) -> {

                Timestamp disbursement =
                    rs.getTimestamp("DISBURSEMENT_DATE");

                Timestamp currentTime =
                    rs.getTimestamp("CURRENT_DB_TIME");

                return new LoanInfo(
                    rs.getLong("LOAN_ID"),
                    rs.getString("LOAN_STATUS"),
                    disbursement == null
                        ? null
                        : disbursement.toLocalDateTime(),
                    rs.getInt("COOLING_OFF_DAYS"),
                    currentTime.toLocalDateTime()
                );
            },
            loanId,
            borrowerId
        );

        // 2. Loan must exist and belong to borrower
        if (loans.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Loan not found"
            );
        }

        LoanInfo loan = loans.get(0);

        // 3. Calculate cooling-off expiry
        LocalDateTime expiry = null;

        if (loan.disbursementDate() != null) {
            expiry = loan.disbursementDate()
                .plusDays(loan.coolingOffDays());
        }

        // 4. Check loan status
        if (!"ACTIVE".equals(loan.status())) {
            return result(
                loan, expiry, false,
                "Only active loans can use cooling-off exit"
            );
        }

        // 5. Check disbursement date
        if (loan.disbursementDate() == null) {
            return result(
                loan, expiry, false,
                "Loan disbursement date is missing"
            );
        }

        // 6. Check cooling-off duration
        if (loan.coolingOffDays() <= 0) {
            return result(
                loan, expiry, false,
                "Cooling-off exit is not available for this loan"
            );
        }

        // 7. Check future disbursement date
        if (loan.currentTime().isBefore(
            loan.disbursementDate()
        )) {
            return result(
                loan, expiry, false,
                "Loan has not been disbursed yet"
            );
        }

        // 8. Check expiry
        if (!loan.currentTime().isBefore(expiry)) {
            return result(
                loan, expiry, false,
                "Cooling-off period has expired"
            );
        }

        // 9. Check for an existing request
        String requestSql = """
            SELECT COUNT(*)
            FROM COOLING_OFF_REQUEST
            WHERE LOAN_ID = ?
              AND STATUS IN (
                  'PENDING',
                  'UNDER_REVIEW',
                  'MORE_INFO_REQUIRED',
                  'APPROVED',
                  'COMPLETED'
              )
            """;

        Integer existingRequests = jdbc.queryForObject(
            requestSql,
            Integer.class,
            loanId
        );

        if (existingRequests != null
                && existingRequests > 0) {

            return result(
                loan, expiry, false,
                "A cooling-off exit request already exists"
            );
        }

        // 10. All checks passed
        return result(
            loan, expiry, true,
            "Loan is eligible for cooling-off exit"
        );
    }

    // ---------------------------------------
    // CREATE RESPONSE DTO
    // ---------------------------------------

    private EligibilityDto result(
        LoanInfo loan,
        LocalDateTime expiry,
        boolean eligible,
        String reason
    ) {
        return new EligibilityDto(
            loan.loanId(),
            eligible,
            loan.status(),
            loan.coolingOffDays(),
            loan.disbursementDate(),
            expiry,
            reason
        );
    }
}
