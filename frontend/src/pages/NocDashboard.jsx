import { useEffect, useRef, useState } from "react";
import { getMyLoans } from "../api";
import {
  downloadNoc,
  generateNoc,
  getLoanNocs,
  getNocEligibility,
  getNocHistory,
  reissueNoc,
  retryNoc,
  verifyNoc,
} from "../nocApi";
import "./NocDashboard.css";

function formatDate(value) {
  if (!value) return "—";

  const [date, time] = String(value).split("T");
  const parts = date.split("-");
  const formatted =
    parts.length === 3 ? `${parts[2]}-${parts[1]}-${parts[0]}` : date;

  return time ? `${formatted} ${time.slice(0, 5)}` : formatted;
}

function StatusBadge({ status }) {
  const tone = {
    AVAILABLE: "success",
    FAILED: "danger",
    DELAYED: "warning",
    GENERATION_PENDING: "warning",
    REVOKED: "neutral",
  }[status] || "neutral";

  return (
    <span className={`noc-badge noc-badge-${tone}`}>
      {(status || "UNKNOWN").replaceAll("_", " ")}
    </span>
  );
}

function Detail({ label, children }) {
  return (
    <div className="noc-detail">
      <dt>{label}</dt>
      <dd>{children ?? "—"}</dd>
    </div>
  );
}

export default function NocDashboard({ session, onLogout }) {
  // Remount when the signed-in account changes.
  return (
    <NocWorkspace
      key={`${session.accountType}:${session.userId}`}
      session={session}
      onLogout={onLogout}
    />
  );
}

function NocWorkspace({ session, onLogout }) {
  const borrower = session.accountType === "BORROWER";

  const canManage =
    session.accountType === "OFFICER" &&
    ["LOAN_CLOSURE_OFFICER", "ADMIN"].includes(session.role);

  const backRoute = borrower
    ? "#borrower-dashboard"
    : session.role === "COLLATERAL_OFFICER"
      ? "#collateral-officer-dashboard"
      : "#loan-closure-dashboard";

  const [loans, setLoans] = useState([]);
  const [loansLoading, setLoansLoading] = useState(borrower);
  const [loansError, setLoansError] = useState("");
  const [loansRefresh, setLoansRefresh] = useState(0);

  const [loanInput, setLoanInput] = useState("");
  const [loanId, setLoanId] = useState("");

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [selectedId, setSelectedId] = useState("");

  const [history, setHistory] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const [certificateInput, setCertificateInput] = useState("");
  const [verification, setVerification] = useState(null);

  const [busy, setBusy] = useState("");
  const actionLock = useRef(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");

  // Borrowers can choose only from their own loans.
  useEffect(() => {
    if (!borrower) return;

    let active = true;

    async function loadLoans() {
      setLoansLoading(true);
      setLoansError("");

      try {
        const result = await getMyLoans(session.token);

        if (!Array.isArray(result)) {
          throw new Error("The service returned an invalid loan list.");
        }

        if (active) setLoans(result);
      } catch (error) {
        if (active) setLoansError(error.message);
      } finally {
        if (active) setLoansLoading(false);
      }
    }

    loadLoans();

    return () => {
      active = false;
    };
  }, [borrower, session.token, loansRefresh]);

  // Load eligibility and certificate versions for the selected loan.
  useEffect(() => {
    if (!loanId) return;

    const controller = new AbortController();

    async function loadCertificates() {
      setLoading(true);
      setLoadError("");
      setData(null);

      try {
        const [eligibility, certificates] = await Promise.all([
          getNocEligibility(session.token, loanId, controller.signal),
          getLoanNocs(session.token, loanId, controller.signal),
        ]);

        if (!Array.isArray(certificates)) {
          throw new Error("The service returned invalid certificate data.");
        }

        if (!controller.signal.aborted) {
          setData({
            loanId,
            eligibility,
            certificates: [...certificates].sort(
              (a, b) => b.versionNumber - a.versionNumber
            ),
          });
        }
      } catch (error) {
        if (!controller.signal.aborted) setLoadError(error.message);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }

    loadCertificates();

    return () => controller.abort();
  }, [loanId, refresh, session.token]);

  const ready = Boolean(data && data.loanId === loanId);
  const certificates = ready ? data.certificates : [];
  const eligibility = ready ? data.eligibility : null;
  const latest = certificates[0];

  const selected =
    certificates.find((item) => String(item.nocId) === selectedId) ||
    latest ||
    null;

  const selectedNocId = selected?.nocId;

  // Abort outdated history requests when changing certificate or loan.
  useEffect(() => {
    if (!selectedNocId) return;

    const controller = new AbortController();

    async function loadHistory() {
      setHistoryLoading(true);
      setHistoryError("");
      setHistory(null);

      try {
        const entries = await getNocHistory(
          session.token,
          selectedNocId,
          controller.signal
        );

        if (!Array.isArray(entries)) {
          throw new Error("The service returned invalid history data.");
        }

        if (!controller.signal.aborted) {
          setHistory({ nocId: selectedNocId, entries });
        }
      } catch (error) {
        if (!controller.signal.aborted) setHistoryError(error.message);
      } finally {
        if (!controller.signal.aborted) setHistoryLoading(false);
      }
    }

    loadHistory();

    return () => controller.abort();
  }, [selectedNocId, refresh, session.token]);

  function chooseLoan(value) {
    setLoanId(value);
    setData(null);
    setSelectedId("");
    setHistory(null);
    setLoadError("");
    setActionError("");
    setNotice("");
  }

  function openOfficerLoan(event) {
    event.preventDefault();

    const value = loanInput.trim();

    if (!/^[1-9]\d*$/.test(value)) {
      setActionError("Enter a valid positive loan ID.");
      return;
    }

    if (loanId === value) {
      refreshCertificates();
    } else {
      chooseLoan(value);
    }
  }

  function refreshCertificates() {
    setData(null);
    setHistory(null);
    setRefresh((value) => value + 1);
  }

  async function runAction(name, operation) {
    if (actionLock.current) return;

    actionLock.current = true;
    setBusy(name);
    setActionError("");
    setNotice("");

    try {
      await operation();
    } catch (error) {
      setActionError(error.message);
    } finally {
      actionLock.current = false;
      setBusy("");
    }
  }

  function manageCertificate(kind, nocId) {
    const actions = {
      generate: () => generateNoc(session.token, loanId),
      reissue: () => reissueNoc(session.token, loanId),
      retry: () => retryNoc(session.token, nocId),
    };

    runAction(kind, async () => {
      try {
        const result = await actions[kind]();

        if (result?.status !== "AVAILABLE") {
          throw new Error(
            result?.remarks ||
              "The certificate is not available yet. Check its latest status."
          );
        }

        setSelectedId(String(result.nocId));
        setNotice(
          kind === "reissue"
            ? "A replacement certificate was issued. The previous version is revoked."
            : "The certificate is ready to download."
        );
      } finally {
        // Generation failures can still create a FAILED record.
        // Always reload before allowing another management action.
        refreshCertificates();
      }
    });
  }

  function handleDownload(certificate) {
    runAction("download", async () => {
      await downloadNoc(session.token, certificate.nocId);
      setNotice("Certificate download started.");
      setRefresh((value) => value + 1);
    });
  }

  function handleVerify(event) {
    event.preventDefault();

    const number = certificateInput.trim();

    if (!number) return;

    setVerification(null);

    runAction("verify", async () => {
      const result = await verifyNoc(session.token, number);
      setVerification(result);
    });
  }

  const blocked = Boolean(busy) || loading || !ready;
  const availableCount = certificates.filter(
    (item) => item.status === "AVAILABLE"
  ).length;
  const attentionCount = certificates.filter(
    (item) => ["FAILED", "DELAYED"].includes(item.status)
  ).length;

  return (
    <div className="borrower-shell noc-shell">
      <aside className="borrower-sidebar">
        <a href="#home" className="dashboard-brand dashboard-brand-light">
          <span className="dashboard-brand-mark" aria-hidden="true" />
          Clear<span>Close</span>
        </a>

        <p className="sidebar-heading">
          {borrower ? "Borrower Portal" : "Officer Portal"}
        </p>

        <nav className="sidebar-nav" aria-label="NOC navigation">
          <a href={backRoute} className="sidebar-link">
            <span aria-hidden="true">▦</span>
            Dashboard
          </a>

          <a
            href="#noc-dashboard"
            className="sidebar-link active"
            aria-current="page"
          >
            <span aria-hidden="true">▤</span>
            NOC Certificates
          </a>

          {!borrower && (
            <a href="#officer-portals" className="sidebar-link">
              <span aria-hidden="true">↗</span>
              Officer Services
            </a>
          )}
        </nav>

        <div className="sidebar-help">
          <p className="sidebar-heading">Your loan, closed clearly</p>
          <strong>Certificate support</strong>
          <span>
            View your certificate status, download the PDF, and track its
            history in one place.
          </span>
        </div>

        <div className="borrower-profile">
          <div className="profile-avatar" aria-hidden="true">
            {(session.username || "CC").slice(0, 2).toUpperCase()}
          </div>

          <div>
            <strong>{session.username}</strong>
            <span>{borrower ? "Borrower" : session.role?.replaceAll("_", " ")}</span>
          </div>

          <button
            type="button"
            className="logout-button"
            onClick={onLogout}
            aria-label="Sign out"
            title="Sign out"
          >
            ↪
          </button>
        </div>
      </aside>

      <main className="dashboard-main noc-main">
        <header className="noc-topbar">
          <a href={backRoute}>← Back to dashboard</a>
          <span className="noc-portal-label">
            {canManage ? "Certificate Management" : "Certificate Centre"}
          </span>
        </header>

        <div className="noc-content">
          <section className="noc-heading">
            <p className="dashboard-eyebrow">Loan Closure Documentation</p>
            <h1>No Objection Certificates</h1>
            <p className="dashboard-muted">
              {canManage
                ? "Check loan eligibility and manage certificate issuance."
                : "View, verify, and download certificates for your loans."}
            </p>
          </section>

          <section className="noc-banner">
            <div>
              <p className="noc-banner-label">CLEAR RECORDS. COMPLETE CLOSURE.</p>
              <h2>Your loan closure, documented.</h2>
              <p>
                NOC issuance requires a closed loan, zero outstanding dues,
                and a recorded closure date.
              </p>
            </div>
            <span className="noc-banner-icon" aria-hidden="true">✓</span>
          </section>

          <section className="noc-card" aria-labelledby="noc-loan-title">
            <div className="noc-section-heading">
              <div>
                <p className="dashboard-eyebrow">Step 01</p>
                <h2 id="noc-loan-title">Select a loan</h2>
              </div>

              {loanId && (
                <button
                  type="button"
                  className="noc-button noc-button-outline"
                  onClick={refreshCertificates}
                  disabled={Boolean(busy) || loading}
                >
                  {loading ? "Refreshing…" : "Refresh"}
                </button>
              )}
            </div>

            {borrower ? (
              <div className="noc-field">
                <label htmlFor="noc-loan">Your loan account</label>

                <select
                  id="noc-loan"
                  value={loanId}
                  onChange={(event) => chooseLoan(event.target.value)}
                  disabled={loansLoading || Boolean(busy)}
                >
                  <option value="">
                    {loansLoading ? "Loading your loans…" : "Select a loan"}
                  </option>

                  {loans.map((loan) => (
                    <option key={loan.loanId} value={String(loan.loanId)}>
                      {loan.loanReference || `Loan #${loan.loanId}`}
                      {loan.loanStatus ? ` · ${loan.loanStatus}` : ""}
                    </option>
                  ))}
                </select>

                {loansError && (
                  <div className="noc-alert noc-alert-danger" role="alert">
                    <p>{loansError}</p>
                    <button
                      type="button"
                      className="noc-button noc-button-outline"
                      onClick={() => setLoansRefresh((value) => value + 1)}
                      disabled={loansLoading}
                    >
                      Retry loading loans
                    </button>
                  </div>
                )}

                {!loansLoading && !loansError && loans.length === 0 && (
                  <p className="noc-muted">No loans are linked to this account.</p>
                )}
              </div>
            ) : (
              <form className="noc-form-row" onSubmit={openOfficerLoan}>
                <div className="noc-field">
                  <label htmlFor="noc-loan-id">Loan account ID</label>
                  <input
                    id="noc-loan-id"
                    type="text"
                    inputMode="numeric"
                    pattern="[1-9][0-9]*"
                    placeholder="Enter loan ID"
                    value={loanInput}
                    onChange={(event) => setLoanInput(event.target.value)}
                    disabled={Boolean(busy)}
                    required
                  />
                </div>

                <button
                  className="noc-button noc-button-primary"
                  disabled={Boolean(busy) || loading}
                >
                  View certificates
                </button>
              </form>
            )}
          </section>

          {actionError && (
            <p className="noc-alert noc-alert-danger" role="alert">
              {actionError}
            </p>
          )}

          {notice && (
            <p className="noc-alert noc-alert-success" role="status">
              {notice}
            </p>
          )}

          {loadError && (
            <p className="noc-alert noc-alert-danger" role="alert">
              {loadError} Use Refresh to try again.
            </p>
          )}

          {loading && (
            <div className="noc-card noc-empty" role="status">
              Loading eligibility and certificates…
            </div>
          )}

          {ready && (
            <>
              <section className="noc-stats" aria-label="Selected loan summary">
                {[
                  ["Certificate versions", certificates.length],
                  ["Available", availableCount],
                  ["Need attention", attentionCount],
                ].map(([label, value]) => (
                  <div className="noc-stat" key={label}>
                    <p>{label}</p>
                    <strong>{value}</strong>
                    <span>Loan #{loanId}</span>
                  </div>
                ))}
              </section>

              <section className="noc-card">
                <div className="noc-section-heading">
                  <div>
                    <p className="dashboard-eyebrow">Eligibility</p>
                    <h2>
                      {eligibility?.eligible
                        ? "Loan eligible for NOC"
                        : "Loan not eligible yet"}
                    </h2>
                  </div>

                  <span
                    className={`noc-badge noc-badge-${
                      eligibility?.eligible ? "success" : "warning"
                    }`}
                  >
                    {eligibility?.eligible ? "ELIGIBLE" : "NOT ELIGIBLE"}
                  </span>
                </div>

                <p className="noc-muted">{eligibility?.reason}</p>

                {canManage ? (
                  <div className="noc-actions">
                    {certificates.length === 0 && (
                      <button
                        type="button"
                        className="noc-button noc-button-primary"
                        disabled={blocked || !eligibility?.eligible}
                        onClick={() => manageCertificate("generate")}
                      >
                        Generate NOC
                      </button>
                    )}

                    {latest?.status === "AVAILABLE" && (
                      <button
                        type="button"
                        className="noc-button noc-button-primary"
                        disabled={blocked || !eligibility?.eligible}
                        onClick={() => manageCertificate("reissue")}
                      >
                        Reissue latest certificate
                      </button>
                    )}

                    {latest?.status === "AVAILABLE" && (
                      <p className="noc-muted">
                        Reissuing creates a new version and revokes the
                        previous certificate when successful.
                      </p>
                    )}
                  </div>
                ) : (
                  <p className="noc-note">
                    Certificates are issued by an authorized loan closure
                    officer or administrator.
                  </p>
                )}
              </section>

              <section className="noc-card">
                <div className="noc-section-heading">
                  <div>
                    <p className="dashboard-eyebrow">Step 02</p>
                    <h2>Certificate versions</h2>
                  </div>
                  <span className="noc-muted">
                    {certificates.length} record(s)
                  </span>
                </div>

                {certificates.length === 0 ? (
                  <div className="noc-empty">
                    <strong>No certificate has been issued.</strong>
                    <p>
                      {canManage
                        ? "Generate an NOC once this loan is eligible."
                        : "Your certificate will appear here after issuance."}
                    </p>
                  </div>
                ) : (
                  <div className="noc-table-wrap">
                    <table className="noc-table">
                      <caption className="noc-sr-only">
                        NOC certificates for loan {loanId}
                      </caption>
                      <thead>
                        <tr>
                          <th scope="col">Certificate</th>
                          <th scope="col">Version</th>
                          <th scope="col">Issued</th>
                          <th scope="col">Status</th>
                          <th scope="col">Actions</th>
                        </tr>
                      </thead>

                      <tbody>
                        {certificates.map((certificate) => (
                          <tr
                            key={certificate.nocId}
                            className={
                              selected?.nocId === certificate.nocId
                                ? "noc-row-selected"
                                : ""
                            }
                          >
                            <td className="noc-certificate-number">
                              {certificate.certificateNumber}
                            </td>
                            <td>v{certificate.versionNumber}</td>
                            <td>{formatDate(certificate.issueDate)}</td>
                            <td>
                              <StatusBadge status={certificate.status} />
                            </td>
                            <td>
                              <div className="noc-table-actions">
                                <button
                                  type="button"
                                  className="noc-text-button"
                                  aria-pressed={
                                    selected?.nocId === certificate.nocId
                                  }
                                  disabled={Boolean(busy)}
                                  onClick={() => {
                                    setHistory(null);
                                    setSelectedId(String(certificate.nocId));
                                  }}
                                >
                                  Details
                                </button>

                                {certificate.status === "AVAILABLE" && (
                                  <button
                                    type="button"
                                    className="noc-text-button"
                                    disabled={Boolean(busy)}
                                    onClick={() => handleDownload(certificate)}
                                  >
                                    Download PDF
                                  </button>
                                )}

                                {canManage &&
                                  ["FAILED", "DELAYED"].includes(
                                    certificate.status
                                  ) && (
                                    <button
                                      type="button"
                                      className="noc-text-button"
                                      disabled={
                                        blocked || !eligibility?.eligible
                                      }
                                      onClick={() =>
                                        manageCertificate(
                                          "retry",
                                          certificate.nocId
                                        )
                                      }
                                    >
                                      Retry
                                    </button>
                                  )}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>

              {selected && (
                <section className="noc-card">
                  <div className="noc-section-heading">
                    <div>
                      <p className="dashboard-eyebrow">Certificate Details</p>
                      <h2>Version {selected.versionNumber}</h2>
                    </div>
                    <StatusBadge status={selected.status} />
                  </div>

                  <dl className="noc-details">
                    <Detail label="Certificate number">
                      {selected.certificateNumber}
                    </Detail>
                    <Detail label="NOC ID">{selected.nocId}</Detail>
                    <Detail label="Loan ID">{selected.loanId}</Detail>
                    <Detail label="Issue date">
                      {formatDate(selected.issueDate)}
                    </Detail>
                    <Detail label="Generated at">
                      {formatDate(selected.generatedAt)}
                    </Detail>
                    <Detail label="Remarks">{selected.remarks}</Detail>
                  </dl>

                  <div className="noc-history-heading">
                    <h3>Certificate history</h3>
                    <button
                      type="button"
                      className="noc-text-button"
                      disabled={Boolean(busy) || loading || historyLoading}
                      onClick={() => setRefresh((value) => value + 1)}
                    >
                      Refresh history
                    </button>
                  </div>

                  {historyLoading && (
                    <p className="noc-muted" role="status">
                      Loading history…
                    </p>
                  )}

                  {historyError && (
                    <p role="alert" className="noc-alert noc-alert-danger">
                      {historyError}
                    </p>
                  )}

                  {history?.nocId === selected.nocId && (
                    history.entries.length ? (
                      <ol className="noc-timeline">
                        {history.entries.map((entry) => (
                          <li key={entry.auditId}>
                            <div className="noc-timeline-heading">
                              <strong>
                                {entry.action?.replaceAll("_", " ")}
                              </strong>
                              <span>{formatDate(entry.actionAt)}</span>
                            </div>
                            <p>{entry.remarks || "No additional remarks."}</p>
                            <small>Actor ID: {entry.actorId ?? "—"}</small>
                          </li>
                        ))}
                      </ol>
                    ) : (
                      <p className="noc-muted">No history recorded.</p>
                    )
                  )}
                </section>
              )}
            </>
          )}

          {!loanId && (
            <div className="noc-card noc-empty">
              Select a loan to view eligibility and certificate records.
            </div>
          )}

          <section className="noc-card">
            <div className="noc-section-heading">
              <div>
                <p className="dashboard-eyebrow">Certificate Lookup</p>
                <h2>Verify a certificate</h2>
              </div>
            </div>

            <p className="noc-muted">
              Look up a certificate your account is authorized to access.
              Its current status determines whether it is available.
            </p>

            <form className="noc-form-row noc-verify-form" onSubmit={handleVerify}>
              <div className="noc-field">
                <label htmlFor="noc-certificate">Certificate number</label>
                <input
                  id="noc-certificate"
                  placeholder="NOC-..."
                  value={certificateInput}
                  onChange={(event) => {
                    setCertificateInput(event.target.value);
                    setVerification(null);
                  }}
                  disabled={Boolean(busy)}
                  required
                />
              </div>

              <button
                className="noc-button noc-button-primary"
                disabled={Boolean(busy) || !certificateInput.trim()}
              >
                {busy === "verify" ? "Verifying…" : "Verify certificate"}
              </button>
            </form>

            {verification && (
              <div className="noc-verification" role="status">
                <div className="noc-section-heading">
                  <strong>
                    {verification.status === "AVAILABLE"
                      ? "Certificate record found — available"
                      : "Certificate record found"}
                  </strong>
                  <StatusBadge status={verification.status} />
                </div>

                <dl className="noc-details">
                  <Detail label="Certificate number">
                    {verification.certificateNumber}
                  </Detail>
                  <Detail label="Loan ID">{verification.loanId}</Detail>
                  <Detail label="Version">
                    {verification.versionNumber}
                  </Detail>
                  <Detail label="Issue date">
                    {formatDate(verification.issueDate)}
                  </Detail>
                </dl>

                {verification.status === "AVAILABLE" ? (
                  <button
                    type="button"
                    className="noc-button noc-button-outline"
                    disabled={Boolean(busy)}
                    onClick={() => handleDownload(verification)}
                  >
                    Download PDF
                  </button>
                ) : (
                  <p className="noc-note">
                    This certificate is {verification.status?.replaceAll("_", " ")}
                    {" "}and is unavailable for download.
                  </p>
                )}
              </div>
            )}
          </section>

          {busy && (
            <p className="noc-working" role="status">
              {busy === "download"
                ? "Preparing your download…"
                : busy === "verify"
                  ? "Checking certificate…"
                  : "Processing certificate. Please wait…"}
            </p>
          )}

          <footer className="noc-footer">
            ClearClose · NOC Service
            <span>
              Certificates generated by this backend use demonstration data.
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}