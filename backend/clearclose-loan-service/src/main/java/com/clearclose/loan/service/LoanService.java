
package com.clearclose.loan.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clearclose.loan.dto.LoanDto;

@Service
public class LoanService {

    private final JdbcTemplate jdbc;

    public LoanService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------
    // SQL QUERY
    // ---------------------------------------

    private static final String LOAN_QUERY = """
        SELECT
            l.LOAN_ID,
            l.LOAN_REFERENCE,
            r.PRODUCT_TYPE,
            l.PRINCIPAL_AMOUNT,
            l.OUTSTANDING_AMOUNT,
            l.INTEREST_RATE,
            l.TENURE_MONTHS,
            l.DISBURSEMENT_DATE,
            l.MATURITY_DATE,
            r.COOLING_OFF_DAYS,
            l.DISBURSEMENT_DATE + r.COOLING_OFF_DAYS
                AS COOLING_OFF_EXPIRY,
            l.LOAN_STATUS,
            l.CLOSURE_DATE
        FROM LOAN_ACCOUNT l
        JOIN LOAN_PRODUCT_RULE r
            ON l.RULE_ID = r.RULE_ID
        WHERE l.BORROWER_ID = ?
        """;

    // ---------------------------------------
    // CONVERT SQL ROW INTO LOAN DTO
    // ---------------------------------------

    private final RowMapper<LoanDto> loanMapper =
        new RowMapper<>() {

            @Override
            public LoanDto mapRow(
                ResultSet rs,
                int rowNum
            ) throws SQLException {

                return new LoanDto(

                    rs.getLong("LOAN_ID"),

                    rs.getString("LOAN_REFERENCE"),

                    rs.getString("PRODUCT_TYPE"),

                    rs.getBigDecimal("PRINCIPAL_AMOUNT"),

                    rs.getBigDecimal("OUTSTANDING_AMOUNT"),

                    rs.getBigDecimal("INTEREST_RATE"),

                    rs.getInt("TENURE_MONTHS"),

                    toDateTime(rs, "DISBURSEMENT_DATE"),

                    toDateTime(rs, "MATURITY_DATE"),

                    rs.getInt("COOLING_OFF_DAYS"),

                    toDateTime(rs, "COOLING_OFF_EXPIRY"),

                    rs.getString("LOAN_STATUS"),

                    toDateTime(rs, "CLOSURE_DATE")
                );
            }
        };

    // ---------------------------------------
    // CONVERT DATABASE DATE/TIMESTAMP
    // ---------------------------------------

    private LocalDateTime toDateTime(
        ResultSet rs,
        String column
    ) throws SQLException {

        Timestamp timestamp = rs.getTimestamp(column);

        return timestamp == null
            ? null
            : timestamp.toLocalDateTime();
    }

    // ---------------------------------------
    // GET ALL LOANS OF LOGGED-IN BORROWER
    // ---------------------------------------

    public List<LoanDto> getMyLoans(Long borrowerId) {

        String sql = LOAN_QUERY + " ORDER BY l.LOAN_ID";

        return jdbc.query(
            sql,
            loanMapper,
            borrowerId
        );
    }

    // ---------------------------------------
    // GET ONE LOAN BY ID
    // ---------------------------------------

    public LoanDto getLoanById(
        Long borrowerId,
        Long loanId
    ) {

        String sql = LOAN_QUERY + """
            AND l.LOAN_ID = ?
            """;

        List<LoanDto> loans = jdbc.query(
            sql,
            loanMapper,
            borrowerId,
            loanId
        );

        if (loans.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Loan not found"
            );
        }

        return loans.get(0);
    }
}
