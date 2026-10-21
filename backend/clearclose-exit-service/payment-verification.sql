-- READ-ONLY manual checks; no schema changes or payment updates.
-- Replace 2 with the quote ID you actually tested.
DEFINE quote_id = 2;

-- Compare the live schema and constraints with backend/db/consolidated.db.
SELECT column_name, data_type, nullable, data_default
FROM user_tab_columns WHERE table_name = 'PAYMENT' ORDER BY column_id;
SELECT constraint_name, constraint_type, search_condition
FROM user_constraints WHERE table_name IN ('PAYMENT', 'AUDIT_LOG');

SELECT payment_id, quote_id, razorpay_order_id, razorpay_payment_id, amount,
       payment_status, payment_date, method, confirmed_at, created_at, updated_at
FROM payment WHERE quote_id = &quote_id ORDER BY payment_id;

-- Expect exactly one SUCCESS after verified capture; zero before capture.
SELECT quote_id, COUNT(*) AS successful_payments
FROM payment WHERE quote_id = &quote_id AND payment_status = 'SUCCESS'
GROUP BY quote_id;

-- Expect one PAYMENT_INITIATED and one PAYMENT_VERIFIED for a successful payment.
SELECT log_id, user_id, entity_id, entity_type, action, action_time, payment_id, quote_id
FROM audit_log WHERE quote_id = &quote_id AND entity_type = 'PAYMENT'
ORDER BY log_id;

-- After payment: loan ACTIVE, request APPROVED, quote ACCEPTED.
SELECT q.quote_id, q.quote_status, q.total_amount, q.valid_until, q.accepted_at,
       l.loan_id, l.borrower_id, l.loan_reference, l.loan_status,
       c.exit_request_id, c.status AS request_status
FROM settlement_quote q
JOIN loan_account l ON l.loan_id = q.loan_id
JOIN cooling_off_request c ON c.exit_request_id = q.exit_request_id
WHERE q.quote_id = &quote_id;

-- Both queries should return no rows for this integration.
SELECT quote_id FROM payment WHERE payment_status = 'SUCCESS'
GROUP BY quote_id HAVING COUNT(*) > 1;
SELECT razorpay_order_id FROM payment WHERE razorpay_order_id IS NOT NULL
GROUP BY razorpay_order_id HAVING COUNT(*) > 1;
