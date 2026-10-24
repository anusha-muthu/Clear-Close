-- Run manually with F5 in Oracle SQL Developer as the schema owner, before
-- starting the updated Exit Service. Oracle DDL commits implicitly.
-- Additive and rerunnable. Does not infer repayment history, change balances,
-- backfill loan classifications, or alter any saved quote/payment amounts.
DECLARE
    PROCEDURE add_column(table_name_in VARCHAR2, column_name_in VARCHAR2, definition_in VARCHAR2) IS
        column_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO column_count FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = table_name_in AND COLUMN_NAME = column_name_in;
        IF column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || table_name_in || ' ADD (' || column_name_in || ' ' || definition_in || ')';
        END IF;
    END;
BEGIN
    -- Trusted loan-servicing inputs, never supplied by a borrower quote request.
    -- Paid-through means interest already settled through this calendar date;
    -- outstanding principal must be unchanged over the subsequent accrual period.
    add_column('LOAN_ACCOUNT', 'INTEREST_PAID_THROUGH', 'DATE');
    add_column('LOAN_ACCOUNT', 'INTEREST_RATE_TYPE',
        'VARCHAR2(10) CHECK (INTEREST_RATE_TYPE IN (''FIXED'', ''FLOATING''))');
    add_column('LOAN_ACCOUNT', 'LOAN_PURPOSE',
        'VARCHAR2(20) CHECK (LOAN_PURPOSE IN (''BUSINESS'', ''NON_BUSINESS''))');
    -- Y only after lender/category/contract eligibility review; N prohibited/waived.
    -- Null is unknown, never automatic permission. Protected categories override Y.
    add_column('LOAN_ACCOUNT', 'PREPAYMENT_CHARGE_ALLOWED',
        'CHAR(1) CHECK (PREPAYMENT_CHARGE_ALLOWED IN (''Y'', ''N''))');
    -- Percentage on taxable foreclosure fees only; no global 18% assumption.
    add_column('LOAN_PRODUCT_RULE', 'CLOSURE_TAX_RATE',
        'NUMBER(7,4) CHECK (CLOSURE_TAX_RATE BETWEEN 0 AND 100)');

    -- Immutable explanation captured with new pre-closure quotes.
    -- Old quotes and cooling-off quotes retain NULL calculation metadata.
    add_column('SETTLEMENT_QUOTE', 'CALCULATION_VERSION', 'NUMBER(4)');
    add_column('SETTLEMENT_QUOTE', 'INTEREST_PAID_THROUGH', 'DATE');
    add_column('SETTLEMENT_QUOTE', 'INTEREST_AS_OF_DATE', 'DATE');
    add_column('SETTLEMENT_QUOTE', 'ACCRUED_INTEREST_DAYS', 'NUMBER(10)');
    add_column('SETTLEMENT_QUOTE', 'QUOTED_ANNUAL_RATE', 'NUMBER(7,4)');
    add_column('SETTLEMENT_QUOTE', 'APPLIED_CHARGE_TYPE', 'VARCHAR2(20)');
    add_column('SETTLEMENT_QUOTE', 'APPLIED_CHARGE_VALUE', 'NUMBER(12,2)');
    add_column('SETTLEMENT_QUOTE', 'APPLIED_TAX_RATE', 'NUMBER(7,4)');
    add_column('SETTLEMENT_QUOTE', 'CHARGE_POLICY_REASON', 'VARCHAR2(500)');
END;
/
