import { useEffect, useState } from "react";
import { getMyLoans } from "../api";

const menuItems = [
  { id: "overview", icon: "▦", label: "Overview" },
  { id: "my-loans", icon: "◫", label: "My Loans" },
  { id: "closure", icon: "↗", label: "Loan Closure" },
  { id: "documents", icon: "▤", label: "Documents" },
  { id: "support", icon: "?", label: "Support" },
];

function ProgressBar({ value, color = "green" }) {
  return (
    <div className="progress-track">
      <span
        className={`progress-value progress-${color}`}
        style={{ width: `${value}%` }}
      />
    </div>
  );
}

function formatCurrency(amount) {
  if (amount == null) return "N/A";

  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);
}

function formatLoanType(type) {
  if (!type) return "Loan";

  return type
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function BorrowerDashboard({ session, onLogout }) {
  const [activeMenu, setActiveMenu] = useState("overview");
  const [loans, setLoans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let isCurrent = true;

    const loadLoans = async () => {
      setLoading(true);
      setError("");

      try {
        const data = await getMyLoans(session.token);
        if (isCurrent) setLoans(data);
      } catch (loadError) {
        if (isCurrent) setError(loadError.message);
      } finally {
        if (isCurrent) setLoading(false);
      }
    };

    loadLoans();
    return () => {
      isCurrent = false;
    };
  }, [session.token]);

  const primaryLoan = loans.find((loan) => loan.loanStatus === "ACTIVE") || loans[0];
  const totalOutstanding = loans.reduce(
    (total, loan) => total + Number(loan.outstandingAmount || 0),
    0,
  );

  return (
    <main className="borrower-shell">
      <aside className="borrower-sidebar">
        <a href="#home" className="dashboard-brand dashboard-brand-light">
          <span className="dashboard-brand-mark" />
          Clear<span>Close</span>
        </a>

        <p className="sidebar-heading">Borrower portal</p>

        <nav className="sidebar-nav">
          {menuItems.map((item) => (
            <button
              key={item.id}
              className={activeMenu === item.id ? "sidebar-link active" : "sidebar-link"}
              onClick={() => setActiveMenu(item.id)}
            >
              <span>{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-help">
          <p className="sidebar-heading">Need help?</p>
          <strong>Talk to a loan expert</strong>
          <span>Mon–Sat, 9 AM–6 PM</span>
          <button>Contact support</button>
        </div>

        <div className="borrower-profile">
          <div className="profile-avatar">AY</div>
          <div>
            <strong>{session.username}</strong>
            <span>Borrower account</span>
          </div>
          <button
            className="logout-button"
            title="Sign out"
            onClick={onLogout}
          >
            ↗
          </button>
        </div>
      </aside>

      <section className="dashboard-main">
        <header className="dashboard-topbar">
          <div className="dashboard-search">
            <span>⌕</span>
            <input placeholder="Search loans, requests, documents..." />
          </div>

          <div className="topbar-actions">
            <button title="Notifications">♧</button>
            <button title="Theme">◐</button>
            <div className="topbar-avatar">AY</div>
          </div>
        </header>

        <div className="dashboard-content">
          <div className="dashboard-intro">
            <div>
              <p className="dashboard-eyebrow">Good morning, {session.username}</p>
              <h1>Your loan dashboard</h1>
              <p className="dashboard-muted">
                Keep your loan closure moving with clear next steps.
              </p>
            </div>

            <button className="dashboard-outline-button">
              Download statement ↓
            </button>
          </div>

          {loading && (
            <p className="dashboard-muted" style={{ marginTop: "24px" }}>
              Loading your loan accounts...
            </p>
          )}

          {error && (
            <p className="login-error" role="alert" style={{ marginTop: "24px" }}>
              {error}
            </p>
          )}

          <section className="loan-summary-card">
            <div>
              <p className="dashboard-eyebrow">Active loan</p>
              <h2>{formatLoanType(primaryLoan?.productType)}</h2>
              <p>ClearClose Finance · Loan ending in 4821</p>
            </div>

            <div className="loan-summary-amount">
              <span>Outstanding amount</span>
              <strong>{formatCurrency(totalOutstanding)}</strong>
            </div>

            <div className="loan-summary-action">
              <span>Closure request</span>
              <strong>{primaryLoan?.loanStatus || "No active loan"}</strong>
              <button>View request →</button>
            </div>
          </section>
{/* 
          <section className="dashboard-stat-grid">
            <article className="dashboard-stat-card">
              <span className="stat-icon">₹</span>
              <p>Next EMI</p>
              <strong>DUMMY ₹18,450</strong>
              <small>DUMMY · Due 05 Nov 2026</small>
            </article>

            <article className="dashboard-stat-card">
              <span className="stat-icon">◷</span>
              <p>Loan closure status</p>
              <strong>DUMMY · 68% complete</strong>
              <ProgressBar value={68} />
              <small>DUMMY · 2 actions remaining</small>
            </article>

            <article className="dashboard-stat-card">
              <span className="stat-icon">▤</span>
              <p>Documents</p>
              <strong>DUMMY · 4 of 5 ready</strong>
              <small>DUMMY · One document needs attention</small>
            </article>
          </section>

          <section className="dashboard-two-column">
            <article className="dashboard-panel closure-panel">
              <div className="panel-heading">
                <div>
                  <p className="dashboard-eyebrow">Closure journey</p>
                  <h2>Where you are now</h2>
                </div>
                <span className="status-pill">DUMMY · In progress</span>
              </div>

              <div className="journey-list">
                <div className="journey-step completed">
                  <span>✓</span>
                  <div>
                    <strong>Closure request submitted</strong>
                    <p>DUMMY · Your request was received on 12 Oct 2026.</p>
                  </div>
                </div>

                <div className="journey-step completed">
                  <span>✓</span>
                  <div>
                    <strong>Lender verification</strong>
                    <p>DUMMY · Your loan details have been verified.</p>
                  </div>
                </div>

                <div className="journey-step current">
                  <span>3</span>
                  <div>
                    <strong>Review settlement amount</strong>
                    <p>DUMMY · Confirm your settlement statement to continue.</p>
                    <button className="text-button">Review statement →</button>
                  </div>
                </div>

                <div className="journey-step">
                  <span>4</span>
                  <div>
                    <strong>Receive NOC</strong>
                    <p>DUMMY · Your NOC will be available after closure is completed.</p>
                  </div>
                </div>
              </div>
            </article>

            <article className="dashboard-panel payment-panel">
              <div className="panel-heading">
                <div>
                  <p className="dashboard-eyebrow">Payment overview</p>
                  <h2>Loan repayment</h2>
                </div>
                <button className="panel-menu">•••</button>
              </div>

              <div className="repayment-visual">
                <div className="repayment-ring">
                  <span>DUMMY 64%</span>
                  <small>repaid</small>
                </div>

                <div className="repayment-details">
                  <div>
                    <span>Total sanctioned</span>
                    <strong>DUMMY ₹12,00,000</strong>
                  </div>
                  <div>
                    <span>Paid so far</span>
                    <strong>DUMMY ₹4,50,000</strong>
                  </div>
                  <div>
                    <span>Outstanding</span>
                    <strong>DUMMY ₹7,50,000</strong>
                  </div>
                </div>
              </div>

              <button className="dashboard-primary-button full-width">
                Make a payment
              </button>
            </article>
          </section> */}
        </div>
      </section>

    </main>
  );
}

export default BorrowerDashboard;
