export default function OfficerPortals() {
  return (
    <main className="borrower-login-page">
      <section className="borrower-login-card">
        <a href="#home" className="dashboard-brand">
          ClearClose
        </a>

        <h1>Bank Employee Portal</h1>
        <p>Choose the service you want to manage.</p>

        <div
          style={{
            display: "grid",
            gap: "16px",
            marginTop: "24px",
          }}
        >
          <a
            href="#officer-login"
            className="dashboard-primary-button full-width"
          >
            Cooling-off and Loan Closure
          </a>

          <a
            href="#grievance-officer-login"
            className="dashboard-primary-button full-width"
          >
            Grievance Management
          </a>

          <a
            href="#collateral-officer-login"
            className="dashboard-primary-button full-width"
          >
            Collateral Release
          </a>
        </div>

        <a href="#home">Back to Homepage</a>
      </section>
    </main>
  );
}