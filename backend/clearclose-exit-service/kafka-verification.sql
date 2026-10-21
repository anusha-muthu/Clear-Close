-- Read-only Oracle checks and recovery payloads. Execute manually.
-- Final workflow is now active: see final-completion-verification.sql for CLOSED,
-- zero outstanding, COMPLETED and completion-event delivery checks.
-- Replace this example with the payment you just verified in Razorpay Test Mode.
DEFINE payment_id = 8001;

SELECT p.payment_id, p.payment_status, p.amount, p.confirmed_at,
       q.quote_id, q.quote_status, l.loan_id, l.loan_status,
       c.exit_request_id, c.status AS request_status
FROM payment p
JOIN settlement_quote q ON q.quote_id = p.quote_id
JOIN loan_account l ON l.loan_id = q.loan_id
JOIN cooling_off_request c ON c.exit_request_id = q.exit_request_id
WHERE p.payment_id = &payment_id;
-- Expect SUCCESS, PAID, ACTIVE, APPROVED once Kafka confirmation is processed.
-- During Kafka failure/lag: SUCCESS, ACCEPTED, ACTIVE, APPROVED is expected.

SELECT log_id, action, details, action_time, payment_id, quote_id, exit_request_id
FROM audit_log
WHERE payment_id = &payment_id
  AND action IN ('PAYMENT_VERIFIED', 'PAYMENT_EVENT_READY', 'PAYMENT_EVENT_CONFIRMED')
ORDER BY log_id;
-- Exactly one of each for a newly verified payment whose event was processed.

SELECT payment_id, action, COUNT(*) AS duplicate_count
FROM audit_log
WHERE action IN ('PAYMENT_EVENT_READY', 'PAYMENT_EVENT_CONFIRMED')
GROUP BY payment_id, action HAVING COUNT(*) > 1;
-- Should return no rows for payments created by this integration.

-- Find prepared events with no consumer confirmation. These may simply be in
-- Kafka backlog; they are not automatically proof of a publication failure.
SELECT a.payment_id, a.quote_id, a.details, a.action_time
FROM audit_log a
JOIN payment p ON p.payment_id = a.payment_id
WHERE a.action = 'PAYMENT_EVENT_READY' AND p.payment_status = 'SUCCESS'
  AND NOT EXISTS (
      SELECT 1 FROM audit_log done
      WHERE done.payment_id = a.payment_id AND done.action = 'PAYMENT_EVENT_CONFIRMED'
  )
ORDER BY a.log_id;

-- Produce one replay line from the ORIGINAL stored event identity and verified
-- payment values. Copy just the result into payment-event-replay.jsonl locally.
-- The prefix before | is the required quoteId message key. Use this same line
-- for duplicate-delivery testing; do not invent another eventId or occurredAt.
SELECT TO_CHAR(q.quote_id) || '|' || JSON_OBJECT(
    'eventId' VALUE REGEXP_SUBSTR(a.details, 'eventId=([^;]+)', 1, 1, NULL, 1),
    'eventType' VALUE 'PAYMENT_SUCCEEDED',
    'eventVersion' VALUE 1,
    'paymentId' VALUE p.payment_id,
    'quoteId' VALUE p.quote_id,
    'loanId' VALUE q.loan_id,
    'exitRequestId' VALUE q.exit_request_id,
    'amount' VALUE p.amount,
    'currency' VALUE 'INR',
    'occurredAt' VALUE REGEXP_SUBSTR(a.details, 'occurredAt=(.+)$', 1, 1, NULL, 1)
    RETURNING CLOB
) AS replay_line
FROM payment p
JOIN settlement_quote q ON q.quote_id = p.quote_id
JOIN audit_log a ON a.payment_id = p.payment_id AND a.action = 'PAYMENT_EVENT_READY'
WHERE p.payment_id = &payment_id AND p.payment_status = 'SUCCESS';

-- Payments verified before this integration have no PAYMENT_EVENT_READY row.
-- Re-verifying those payments deliberately does not generate another business
-- event. Do not invent producer audit entries to make a message pass validation.
-- Use a fresh Test Mode payment or plan a separate reviewed legacy backfill.
