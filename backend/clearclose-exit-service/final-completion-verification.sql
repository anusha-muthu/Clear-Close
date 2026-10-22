-- READ-ONLY checks. Replace with the payment from a real Razorpay Test Mode flow.
DEFINE payment_id = 2;

SELECT p.payment_id, p.payment_status, p.amount, q.quote_id, q.quote_status,
       c.exit_request_id, c.status AS request_status, c.officer_id, c.decided_at,
       l.loan_id, l.loan_status, l.outstanding_amount, l.closure_date
FROM payment p
JOIN settlement_quote q ON q.quote_id = p.quote_id
JOIN cooling_off_request c ON c.exit_request_id = q.exit_request_id AND c.loan_id = q.loan_id
JOIN loan_account l ON l.loan_id = q.loan_id
WHERE p.payment_id = &payment_id;
-- Final: SUCCESS / PAID / COMPLETED / CLOSED / 0 / non-null CLOSURE_DATE.

SELECT log_id, entity_type, entity_id, action, details, payment_id, quote_id, exit_request_id, action_time
FROM audit_log
WHERE payment_id = &payment_id
ORDER BY log_id;
-- After final completion expect exactly one of each:
-- PAYMENT_EVENT_CONFIRMED, COOLING_OFF_LOAN_CLOSED, COOLING_OFF_EXIT_COMPLETED,
-- LOAN_EXIT_EVENT_READY, LOAN_EXIT_EVENT_PUBLISHED.
-- The last marker appears only after Kafka acknowledges the send.

SELECT payment_id, action, COUNT(*) AS records
FROM audit_log
WHERE action IN ('COOLING_OFF_LOAN_CLOSED', 'COOLING_OFF_EXIT_COMPLETED',
                 'LOAN_EXIT_EVENT_READY', 'LOAN_EXIT_EVENT_PUBLISHED')
GROUP BY payment_id, action HAVING COUNT(*) > 1;
-- No rows expected for workflows handled by this implementation.

-- Durable unfinished work, including a loan closed before Exit Service could finish.
SELECT p.payment_id, q.quote_id, c.exit_request_id, c.status, l.loan_id, l.loan_status
FROM payment p
JOIN settlement_quote q ON q.quote_id = p.quote_id
JOIN cooling_off_request c ON c.exit_request_id = q.exit_request_id
JOIN loan_account l ON l.loan_id = q.loan_id
WHERE p.payment_status = 'SUCCESS' AND q.quote_status = 'PAID' AND c.status = 'APPROVED'
  AND EXISTS (SELECT 1 FROM audit_log a WHERE a.payment_id = p.payment_id
              AND a.action = 'PAYMENT_EVENT_CONFIRMED');

-- Completion events awaiting publication/republication (scheduler retries these).
SELECT a.payment_id, a.quote_id, a.exit_request_id, a.details
FROM audit_log a
WHERE a.action = 'LOAN_EXIT_EVENT_READY'
  AND NOT EXISTS (SELECT 1 FROM audit_log sent WHERE sent.payment_id = a.payment_id
                  AND sent.action = 'LOAN_EXIT_EVENT_PUBLISHED');
