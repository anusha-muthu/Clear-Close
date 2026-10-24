package com.clearclose.noc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recovers missed closures after downtime without relying on browser activity. */
@Component
public class AutomaticNocIssuance {
    private static final Logger log = LoggerFactory.getLogger(AutomaticNocIssuance.class);
    private final JdbcTemplate jdbc;
    private final NocService service;

    public AutomaticNocIssuance(JdbcTemplate jdbc, NocService service) {
        this.jdbc = jdbc;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${noc.automatic-issuance.delay-ms:5000}",
               initialDelayString = "${noc.automatic-issuance.initial-delay-ms:5000}")
    public void issueClosedLoans() {
        var loanIds = jdbc.queryForList("""
            SELECT l.LOAN_ID FROM LOAN_ACCOUNT l
            JOIN BORROWERS b ON b.USER_ID = l.BORROWER_ID
            WHERE UPPER(l.LOAN_STATUS) = 'CLOSED'
              AND l.OUTSTANDING_AMOUNT = 0 AND l.CLOSURE_DATE IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM NOC n WHERE n.LOAN_ID = l.LOAN_ID)
            ORDER BY l.CLOSURE_DATE, l.LOAN_ID
            FETCH FIRST 100 ROWS ONLY
            """, Long.class);
        for (Long loanId : loanIds) {
            try {
                var result = service.generateAutomatically(loanId);
                if (!"AVAILABLE".equals(result.status())) {
                    log.warn("Automatic NOC generation failed for loan {}; officer retry required", loanId);
                }
            } catch (Exception ex) {
                // One failed loan must not stop issuance for the rest of the batch.
                log.error("Unable to issue automatic NOC for loan {}", loanId, ex);
            }
        }
    }
}
