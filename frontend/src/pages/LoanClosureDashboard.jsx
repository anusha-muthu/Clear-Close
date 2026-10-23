
import { useEffect, useRef, useState } from "react";
import RequestInformation from "../components/RequestInformation";

import {
  getOfficerRequests,
  getOfficerRequestDetails,
  getOfficerSummary,
  assignRequest,
  makeOfficerDecision,
} from "../officerApi";

// -----------------------------------------
// FORMAT CURRENCY
// -----------------------------------------

function formatCurrency(amount) {
  if (amount == null) return "Not available";

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
  if (!date) return "Not available";

  return String(date)
    .replace("T", " ")
    .slice(0, 16);
}

// -----------------------------------------
// STATUS BADGE
// -----------------------------------------

function StatusBadge({ status }) {
  const colors = {
    PENDING: "officer-status-pending",
    UNDER_REVIEW: "officer-status-review",
    APPROVED: "officer-status-success",
    COMPLETED: "officer-status-success",
    REJECTED: "officer-status-danger",
    MORE_INFO_REQUIRED: "officer-status-attention",
    CANCELLED: "officer-status-neutral",
    EXPIRED: "officer-status-neutral",
  };

  return (
    <span
      className={`officer-status ${colors[status] || "officer-status-neutral"}`}
    >
      {status?.replaceAll("_", " ") || "UNKNOWN"}
    </span>
  );
}

// -----------------------------------------
// DETAIL FIELD
// -----------------------------------------

function DetailField({ label, value }) {
  return (
    <div className="officer-detail-field">
      <p className="text-xs text-gray-500">
        {label}
      </p>

      <p className="mt-2 break-words text-sm font-semibold">
        {value ?? "Not available"}
      </p>
    </div>
  );
}

// =========================================
// MAIN OFFICER DASHBOARD
// =========================================

function LoanClosureDashboard({ session, onLogout }) {

  const [requests, setRequests] = useState([]);
  const [summary, setSummary] = useState(null);

  const [selectedId, setSelectedId] = useState(null);
  const [selectedRequest, setSelectedRequest] = useState(null);

  const [activeView, setActiveView] = useState("all");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const [loading, setLoading] = useState(true);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [assigning, setAssigning] = useState(false);
  const [decision, setDecision] = useState("APPROVED");
  const [remarks, setRemarks] = useState("");
  const [savingDecision, setSavingDecision] = useState(false);
  const decisionSubmittingRef = useRef(false);
  const [decisionResult, setDecisionResult] = useState(null);

  const [error, setError] = useState("");
  const [detailsError, setDetailsError] = useState("");
  const [actionError, setActionError] = useState("");
  const [success, setSuccess] = useState("");

  const [refreshCount, setRefreshCount] = useState(0);

  function refreshRequest() {
    setDecisionResult(null);
    setSuccess("");
    setActionError("");
    setRefreshCount((count) => count + 1);
  }

  // -----------------------------------------
  // 1. FETCH DASHBOARD DATA
  // -----------------------------------------

  useEffect(() => {
    let active = true;

    async function loadDashboard() {
      setLoading(true);
      setError("");

      try {
        // Fetch requests and summary together
        const [requestData, summaryData] =
          await Promise.all([
            getOfficerRequests(session.token),
            getOfficerSummary(session.token),
          ]);

        if (!Array.isArray(requestData)) {
          throw new Error("Invalid request data received.");
        }

        if (active) {
          setRequests(requestData);
          setSummary(summaryData);
        }

      } catch (err) {
        if (active) {
          setError(err.message);
        }

      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadDashboard();

    return () => {
      active = false;
    };

  }, [session.token, refreshCount]);

  // -----------------------------------------
  // 2. FETCH SELECTED REQUEST DETAILS
  // -----------------------------------------

  useEffect(() => {
    if (selectedId == null) {
      setSelectedRequest(null);
      return;
    }

    let active = true;

    async function loadDetails() {
      setDetailsLoading(true);
      setDetailsError("");
      setSelectedRequest(null);

      try {
        const data = await getOfficerRequestDetails(
          session.token,
          selectedId
        );

        if (active) {
          setSelectedRequest(data);
        }

      } catch (err) {
        if (active) {
          setDetailsError(err.message);
        }

      } finally {
        if (active) {
          setDetailsLoading(false);
        }
      }
    }

    loadDetails();

    return () => {
      active = false;
    };

  }, [session.token, selectedId, refreshCount]);

  // -----------------------------------------
  // 3. FILTER REQUESTS
  // -----------------------------------------

  const filteredRequests = requests.filter((request) => {
    const keyword = search.toLowerCase().trim();

    const matchesSearch =
      String(request.exitRequestId).includes(keyword) ||
      String(request.loanId).includes(keyword) ||
      String(request.borrowerName || "")
        .toLowerCase()
        .includes(keyword) ||
      String(request.loanReference || "")
        .toLowerCase()
        .includes(keyword);

    const matchesStatus =
      statusFilter === "ALL" ||
      request.status === statusFilter;

    const matchesView =
      activeView === "all" ||
      Number(request.officerId) === Number(session.userId);

    return matchesSearch && matchesStatus && matchesView;
  });

  // -----------------------------------------
  // 4. ASSIGN REQUEST TO CURRENT OFFICER
  // -----------------------------------------

  async function handleAssign(requestId) {
    setAssigning(true);
    setActionError("");
    setSuccess("");

    try {
      await assignRequest(session.token, requestId);

      setSuccess("Request assigned successfully.");
      // Refresh request list, summary and details
      setRefreshCount((count) => count + 1);

    } catch (err) {
      setActionError(err.message);

    } finally {
      setAssigning(false);
    }
  }

  async function handleDecision(event) {
    event.preventDefault();
    if (!selectedRequest || decisionSubmittingRef.current) return;

    const decisionRemarks = remarks.trim();
    if (!["APPROVED", "REJECTED", "MORE_INFO_REQUIRED"].includes(decision)) {
      setActionError("Choose a valid decision.");
      return;
    }
    if (!decisionRemarks) {
      setActionError("Please enter decision remarks.");
      return;
    }
    if (decisionRemarks.length > 1000) {
      setActionError("Decision remarks must be 1000 characters or fewer.");
      return;
    }

    decisionSubmittingRef.current = true;
    setSavingDecision(true);
    setActionError("");
    setSuccess("");
    setDecisionResult(null);

    try {
      const result = await makeOfficerDecision(
        session.token,
        selectedRequest.exitRequestId,
        decision,
        decisionRemarks,
      );

      setDecisionResult(result);
      setSuccess(result?.status
        ? `Request #${result.exitRequestId ?? selectedRequest.exitRequestId} updated to ${result.status}.`
        : "Decision submitted successfully.");
      setRemarks("");
      setDecision("APPROVED");
      setRefreshCount((count) => count + 1);
    } catch (err) {
      setActionError(err.message);
    } finally {
      decisionSubmittingRef.current = false;
      setSavingDecision(false);
    }
  }

  // -----------------------------------------
  // 5. LOGOUT
  // -----------------------------------------

  function handleLogout() {
    onLogout();
    window.location.hash = "#home";
  }

  // -----------------------------------------
  // SUMMARY CARDS
  // -----------------------------------------

  const summaryCards = [
    {
      label: "Total Requests",
      value: summary?.totalRequests ?? 0,
    },
    {
      label: "Pending",
      value: summary?.pending ?? 0,
    },
    {
      label: "Under Review",
      value: summary?.underReview ?? 0,
    },
    {
      label: "Assigned to Me",
      value: summary?.assignedToMe ?? 0,
    },
    {
      label: "Approved",
      value: summary?.approved ?? 0,
    },
    {
      label: "Completed",
      value: summary?.completed ?? 0,
    },
  ];

  const canDecide =
    selectedRequest?.status === "UNDER_REVIEW" &&
    Number(selectedRequest.officerId) === Number(session.userId);

  // =========================================
  // RENDER
  // =========================================

  return (
    <div className="officer-shell">

      <div className="officer-layout">

        {/* SIDEBAR */}

        <aside className="officer-sidebar borrower-sidebar">

          <a href="/" className="dashboard-brand dashboard-brand-light">
            <span className="dashboard-brand-mark" />
            Clear<span>Close</span>
          </a>

          <p className="sidebar-heading officer-sidebar-label">
            Officer Portal
          </p>

          <nav className="sidebar-nav officer-sidebar-nav">

            <button
              onClick={() => setActiveView("all")}
              className={`sidebar-link ${
                activeView === "all"
                  ? "active"
                  : ""
              }`}
            >
              ▦ All Requests
            </button>

            <button
              onClick={() => setActiveView("mine")}
              className={`sidebar-link ${
                activeView === "mine"
                  ? "active"
                  : ""
              }`}
            >
              ◫ Assigned to Me
            </button>

          </nav>

          <div className="officer-sidebar-account">

            <div className="officer-account-card">
              <p className="font-semibold">
                {session.username}
              </p>

              <p>
                {session.role.replaceAll("_", " ")}
              </p>
            </div>

            <button
              onClick={handleLogout}
              className="officer-logout-button"
            >
              Logout
            </button>

          </div>

        </aside>

        {/* MAIN CONTENT */}

        <main className="officer-main dashboard-main">

          {/* HEADER */}

          <div className="officer-page-header">

            <div>
              <p className="dashboard-eyebrow">
                Bank Employee Dashboard
              </p>

              <h2>
                Cooling-Off Requests
              </h2>

              <p className="dashboard-muted">
                Review borrower requests and manage assignments.
              </p>
            </div>

            <button
              onClick={() =>
                setRefreshCount((count) => count + 1)
              }
              disabled={loading}
              className="dashboard-outline-button"
            >
              Refresh ↻
            </button>

          </div>

          {/* LOADING / ERROR */}

          {loading && (
            <p className="mt-8 text-gray-500">
              Loading officer dashboard...
            </p>
          )}

          {error && (
            <p role="alert" className="mt-8 rounded-xl bg-red-50 p-4 text-sm text-red-600">
              {error}
            </p>
          )}

          {!loading && !error && (
            <>

              {/* SUMMARY CARDS */}

              <section className="officer-summary-grid">

                {summaryCards.map((card) => (

                  <div
                    key={card.label}
                    className="officer-summary-card"
                  >
                    <p>
                      {card.label}
                    </p>

                    <h3>
                      {card.value}
                    </h3>
                  </div>

                ))}

              </section>

              {/* REQUEST LIST */}

              <section className="officer-requests-section">

                <div className="officer-requests-heading">

                  <h3>
                    {activeView === "mine"
                      ? "My Assigned Requests"
                      : "Cooling-Off Requests"}
                  </h3>

                  <div className="officer-request-filters">

                    <input
                      type="text"
                      placeholder="Search borrower, loan, request..."
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      className="officer-filter-control officer-search-control"
                    />

                    <select
                      value={statusFilter}
                      onChange={(e) =>
                        setStatusFilter(e.target.value)
                      }
                      className="officer-filter-control"
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="PENDING">Pending</option>
                      <option value="UNDER_REVIEW">Under Review</option>
                      <option value="MORE_INFO_REQUIRED">More Info Required</option>
                      <option value="APPROVED">Approved</option>
                      <option value="REJECTED">Rejected</option>
                      <option value="COMPLETED">Completed</option>
                    </select>

                  </div>
                </div>

                {/* EMPTY STATE */}

                {filteredRequests.length === 0 && (
                  <div className="officer-empty-state">
                    <p>
                      No requests found
                    </p>

                    <p>
                      No cooling-off requests match your current filters.
                    </p>
                  </div>
                )}

                {/* REQUEST CARDS */}

                <div className="officer-request-grid">

                  {filteredRequests.map((request) => (

                    <article
                      key={request.exitRequestId}
                      className="officer-request-card"
                    >

                      <div className="flex flex-wrap items-start justify-between gap-3">

                        <div>
                          <p className="text-xs text-gray-500">
                            Request #{request.exitRequestId}
                          </p>

                          <h4 className="mt-2 text-xl font-semibold">
                            {request.borrowerName}
                          </h4>

                          <p className="mt-1 text-sm text-gray-500">
                            {request.loanReference}
                          </p>
                        </div>

                        <StatusBadge status={request.status} />

                      </div>

                      <div className="mt-6 grid grid-cols-2 gap-5">

                        <DetailField
                          label="Loan ID"
                          value={request.loanId}
                        />

                        <DetailField
                          label="Borrower ID"
                          value={request.borrowerId}
                        />

                        <DetailField
                          label="Outstanding"
                          value={formatCurrency(
                            request.outstandingAmount
                          )}
                        />

                        <DetailField
                          label="Settlement Quote"
                          value={formatCurrency(
                            request.settlementAmount
                          )}
                        />

                        <DetailField
                          label="Request Date"
                          value={formatDate(
                            request.requestDate
                          )}
                        />

                        <DetailField
                          label="Assigned Officer"
                          value={
                            request.officerId == null
                              ? "Unassigned"
                              : `Officer #${request.officerId}`
                          }
                        />

                      </div>

                      <button
                        onClick={() => {
                          setSelectedId(request.exitRequestId);
                          setDecisionResult(null);
                          setSuccess("");
                          setActionError("");
                        }}
                        className="dashboard-primary-button full-width officer-request-action"
                      >
                        View Request Details →
                      </button>

                    </article>

                  ))}

                </div>

              </section>

            </>
          )}

          {/* SELECTED REQUEST DETAILS */}

          {selectedId != null && (
            <div
              className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
              role="presentation"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget && !savingDecision && !assigning) {
                  setSelectedId(null);
                  setDecisionResult(null);
                  setSuccess("");
                  setActionError("");
                }
              }}
            >
              <section
                role="dialog"
                aria-modal="true"
                aria-labelledby="officer-request-details-title"
                className="officer-details-panel !mt-0 max-h-[90vh] w-full max-w-5xl overflow-y-auto"
              >

              <div className="flex items-center justify-between gap-4">

                <h3 id="officer-request-details-title" className="text-xl font-semibold">
                  Request Details
                </h3>

                <button
                  type="button"
                  disabled={detailsLoading || savingDecision || assigning}
                  onClick={refreshRequest}
                  className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-semibold disabled:opacity-50"
                >
                  Refresh Status
                </button>

                <button
                  disabled={savingDecision || assigning}
                  onClick={() => {
                    setSelectedId(null);
                    setDecisionResult(null);
                    setSuccess("");
                    setActionError("");
                  }}
                  className="text-sm text-gray-500 hover:text-black"
                >
                  Close ✕
                </button>

              </div>

              {detailsLoading && (
                <p className="mt-5 text-sm text-gray-500">
                  Loading request details...
                </p>
              )}

              {detailsError && (
                <p role="alert" className="mt-5 text-sm text-red-600">
                  {detailsError}
                </p>
              )}

              {decisionResult && (
                <section role="status" className="mt-5 rounded-xl border border-green-200 bg-green-50 p-5 text-green-950">
                  <h3 className="font-semibold">
                    {decisionResult.status === "APPROVED"
                      ? "Cooling-Off Exit Request Approved Successfully"
                      : "Cooling-Off Exit Request Updated"}
                  </h3>
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <DetailField label="Request ID" value={decisionResult.exitRequestId} />
                    <DetailField label="Updated Status" value={decisionResult.status} />
                    <DetailField label="Decision Date" value={formatDate(decisionResult.decidedAt)} />
                    <DetailField label="Decision Remarks" value={decisionResult.remarks} />
                  </div>
                </section>
              )}

              {selectedRequest && !detailsLoading && (
                <>

                  <div className="mt-6 flex items-center gap-3">
                    <StatusBadge
                      status={selectedRequest.status}
                    />

                    <span className="text-sm text-gray-500">
                      Request #{selectedRequest.exitRequestId}
                    </span>
                  </div>

                  {selectedRequest.status === "APPROVED" && (
                    <p className="mt-4 rounded-xl bg-green-50 p-4 text-sm text-green-800">
                      Exit request approved. Settlement acceptance, payment verification and final exit completion may still be pending.
                    </p>
                  )}
                  {selectedRequest.status === "REJECTED" && (
                    <p className="mt-4 rounded-xl bg-red-50 p-4 text-sm text-red-800">
                      This cooling-off exit request was rejected.
                    </p>
                  )}
                  {selectedRequest.status === "MORE_INFO_REQUIRED" && (
                    <p className="mt-4 rounded-xl bg-orange-50 p-4 text-sm text-orange-900">
                      More information is required for this request.
                    </p>
                  )}

                  <RequestInformation
                    key={selectedRequest.exitRequestId}
                    token={session.token}
                    requestId={selectedRequest.exitRequestId}
                    status={selectedRequest.status}
                    officer
                    onRefresh={refreshRequest}
                  />
                  {selectedRequest.status === "COMPLETED" && (
                    <p className="mt-4 rounded-xl bg-green-50 p-4 text-sm text-green-800">
                      This cooling-off exit request is completed.
                    </p>
                  )}

                  <div className="mt-7 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">

                    <DetailField
                      label="Borrower"
                      value={selectedRequest.borrowerName}
                    />

                    <DetailField
                      label="Borrower ID"
                      value={selectedRequest.borrowerId}
                    />

                    <DetailField
                      label="Loan ID"
                      value={selectedRequest.loanId}
                    />

                    <DetailField
                      label="Loan Reference"
                      value={selectedRequest.loanReference}
                    />

                    <DetailField
                      label="Original Principal"
                      value={formatCurrency(
                        selectedRequest.principalAmount
                      )}
                    />

                    <DetailField
                      label="Outstanding Amount"
                      value={formatCurrency(
                        selectedRequest.outstandingAmount
                      )}
                    />

                    <DetailField
                      label="Interest Rate"
                      value={`${selectedRequest.interestRate}%`}
                    />

                    <DetailField
                      label="Settlement Amount"
                      value={formatCurrency(
                        selectedRequest.settlementAmount
                      )}
                    />

                    <DetailField
                      label="Quote ID"
                      value={selectedRequest.quoteId}
                    />

                    <DetailField
                      label="Quote Status"
                      value={
                        selectedRequest.quoteStatus ||
                        "No quote generated"
                      }
                    />

                    <DetailField
                      label="Quote Valid Until"
                      value={formatDate(
                        selectedRequest.quoteValidUntil
                      )}
                    />

                    <DetailField
                      label="Cooling-Off Expiry"
                      value={formatDate(
                        selectedRequest.expiryDate
                      )}
                    />

                    <DetailField
                      label="Request Date"
                      value={formatDate(
                        selectedRequest.requestDate
                      )}
                    />

                    <DetailField
                      label="Assigned Officer ID"
                      value={
                        selectedRequest.officerId ??
                        "Unassigned"
                      }
                    />

                  </div>

                  {/* EXIT REASON */}

                  <div className="officer-reason-panel">

                    <p className="text-xs font-medium text-gray-500">
                      Borrower's Exit Reason
                    </p>

                    <p className="mt-2 text-sm">
                      {selectedRequest.reason ||
                        "No reason provided"}
                    </p>

                  </div>

                  {/* ASSIGN BUTTON */}

                  {selectedRequest.status === "PENDING" &&
                    selectedRequest.officerId == null && (

                    <button
                      onClick={() =>
                        handleAssign(
                          selectedRequest.exitRequestId
                        )
                      }
                      disabled={assigning}
                      className="dashboard-primary-button officer-assign-button"
                    >
                      {assigning
                        ? "Assigning..."
                        : "Assign to Me →"}
                    </button>

                  )}

                  {Number(selectedRequest.officerId) ===
                    Number(session.userId) && (

                    <p className="mt-6 text-sm font-medium text-green-700">
                      ✓ This request is assigned to you.
                    </p>

                  )}

                  {canDecide && (
                    <form
                      onSubmit={handleDecision}
                      className="officer-decision-form mt-6 rounded-xl border border-lime-200 bg-lime-50 p-5"
                    >
                      <h3 className="text-lg font-semibold">Officer decision</h3>
                      <p className="mt-1 text-sm text-gray-600">
                        Record your review outcome and supporting remarks.
                      </p>
                      <label className="mt-4 block text-sm font-medium" htmlFor="officer-decision">
                        Decision
                      </label>
                      <select
                        id="officer-decision"
                        value={decision}
                        onChange={(event) => setDecision(event.target.value)}
                        className="mt-2 w-full rounded-lg border border-gray-300 bg-white px-3 py-2"
                      >
                        <option value="APPROVED">Approve</option>
                        <option value="REJECTED">Reject</option>
                        <option value="MORE_INFO_REQUIRED">Request more information</option>
                      </select>
                      <label className="mt-4 block text-sm font-medium" htmlFor="officer-remarks">
                        Decision remarks
                      </label>
                      <textarea
                        id="officer-remarks"
                        value={remarks}
                        onChange={(event) => setRemarks(event.target.value.slice(0, 1000))}
                        placeholder="Enter the reason for your decision..."
                        maxLength={1000}
                        required
                        rows={4}
                        className="mt-2 w-full rounded-lg border border-gray-300 bg-white px-3 py-2"
                      />
                      <button
                        type="submit"
                        disabled={savingDecision}
                        className="dashboard-primary-button mt-4"
                      >
                        {savingDecision ? "Saving..." : "Submit decision"}
                      </button>
                      <p className="mt-2 text-right text-xs text-gray-500">
                        {remarks.length}/1000
                      </p>
                    </form>
                  )}

                  {success && (
                    <p role="status" className="mt-4 rounded-lg bg-green-50 p-3 text-sm text-green-700">
                      {success}
                    </p>
                  )}

                  {actionError && (
                    <p role="alert" className="mt-4 text-sm text-red-600">
                      {actionError}
                    </p>
                  )}

                </>
              )}

              </section>
            </div>
          )}

        </main>
      </div>
    </div>
  );
}

export default LoanClosureDashboard;
