# Pre-closure settlement: manual setup and verification

## Additional information without schema changes

Assigned closure officers can request more information while a pre-closure is
under review. The borrower replies through the same request's Additional
Information panel; approval/rejection is available after the reply.

The existing LOAN_CLOSURE_REQUEST status constraint does not include
MORE_INFO_REQUIRED. Its stored status therefore remains UNDER_REVIEW. The API
derives the displayed MORE_INFO_REQUIRED status only while the latest conversation
audit action is PRE_CLOSURE_MORE_INFO_REQUIRED. A PRE_CLOSURE_INFO_SUBMITTED reply
restores the displayed UNDER_REVIEW status. Questions and replies use existing
AUDIT_LOG.DETAILS and CLOSURE_REQUEST_ID, with no tables, columns or constraint
changes. History returns only these conversation actions, not financial audits.

Test manually: assign a fresh request, ask a question, verify approval is blocked,
reply as its borrower, refresh the officer conversation, then approve and continue
quotation/payment. Other borrowers/officers must not access or mutate it.
Accepted settlements and initiated payments cannot be reopened for information.
Authored tests: PreClosureInformationTest. No builds/tests or SQL were executed.

Implementation only. No migration, seed SQL, build, test, service restart, or
payment was run for this change.

## Setup

1. Stop services and run the current `../db/consolidated.db` manually with SQL
   Developer F5 if the settlement columns have not been added. It now creates
   missing tables and adds missing settlement columns on reruns. Oracle DDL
   commits implicitly. Existing balances, payments, and quotes are not rewritten.
2. For real existing loans, populate `LOAN_ACCOUNT.INTEREST_PAID_THROUGH` from
   verified servicing records. This is the date through which interest has already
   been paid, not an estimated date derived from tenure or months completed.
   For a genuinely new unpaid loan it can be the actual disbursement date.
3. Record `INTEREST_RATE_TYPE` (`FIXED`/`FLOATING`), `LOAN_PURPOSE`
   (`BUSINESS`/`NON_BUSINESS`), and `PREPAYMENT_CHARGE_ALLOWED` (`Y`/`N`). Only
   trusted servicing/admin processes may set these. Y means eligibility was
   reviewed under the applicable lender category, regulatory requirements and
   disclosed contract terms; it is not inferred just from product type. Null means
   unknown and blocks applying a fee. N means prohibited or waived. Known
   floating-rate non-business loans to these individual borrowers override Y and
   always receive zero foreclosure fees.
4. Configure the product's `CLOSURE_CHARGE_TYPE`, `CLOSURE_CHARGE_VALUE`, and
   `CLOSURE_TAX_RATE` (a percentage). Pre-closure uses P + I + C + T only.
   `COOLING_OFF_PROCESSING_FEE` belongs exclusively to cooling-off and is not
   queried or applied during pre-closure. Do not zero that setting to change a
   pre-closure quotation: it still controls new cooling-off quotations.
   Tax applies only to permitted foreclosure charges and is required only when
   the foreclosure charge is positive.
   Rates of 12%, 2%, and 18% are example data, not application defaults.
5. Use Tara loan 4106 / rule 3103 from the full Java seeder. It has 50000
   original principal, 25000 outstanding, 20-month tenure, fifteen months
   completed, 12% annual interest, ten unpaid interest days, a permitted 2%
   foreclosure fee, and 18% tax on that fee, plus collateral 12106.
6. Build and restart Exit Service yourself; refresh the frontend. Run the authored
   `PreClosureSettlementCalculatorTest` and existing preclosure tests if desired.

## Expected frontend flow

Request pre-closure, assign/approve as the closure officer, then create a quotation
as the borrower. On the same database calendar day as the demo seed, expect:

| Component | Amount |
| --- | ---: |
| Outstanding principal | 25000.00 |
| Interest: 25000 × 12 × 10 / 36500 | 82.19 |
| Permitted foreclosure fee: 2% of 25000 | 500.00 |
| Tax: 18% of 500 | 90.00 |
| Total | 25672.19 |

Both officer and borrower screens show the paid-through date, settlement date,
calendar days, annual rate, Actual/365 basis, applied fee, tax rate and fee reason.
Accept the quote, pay in test mode, and refresh until COMPLETED. The loan then has
zero current outstanding; the paid quote keeps its original principal and amounts.
The existing automatic NOC and collateral release flow follows loan closure.

For a separate fresh demo loan, use fee permission N, or FLOATING/NON_BUSINESS,
and expect 25082.19 with zero fees and zero tax. Paid-through = today produces zero
additional interest. Do not change terms of a quote already accepted or paid.

## Calculation limits and saved quotes

P is the stored outstanding principal; it is not original principal minus all EMIs
and is not inferred from months elapsed. Interest is Actual/365 simple interest on
that P from the paid-through date to the database calendar date. No future months
of interest are included. The simplified model assumes the outstanding principal
was constant during that interval and all prior interest was settled. For balance
changes within the interval, arrears, credits, or other dues, a servicing ledger
and additional calculation rules are required; this change does not simulate EMI
payments or implement a complete regulatory classification engine.

New quotes use the original version 1 P + I + C + T calculation, with foreclosure
charges only in CHARGES_AMOUNT. The temporary combined-fee seeder example is no
longer created. Any already saved version 2 quote is identified in the frontend
as an earlier fee policy. Available version 2 quotes must be regenerated before
acceptance; accepted or paid quotes retain their original amounts.
Existing accepted and paid quotes remain unchanged; older quotes have no calculation metadata and
continue to display their saved amounts. Quotes remain valid for at most 15
minutes, capped at database midnight to avoid opening a new checkout on a later
interest date. Already captured payments can still be reconciled under the
existing payment verification flow; refreshing does not reprice them.

Missing verified paid-through data returns a clear conflict rather than silently
charging zero interest or charging the whole loan's lifetime again. Missing fee
permission/classification or tax configuration likewise blocks unsafe new quotes.
