
import { useEffect, useState } from "react";
import { getMyLoans } from "../api";

// -----------------------------------------
// FORMAT CURRENCY
// -----------------------------------------

function formatCurrency(amount) {
  if (amount == null) return "N/A";

  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(amount);
}

// -----------------------------------------
// FORMAT DATE
// -----------------------------------------

function formatDate(date) {
  if (!date) return "N/A";

  // Oracle dates are returned as local date-time strings.
  // Display the date portion without timezone conversion.
  const datePart = String(date).slice(0, 10);
  const parts = datePart.split("-");

  if (parts.length !== 3) return datePart;

  const [year, month, day] = parts;

  return `${day}-${month}-${year}`;
}

// -----------------------------------------
// FORMAT LOAN TYPE
// -----------------------------------------

function formatLoanType(type) {
  if (!type) return "Loan";

  return type
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

// -----------------------------------------
// STATUS BADGE
// -----------------------------------------

function LoanStatus({ status }) {
  const colors = {
    ACTIVE: "bg-green-100 text-green-700",
    CLOSED: "bg-gray-100 text-gray-700",
    EXIT_COMPLETED: "bg-blue-100 text-blue-700",
    PENDING: "bg-yellow-100 text-yellow-700",
    DEFAULTED: "bg-red-100 text-red-700",
    CANCELLED: "bg-gray-100 text-gray-600",
  };

  return (
    <span
      className={`rounded-full px-3 py-1 text-xs font-semibold ${
        colors[status] || "bg-gray-100 text-gray-700"
      }`}
    >
      {status?.replaceAll("_", " ") || "UNKNOWN"}
    </span>
  );
}

// -----------------------------------------
// LOAN DETAIL FIELD
// -----------------------------------------

function DetailField({ label, value }) {
  return (
    <div>
      <p className="text-xs text-gray-500">
        {label}
      </p>

      <p className="mt-2 break-words text-sm font-semibold text-gray-900">
        {value ?? "N/A"}
      </p>
    </div>
  );
}

// -----------------------------------------
// MAIN DASHBOARD
// -----------------------------------------

function BorrowerDashboard({ session, onLogout }) {

  const [activeMenu, setActiveMenu] = useState("overview");

  const [loans, setLoans] = useState([]);

  const [selectedLoanId, setSelectedLoanId] = useState(null);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  const [search, setSearch] = useState("");

  const [refreshCount, setRefreshCount] = useState(0);

  // -----------------------------------------
  // FETCH LOANS FROM BACKEND
  // -----------------------------------------

  useEffect(() => {
    let active = true;

    async function loadLoans() {
      setLoading(true);
      setError("");

      try {
        const data = await getMyLoans(session.token);

        if (!Array.isArray(data)) {
          throw new Error("Invalid loan response from server");
        }

        if (active) {
          setLoans(data);
        }

      } catch (err) {
        if (active) {
          setError(err.message);
          setLoans([]);
        }

      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadLoans();

    return () => {
      active = false;
    };

  }, [session.token, refreshCount]);

  // -----------------------------------------
  // CALCULATED DASHBOARD VALUES
  // -----------------------------------------

  const totalLoans = loans.length;

  const activeLoans = loans.filter(
    (loan) => loan.loanStatus === "ACTIVE"
  ).length;

  const totalOutstanding = loans.reduce(
    (sum, loan) =>
      sum + Number(loan.outstandingAmount || 0),
    0
  );

  // Select active loan first, otherwise first loan
  const primaryLoan =
    loans.find((loan) => loan.loanStatus === "ACTIVE") ||
    loans[0] ||
    null;

  const selectedLoan =
    loans.find((loan) => loan.loanId === selectedLoanId) ||
    primaryLoan;

  // -----------------------------------------
  // SEARCH LOANS
  // -----------------------------------------

  const filteredLoans = loans.filter((loan) => {

    const keyword = search.toLowerCase();

    return (
      String(loan.loanId).includes(keyword) ||
      String(loan.loanReference || "")
        .toLowerCase()
        .includes(keyword) ||
      String(loan.productType || "")
        .toLowerCase()
        .includes(keyword)
    );
  });

  // -----------------------------------------
  // GREETING
  // -----------------------------------------

  const hour = new Date().getHours();

  const greeting =
    hour < 12
      ? "Good morning"
      : hour < 17
      ? "Good afternoon"
      : "Good evening";

  // -----------------------------------------
  // SIDEBAR MENU
  // -----------------------------------------

  const menuItems = [
    { id: "overview", icon: "▦", label: "Overview" },
    { id: "my-loans", icon: "◫", label: "My Loans" },
  ];

  // -----------------------------------------
  // RENDER
  // -----------------------------------------

  return (
    <main className="borrower-shell">

      {/* SIDEBAR */}

      <aside className="borrower-sidebar">

        <div className="dashboard-brand dashboard-brand-light">
          <span className="dashboard-brand-mark" />
          Clear<span>Close</span>
        </div>

        <p className="sidebar-heading">
          Borrower Portal
        </p>

        <nav className="sidebar-nav">

          {menuItems.map((item) => (

            <button
              key={item.id}
              className={
                activeMenu === item.id
                  ? "sidebar-link active"
                  : "sidebar-link"
              }
              onClick={() => setActiveMenu(item.id)}
            >
              <span>{item.icon}</span>
              {item.label}
            </button>

          ))}

        </nav>

        {/* PROFILE */}

        <div className="borrower-profile">

          <div className="profile-avatar">
            {session.username
              ?.slice(0, 2)
              .toUpperCase()}
          </div>

          <div>
            <strong>{session.username}</strong>
            <span>Borrower Account</span>
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

      {/* MAIN DASHBOARD */}

      <section className="dashboard-main">

        {/* TOP BAR */}

        <header className="dashboard-topbar">

          <div className="dashboard-search">
            <span>⌕</span>

            <input
              placeholder="Search loan ID, reference, type..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="topbar-actions">
            <div className="topbar-avatar">
              {session.username
                ?.slice(0, 2)
                .toUpperCase()}
            </div>
          </div>

        </header>

        <div className="dashboard-content">

          {/* HEADING */}

          <div className="dashboard-intro">

            <div>
              <p className="dashboard-eyebrow">
                {greeting}, {session.username}
              </p>

              <h1>
                Your Loan Dashboard
              </h1>

              <p className="dashboard-muted">
                View and manage your existing loan accounts.
              </p>
            </div>

            <button
              className="dashboard-outline-button"
              onClick={() =>
                setRefreshCount((count) => count + 1)
              }
              disabled={loading}
            >
              Refresh Loans ↻
            </button>

          </div>

          {/* LOADING */}

          {loading && (
            <p className="mt-8 text-gray-500">
              Loading your loans...
            </p>
          )}

          {/* ERROR */}

          {error && !loading && (
            <p
              role="alert"
              className="mt-8 rounded-xl bg-red-50 p-5 text-red-600"
            >
              {error}
            </p>
          )}

          {/* NO LOANS */}

          {!loading && !error && loans.length === 0 && (
            <div className="mt-8 rounded-2xl bg-white p-8">
              <h2 className="text-xl font-semibold">
                No Loans Found
              </h2>

              <p className="mt-3 text-gray-500">
                No existing loan accounts are linked
                to your borrower account.
              </p>
            </div>
          )}

          {/* REAL DATA */}

          {!loading && !error && loans.length > 0 && (
            <>

              {/* SUMMARY */}

              <section className="mt-8 grid gap-5 sm:grid-cols-3">

                <div className="rounded-2xl bg-white p-6 shadow-sm">
                  <p className="text-sm text-gray-500">
                    Total Loans
                  </p>

                  <h2 className="mt-3 text-3xl font-bold">
                    {totalLoans}
                  </h2>
                </div>

                <div className="rounded-2xl bg-white p-6 shadow-sm">
                  <p className="text-sm text-gray-500">
                    Active Loans
                  </p>

                  <h2 className="mt-3 text-3xl font-bold">
                    {activeLoans}
                  </h2>
                </div>

                <div className="rounded-2xl bg-lime-100 p-6 shadow-sm">
                  <p className="text-sm text-gray-600">
                    Total Outstanding
                  </p>

                  <h2 className="mt-3 text-2xl font-bold">
                    {formatCurrency(totalOutstanding)}
                  </h2>
                </div>

              </section>

              {/* SELECTED LOAN SUMMARY */}

              {selectedLoan && (
                <section className="loan-summary-card mt-8">

                  <div>
                    <p className="dashboard-eyebrow">
                      Selected Loan
                    </p>

                    <h2>
                      {formatLoanType(
                        selectedLoan.productType
                      )}
                    </h2>

                    <p>
                      Loan ID: {selectedLoan.loanId}
                    </p>

                    <p>
                      Reference: {selectedLoan.loanReference}
                    </p>
                  </div>

                  <div className="loan-summary-amount">
                    <span>Outstanding Amount</span>

                    <strong>
                      {formatCurrency(
                        selectedLoan.outstandingAmount
                      )}
                    </strong>
                  </div>

                  <div className="loan-summary-action">
                    <span>Loan Status</span>

                    <LoanStatus
                      status={selectedLoan.loanStatus}
                    />
                  </div>

                </section>
              )}

              {/* MY LOANS */}

              <section className="mt-10">

                <h2 className="mb-6 text-2xl font-semibold">
                  My Loans
                </h2>

                {filteredLoans.length === 0 ? (
                  <p className="rounded-xl bg-white p-6 text-gray-500">
                    No loans match your search.
                  </p>
                ) : (
                  <div className="grid gap-5 md:grid-cols-2">

                    {filteredLoans.map((loan) => (

                      <article
                        key={loan.loanId}
                        className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm"
                      >

                        <div className="flex items-start justify-between gap-3">

                          <div>
                            <h3 className="text-xl font-semibold">
                              {formatLoanType(
                                loan.productType
                              )}
                            </h3>

                            <p className="mt-2 text-xs text-gray-500">
                              {loan.loanReference}
                            </p>
                          </div>

                          <LoanStatus
                            status={loan.loanStatus}
                          />

                        </div>

                        <div className="mt-6 grid grid-cols-2 gap-5">

                          <DetailField
                            label="Loan ID"
                            value={loan.loanId}
                          />

                          <DetailField
                            label="Loan Tenure"
                            value={`${loan.tenureMonths} months`}
                          />

                          <DetailField
                            label="Principal Amount"
                            value={formatCurrency(
                              loan.principalAmount
                            )}
                          />

                          <DetailField
                            label="Outstanding Amount"
                            value={formatCurrency(
                              loan.outstandingAmount
                            )}
                          />

                          <DetailField
                            label="Interest Rate"
                            value={`${loan.interestRate}%`}
                          />

                          <DetailField
                            label="Cooling-Off Period"
                            value={`${loan.coolingOffDays} days`}
                          />

                        </div>

                        <button
                          onClick={() => {
                            setSelectedLoanId(loan.loanId);
                            setActiveMenu("overview");
                          }}
                          className="mt-7 w-full rounded-xl bg-black py-3 text-sm font-semibold text-white hover:bg-gray-800"
                        >
                          View Loan Details →
                        </button>

                      </article>

                    ))}

                  </div>
                )}

              </section>

              {/* FULL LOAN DETAILS */}

              {activeMenu === "overview" && selectedLoan && (

                <section className="mt-10 rounded-2xl bg-white p-7 shadow-sm">

                  <div className="flex items-center justify-between gap-3">

                    <div>
                      <p className="dashboard-eyebrow">
                        Loan Information
                      </p>

                      <h2 className="mt-2 text-2xl font-semibold">
                        Complete Loan Details
                      </h2>
                    </div>

                    <LoanStatus
                      status={selectedLoan.loanStatus}
                    />

                  </div>

                  <div className="mt-8 grid gap-7 sm:grid-cols-2 lg:grid-cols-3">

                    <DetailField
                      label="Loan ID"
                      value={selectedLoan.loanId}
                    />

                    <DetailField
                      label="Loan Reference"
                      value={selectedLoan.loanReference}
                    />

                    <DetailField
                      label="Loan Type"
                      value={formatLoanType(
                        selectedLoan.productType
                      )}
                    />

                    <DetailField
                      label="Principal Amount"
                      value={formatCurrency(
                        selectedLoan.principalAmount
                      )}
                    />

                    <DetailField
                      label="Outstanding Amount"
                      value={formatCurrency(
                        selectedLoan.outstandingAmount
                      )}
                    />

                    <DetailField
                      label="Interest Rate"
                      value={`${selectedLoan.interestRate}%`}
                    />

                    <DetailField
                      label="Tenure"
                      value={`${selectedLoan.tenureMonths} months`}
                    />

                    <DetailField
                      label="Disbursement Date"
                      value={formatDate(
                        selectedLoan.disbursementDate
                      )}
                    />

                    <DetailField
                      label="Maturity Date"
                      value={formatDate(
                        selectedLoan.maturityDate
                      )}
                    />

                    <DetailField
                      label="Cooling-Off Duration"
                      value={`${selectedLoan.coolingOffDays} days`}
                    />

                    <DetailField
                      label="Cooling-Off Expiry"
                      value={formatDate(
                        selectedLoan.coolingOffExpiry
                      )}
                    />

                    <DetailField
                      label="Closure Date"
                      value={formatDate(
                        selectedLoan.closureDate
                      )}
                    />

                  </div>

                </section>

              )}

            </>
          )}

        </div>
      </section>

    </main>
  );
}

export default BorrowerDashboard;
