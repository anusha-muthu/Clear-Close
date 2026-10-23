-- TEAM DATABASE UPDATE: cooling-off processing fee
-- Run this entire file in SQL Developer with F5, connected as the schema owner.
-- Adds the fee column/check constraint if missing, then sets ALL product rules
-- to INR 500.00, including rules that already have a different fee.
-- Existing loan accounts and settlement quotes are not modified.
-- New quotes read this fee through the loan's RULE_ID and save CHARGES_AMOUNT.
-- Demo policy assumes upfront disclosure and that this fee was not collected.
-- Oracle DDL commits implicitly. Do not rerun the full schema or reset the seeder.

DECLARE
    column_count NUMBER;
    constraint_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO column_count
    FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'LOAN_PRODUCT_RULE'
      AND COLUMN_NAME = 'COOLING_OFF_PROCESSING_FEE';

    IF column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE LOAN_PRODUCT_RULE ADD (
            COOLING_OFF_PROCESSING_FEE NUMBER(12,2) DEFAULT 0 NOT NULL)';
    END IF;

    SELECT COUNT(*) INTO constraint_count
    FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'LOAN_PRODUCT_RULE'
      AND CONSTRAINT_NAME = 'CHK_CO_PROCESSING_FEE';

    IF constraint_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE LOAN_PRODUCT_RULE ADD CONSTRAINT
            CHK_CO_PROCESSING_FEE CHECK (COOLING_OFF_PROCESSING_FEE >= 0)';
    END IF;
END;
/

UPDATE LOAN_PRODUCT_RULE
SET COOLING_OFF_PROCESSING_FEE = 500;

COMMIT;

SELECT RULE_ID, PRODUCT_TYPE, COOLING_OFF_PROCESSING_FEE
FROM LOAN_PRODUCT_RULE
ORDER BY RULE_ID;
