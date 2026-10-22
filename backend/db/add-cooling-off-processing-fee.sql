-- Run manually in the schema owning LOAN_PRODUCT_RULE if not already migrated.
-- Oracle DDL commits implicitly. No loan/quote/payment rows are changed.
-- Existing fee values are preserved when the column already exists.
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
        -- Initial demo policy only; rerunning this script does not reprice rules.
        EXECUTE IMMEDIATE 'UPDATE LOAN_PRODUCT_RULE SET COOLING_OFF_PROCESSING_FEE = 500';
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
COMMIT;

SELECT RULE_ID, PRODUCT_TYPE, COOLING_OFF_PROCESSING_FEE
FROM LOAN_PRODUCT_RULE ORDER BY RULE_ID;
