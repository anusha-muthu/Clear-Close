INSERT INTO BORROWERS
(user_id, name, username, password_hash, email, phone,pan_card, location, account_status)
VALUES
(101, 'Rohan Sharma', 'rohan.borrower', 'REPLACE_WITH_BCRYPT_HASH',
 'rohan.demo@example.com', '9000000101','ABC12007', 'Mumbai', 'ACTIVE');

INSERT INTO BORROWERS
(user_id, name, username, password_hash, email, phone,pan_card, location, account_status)
VALUES
(102, 'vinoth', 'vinoth.borrower', 'REPLACE_WITH_BCRYPT_HASH',
 'vinoth.demo@example.com', '1289999940','AGY12679', 'CHENNAI', 'ACTIVE');

INSERT INTO BORROWERS
(user_id, name, username, password_hash, email, phone,pan_card, location, account_status)
VALUES
(201, 'Amit Patel', 'amit.officer', 'REPLACE_WITH_BCRYPT_HASH',
 'amit.officer@example.com', '9000000201','HRW9849', 'Mumbai', 'ACTIVE');

INSERT INTO BORROWERS
(user_id, name, username, password_hash, email, phone,pan_card, location, account_status)
VALUES
(301, 'System Admin', 'clearclose.admin', 'REPLACE_WITH_BCRYPT_HASH',
 'admin.demo@example.com', '9000000301','INk19479', 'Mumbai', 'ACTIVE');




INSERT INTO LOAN_PRODUCT_RULE
(rule_id, product_type, cooling_off_days, early_closure_allowed,
 closure_charge_type, closure_charge_value, noc_requires_full_settlement,
 collateral_release_requires_closure, effective_from, rule_status)
VALUES
(1, 'CAR_LOAN', 5, 'Y', 'PERCENTAGE', 2, 'Y', 'Y', DATE '2024-01-01', 'ACTIVE');

INSERT INTO LOAN_PRODUCT_RULE
(rule_id, product_type, cooling_off_days, early_closure_allowed,
 closure_charge_type, closure_charge_value, noc_requires_full_settlement,
 collateral_release_requires_closure, effective_from, rule_status)
VALUES
(2, 'PERSONAL_LOAN', 5, 'Y', 'PERCENTAGE', 2, 'Y', 'N', DATE '2024-01-01', 'ACTIVE');



INSERT INTO LOAN_ACCOUNT(
    borrower_id, rule_id, loan_reference,
    principal_amount, outstanding_amount,
    interest_rate, tenure_months,
    disbursement_date, maturity_date,
    loan_status, closure_date
)VALUES
(
    102, 1, 'PP-PER-002001',
    1500000, 150000,
    8.5, 80,
    DATE '2024-03-01', DATE '2030-03-01',
    'ACTIVE', DATE '2030-03-01'
);


INSERT INTO LOAN_ACCOUNT(
    borrower_id, rule_id, loan_reference,
    principal_amount, outstanding_amount,
    interest_rate, tenure_months,
    disbursement_date, maturity_date,
    loan_status, closure_date
)VALUES
(
    101, 1, 'CC-CAR-001001',
    1000000, 850000,
    8, 60,
    DATE '2024-01-01', DATE '2029-01-01',
    'ACTIVE', DATE '2029-01-01'
);

INSERT INTO GRIEVANCES(LOAN_ACCOUNT_ID,BORROWER_ID,CATEGORY,DESCRIPTION,RESOLUTION_REMARKS,ASSIGNED_TO)
VALUES (1001,501,'LOAN CLOSURE DELAY','My loan closure request has been delayed','Missing documents',101);

INSERT INTO GRIEVANCES_HISTORY
(history_id, grievance_id, updated_by, old_status, new_status, remarks, updated_at)
VALUES
(11001, 4001, 201, 'OPEN', 'IN_PROGRESS', 'Officer is reviewing the complaint',
 TIMESTAMP '2024-01-03 14:20:00');


INSERT INTO COLLATERAL (
    LOAN_ACCOUNT_ID,
    COLLATERAL_TYPE,
    DESCRIPTION,
    CUSTODIAN,
    RELEASE_STATUS
)
VALUES (
    7,
    'VEHICLE',
    'Original vehicle registration documents',
    'Loan Operations',
    'ELIGIBLE'
);


INSERT INTO COLLATERAL (
    LOAN_ACCOUNT_ID,
    COLLATERAL_TYPE,
    DESCRIPTION,
    CUSTODIAN,
    RELEASE_STATUS
)
VALUES (
    8,
    'PERSONAL',
    'ORIGINAL HOME documents',
    'Loan Operations',
    'ELIGIBLE'
);


INSERT INTO COLLATERAL_RELEASE_REQUESTS
(collateral_id, loan_account_id,requested_by,assigned_to ,status, reason, 
 expected_release_date,released_at, release_reference)
VALUES
(1,7,101,201,'PENDING','Missing documents', DATE '2027-12-10', 
 DATE '2027-12-05', 'REL-2027-0001');


COMMIT;
