# CLEAR CLOSE — NOC Service

Standalone Spring Boot microservice for No Objection Certificate (NOC) generation and management.

## Technologies

- Java 25
- Spring Boot
- Spring Data JPA
- Oracle Database
- OpenPDF
- Maven

## Features

- Check NOC eligibility after loan closure
- Generate PDF certificates
- Store certificate details in Oracle Database
- Download NOC PDFs
- Verify certificate numbers
- Reissue certificates
- Retry failed PDF generation
- Maintain NOC audit history

## Database

The service uses the following existing Oracle Database tables:

- `LOAN_ACCOUNT`
- `NOC`
- `NOC_AUDIT`

The required database schema must be created before starting the application.

For an existing Clear Close schema missing the new NOC columns or NOC_AUDIT,
stop NOC Service and run `../db/migrate-noc-service.sql` with SQL Developer's F5
in the owner schema. Do not rerun the full consolidated schema or reset seed data.
The migration skips existing columns/tables. If NOC_STATUS is newly added, old
records are marked FAILED because their PDFs/hashes have not been verified; an
authorized officer can retry them after loan eligibility checks. Existing statuses
are preserved. Oracle DDL commits implicitly. No migration was run by the agent.

## Configuration

Configure the following environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`: exactly the same signing secret as Auth Service and API Gateway,
  at least 32 UTF-8 bytes. Do not use the separate `EXIT_LOAN_SERVICE_SECRET`.

## Running in STS

1. Import the project as an Existing Maven Project.
2. Configure the Oracle Database environment variables.
3. Set `JWT_SECRET` to match Auth Service. The optional `local` profile no longer
   disables authentication; JWT is required in every profile.
4. Run `NocApplication.java` as a Spring Boot application.

**Default Port:** `8088`

Requests through the API Gateway use `http://localhost:8080/api/noc/**`, routed
to this service at `http://localhost:8088/api/noc/**`. LoanAccount remains on
8085, Grievance on 8086, Collateral on 8087, and Audit on 8089. The separate loan
read service remains on 8090. Do not override NOC's port to 8085 in the IDE or via
`SERVER_PORT`, since that port belongs to LoanAccount.

Both the gateway and NOC Service validate bearer JWTs. NOC validates HS256
signatures, issuer `clearclose-auth`, expiry, identity/subject and account-type/role
claims. Direct calls to port 8088 require the same token. No anonymous certificate
verification or local-profile authentication bypass is enabled.

Borrowers can check eligibility, list, read, verify, download and view history only
for their own loans and certificates. Loan Closure Officers and Admins can perform
those reads and generate/reissue/retry. Collateral Officers have read-only access.
Grievance Officers cannot access NOCs. Database account status and the officer's
current role are checked on each operation. Officers cannot generate/reissue/retry
an NOC for their own linked borrower account.

Send `Authorization: Bearer <token>` on every request. `officerId` and `actorId`
query parameters are no longer used; issuer and audit IDs come from the token.
Certificate verification also enforces ownership because the response contains
loan-specific information. See `noc-security-manual.http` for manual checks.

## Important Notes

This service is currently developed using synthetic loan data for demonstration purposes.

The current NOC eligibility check validates loan status, outstanding balance, and closure date. Additional validations for approved loan closure, payment reconciliation, holds, and reversals will be incorporated during integration with the main CLEAR CLOSE application.

JWT/access-control integration does not change the certificate generation or loan
eligibility business checks described above, or introduce automatic NOC generation.

Generated NOC PDF files are excluded from Git version control and should be stored securely in a persistent storage location when deployed.
