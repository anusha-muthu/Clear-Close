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

## Configuration

Configure the following environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SPRING_PROFILES_ACTIVE=local` (for local development and testing only)

## Running in STS

1. Import the project as an Existing Maven Project.
2. Configure the Oracle Database environment variables.
3. Enable the `local` profile for development and testing.
4. Run `NocApplication.java` as a Spring Boot application.

**Default Port:** `8085`

## Important Notes

This service is currently developed using synthetic loan data for demonstration purposes.

The current NOC eligibility check validates loan status, outstanding balance, and closure date. Additional validations for approved loan closure, payment reconciliation, holds, and reversals will be incorporated during integration with the main CLEAR CLOSE application.

The local security configuration is intended for development and testing only. Proper authentication and role-based authorization must be implemented before production deployment.

Generated NOC PDF files are excluded from Git version control and should be stored securely in a persistent storage location when deployed.