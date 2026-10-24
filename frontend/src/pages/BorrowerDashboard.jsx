
import { useEffect, useRef, useState } from "react";
import GrievanceDashboard from "./GrievanceDashboard";
import CollateralReleaseDashboard from "./CollateralReleaseDashboard";
import SettlementPayment from "../components/SettlementPayment";
import RequestInformation from "../components/RequestInformation";
import PreClosure from "../components/PreClosure";
import {
  acceptSettlementQuote,
  checkCoolingOffEligibility,
  generateSettlementQuote,
  getCoolingOffRequestById,
  getMyCoolingOffRequests,
  getMyLoans,
  getSettlementQuoteByRequest,
  submitCoolingOffRequest,
} from "../api";

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

function formatQuoteCurrency(amount) {
  if (amount == null) return "N/A";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
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

function formatDateTime(date) {
  if (!date) return "N/A";
  const [datePart, timePart] = String(date).split("T");
  const formattedDate = formatDate(datePart);
  return timePart ? `${formattedDate} ${timePart.slice(0, 5)}` : formattedDate;
}

// -----------------------------------------
// FORMAT LOAN TYPE
// -----------------------------------------

function formatLoanType(type) {
  if (!type || !String(type).trim()) return "Unspecified Loan Type";

  return String(type).trim()
    .replace(/[_-]+/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function loanTypeKey(type) {
  return String(type || "").trim().toUpperCase().replace(/[\s-]+/g, "_") || "UNSPECIFIED";
}

function outstandingPaise(loan) {
  const amount = Number(loan.outstandingAmount ?? 0);
  return Number.isFinite(amount) ? Math.round(amount * 100) : 0;
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

const requestStatusStyles = {
  PENDING: "bg-yellow-100 text-yellow-800",
  UNDER_REVIEW: "bg-blue-100 text-blue-800",
  MORE_INFO_REQUIRED: "bg-orange-100 text-orange-800",
  APPROVED: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-gray-100 text-gray-700",
  EXPIRED: "bg-gray-100 text-gray-700",
  COMPLETED: "bg-emerald-100 text-emerald-800",
};

const requestStatusLabels = {
  PENDING: "Pending",
  UNDER_REVIEW: "Under Review",
  MORE_INFO_REQUIRED: "More Information Required",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
  COMPLETED: "Completed",
};

const requestStatusMessages = {
  PENDING: "Your exit request has been submitted and is awaiting review.",
  UNDER_REVIEW: "A bank officer is reviewing your request.",
  MORE_INFO_REQUIRED: "The officer needs more information. Read the remarks below and submit your response for review.",
  APPROVED: "Your cooling-off exit request has been approved. Settlement and payment processing are still required.",
  REJECTED: "Your cooling-off exit request was rejected.",
  COMPLETED: "Your cooling-off exit process has been completed.",
};

function RequestStatusBadge({ status }) {
  const label = requestStatusLabels[status] || status?.replaceAll("_", " ") || "N/A";
  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${requestStatusStyles[status] || "bg-gray-100 text-gray-700"}`}>
      {label}
    </span>
  );
}

// -----------------------------------------
// LOAN DETAIL FIELD
// -----------------------------------------

function DetailField({ label, value }) {
  return (
    <div className="min-w-0">
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
  const [preClosureLoanId, setPreClosureLoanId] = useState(null);
  const [coolingOffLoanId, setCoolingOffLoanId] = useState("");
  const coolingOffDetailsRef = useRef(null);
  const requestSubmittingRef = useRef(false);

  const [loans, setLoans] = useState([]);

  const [loanTypeFilter, setLoanTypeFilter] = useState("ALL");
  const [detailsLoanId, setDetailsLoanId] = useState(null);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  const [search, setSearch] = useState("");

  const [refreshCount, setRefreshCount] = useState(0);
  const [eligibilityResults, setEligibilityResults] = useState({});
  const [eligibilityLoading, setEligibilityLoading] = useState({});
  const [eligibilityErrors, setEligibilityErrors] = useState({});
  const [requestLoanId, setRequestLoanId] = useState(null);
  const [requestReason, setRequestReason] = useState("");
  const [requestSubmitting, setRequestSubmitting] = useState(false);
  const [requestErrors, setRequestErrors] = useState({});
  const [requestResults, setRequestResults] = useState({});
  const [coolingOffRequests, setCoolingOffRequests] = useState([]);
  const [requestListLoading, setRequestListLoading] = useState(true);
  const [requestListError, setRequestListError] = useState("");
  const [requestRefreshCount, setRequestRefreshCount] = useState(0);
  const [selectedRequestId, setSelectedRequestId] = useState(null);
  const [selectedRequestDetails, setSelectedRequestDetails] = useState(null);
  const [requestDetailsLoading, setRequestDetailsLoading] = useState(false);
  const [requestDetailsError, setRequestDetailsError] = useState("");
  const [refreshingRequestId, setRefreshingRequestId] = useState(null);
  const [requestRefreshErrors, setRequestRefreshErrors] = useState({});
  const [settlementQuote, setSettlementQuote] = useState(null);
  const [settlementQuoteLoading, setSettlementQuoteLoading] = useState(false);
  const [settlementQuoteLoaded, setSettlementQuoteLoaded] = useState(false);
  const [settlementQuoteError, setSettlementQuoteError] = useState("");
  const [quoteGenerating, setQuoteGenerating] = useState(false);
  const [acceptanceConfirmationOpen, setAcceptanceConfirmationOpen] = useState(false);
  const [acceptanceAcknowledged, setAcceptanceAcknowledged] = useState(false);
  const [acceptingQuote, setAcceptingQuote] = useState(false);
  const [acceptanceError, setAcceptanceError] = useState("");
  const [quoteRefreshError, setQuoteRefreshError] = useState("");
  const acceptanceSubmittingRef = useRef(false);

  async function handleCheckEligibility(loanId) {
    const loanKey = String(loanId);
    setEligibilityLoading((current) => ({ ...current, [loanKey]: true }));
    setEligibilityErrors((current) => ({ ...current, [loanKey]: "" }));
    setEligibilityResults((current) => {
      const next = { ...current };
      delete next[loanKey];
      return next;
    });

    try {
      const result = await checkCoolingOffEligibility(session.token, loanId);
      setEligibilityResults((current) => ({ ...current, [loanKey]: result }));
    } catch (eligibilityError) {
      setEligibilityErrors((current) => ({
        ...current,
        [loanKey]: eligibilityError.message,
      }));
    } finally {
      setEligibilityLoading((current) => ({ ...current, [loanKey]: false }));
    }
  }

  async function handleSubmitCoolingOffRequest(event) {
    event.preventDefault();
    if (requestSubmittingRef.current) return;
    const formLoanId = activeMenu === "cooling-off" ? coolingOffLoanId : requestLoanId;
    const loan = loans.find((item) => String(item.loanId) === String(formLoanId));
    const reason = requestReason.trim();
    const loanKey = String(formLoanId);

    if (!loan || !reason || new TextEncoder().encode(reason).length > 1000) {
      setRequestErrors((current) => ({
        ...current,
        [loanKey]: !loan
          ? "Select a loan from your account before submitting."
          : "Enter a reason within the allowed length.",
      }));
      return;
    }

    if (activeMenu === "cooling-off" && (loan.loanStatus !== "ACTIVE" || requestListLoading || requestListError
        || !eligibilityResults[loanKey]?.eligible || eligibilityLoading[loanKey]
        || coolingOffRequests.some((request) => String(request.loanId) === loanKey &&
          ["PENDING", "UNDER_REVIEW", "MORE_INFO_REQUIRED", "APPROVED"].includes(request.status)))) {
      setRequestErrors((current) => ({ ...current, [loanKey]: "Refresh eligibility and request status before submitting. If a request is already active, continue with that request." }));
      return;
    }

    requestSubmittingRef.current = true;
    setRequestSubmitting(true);
    setRequestErrors((current) => ({ ...current, [loanKey]: "" }));

    try {
      const result = await submitCoolingOffRequest(session.token, loan.loanId, reason);
      setRequestResults((current) => ({ ...current, [loanKey]: result }));
      setRequestReason("");
      setRequestLoanId(null);
      setCoolingOffRequests((current) => [result, ...current.filter((item) => String(item.exitRequestId) !== String(result.exitRequestId))]);
      if (activeMenu === "cooling-off") setSelectedRequestId(result.exitRequestId);
      setRequestRefreshCount((count) => count + 1);
    } catch (requestError) {
      setRequestErrors((current) => ({ ...current, [loanKey]: requestError.message }));
    } finally {
      requestSubmittingRef.current = false;
      setRequestSubmitting(false);
    }
  }

  useEffect(() => {
    if (activeMenu === "cooling-off" && selectedRequestId != null) {
      coolingOffDetailsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [activeMenu, selectedRequestId]);

  function changeMenu(menu) {
    if (requestSubmitting || acceptingQuote) return;
    setSelectedRequestId(null);
    setRequestLoanId(null);
    setAcceptanceConfirmationOpen(false);
    setActiveMenu(menu);
  }

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

  useEffect(() => {
    let active = true;

    async function loadCoolingOffRequests() {
      setRequestListLoading(true);
      setRequestListError("");
      try {
        const data = await getMyCoolingOffRequests(session.token);
        const requestList = Array.isArray(data)
          ? data
          : Array.isArray(data?.requests)
            ? data.requests
            : null;
        if (!requestList) throw new Error("Invalid cooling-off request response from server.");
        if (active) {
          setCoolingOffRequests(requestList);
          setRequestResults((current) => {
            const latestByLoan = new Map();
            for (const request of requestList) {
              const key = String(request.loanId);
              const previous = latestByLoan.get(key);
              const requestDate = Date.parse(request.requestDate || request.createdAt || "") || 0;
              const previousDate = Date.parse(previous?.requestDate || previous?.createdAt || "") || 0;
              const newer = requestDate > previousDate || (
                requestDate === previousDate && Number(request.exitRequestId) > Number(previous?.exitRequestId)
              );
              if (!previous || newer) latestByLoan.set(key, request);
            }
            const next = { ...current };
            for (const [key, request] of latestByLoan) {
              if (next[key]) next[key] = request;
            }
            return next;
          });
        }
      } catch (loadError) {
        if (active) setRequestListError(loadError.message);
      } finally {
        if (active) setRequestListLoading(false);
      }
    }

    loadCoolingOffRequests();
    return () => { active = false; };
  }, [session.token, requestRefreshCount]);

  useEffect(() => {
    if (selectedRequestId == null) {
      setSelectedRequestDetails(null);
      return undefined;
    }

    let active = true;
    async function loadRequestDetails() {
      setRequestDetailsLoading(true);
      setRequestDetailsError("");
      setSelectedRequestDetails(null);
      try {
        const data = await getCoolingOffRequestById(session.token, selectedRequestId);
        if (active) {
          setSelectedRequestDetails(data);
          setCoolingOffRequests((current) => {
            const exists = current.some((item) => String(item.exitRequestId) === String(data.exitRequestId));
            return exists
              ? current.map((item) => String(item.exitRequestId) === String(data.exitRequestId) ? data : item)
              : [...current, data];
          });
        }
      } catch (loadError) {
        if (active) setRequestDetailsError(loadError.message);
      } finally {
        if (active) setRequestDetailsLoading(false);
      }
    }

    loadRequestDetails();
    return () => { active = false; };
  }, [session.token, selectedRequestId]);

  async function handleRefreshRequest(requestId) {
    const key = String(requestId);
    setRefreshingRequestId(key);
    setRequestRefreshErrors((current) => ({ ...current, [key]: "" }));
    try {
      const [latest, latestLoans, latestQuote] = await Promise.all([
        getCoolingOffRequestById(session.token, requestId),
        getMyLoans(session.token),
        getSettlementQuoteByRequest(session.token, requestId),
      ]);
      if (!Array.isArray(latestLoans)) throw new Error("Invalid loan response from server.");
      setLoans(latestLoans);
      setCoolingOffRequests((current) => current.map((item) =>
        String(item.exitRequestId) === key ? latest : item,
      ));
      if (String(selectedRequestId) === key) {
        setSelectedRequestDetails(latest);
        setSettlementQuote(latestQuote);
        setSettlementQuoteLoaded(true);
      }
    } catch (refreshError) {
      setRequestRefreshErrors((current) => ({ ...current, [key]: refreshError.message }));
    } finally {
      setRefreshingRequestId(null);
    }
  }

  async function handleGenerateSettlementQuote() {
    if (!selectedRequestDetails?.exitRequestId) return;
    setQuoteGenerating(true);
    setSettlementQuoteError("");
    try {
      const quote = await generateSettlementQuote(
        session.token,
        selectedRequestDetails.exitRequestId,
      );
      setSettlementQuote(quote);
      setSettlementQuoteLoaded(true);
    } catch (quoteError) {
      setSettlementQuoteError(quoteError.message);
    } finally {
      setQuoteGenerating(false);
    }
  }

  async function handleAcceptSettlementQuote() {
    if (acceptanceSubmittingRef.current) return;

    const quoteId = settlementQuote?.quoteId;
    const canAccept = selectedRequestDetails?.status === "APPROVED" &&
      settlementQuote?.quoteStatus === "AVAILABLE" &&
      settlementQuote?.expired === false &&
      quoteId != null;

    if (!canAccept) {
      setAcceptanceError("This quote is not currently available for acceptance.");
      return;
    }
    if (!acceptanceAcknowledged) {
      setAcceptanceError("Confirm that you understand and accept the settlement amount.");
      return;
    }

    acceptanceSubmittingRef.current = true;
    setAcceptingQuote(true);
    setAcceptanceError("");
    setQuoteRefreshError("");

    try {
      const acceptedQuote = await acceptSettlementQuote(session.token, quoteId);
      setSettlementQuote(acceptedQuote);
      setAcceptanceConfirmationOpen(false);

      try {
        const latestQuote = await getSettlementQuoteByRequest(
          session.token,
          acceptedQuote.exitRequestId ?? selectedRequestDetails.exitRequestId,
        );
        if (latestQuote) setSettlementQuote(latestQuote);
      } catch (refreshError) {
        setQuoteRefreshError(`Quote accepted, but refreshing its latest details failed: ${refreshError.message}`);
      }
    } catch (error) {
      setAcceptanceError(error.message);
    } finally {
      acceptanceSubmittingRef.current = false;
      setAcceptingQuote(false);
    }
  }

  useEffect(() => {
    const requestId = selectedRequestDetails?.exitRequestId;
    let active = true;

    if (requestId == null) {
      setSettlementQuote(null);
      setSettlementQuoteLoaded(false);
      setSettlementQuoteError("");
      return () => { active = false; };
    }

    async function loadQuote() {
      setSettlementQuote(null);
      setSettlementQuoteLoaded(false);
      setSettlementQuoteLoading(true);
      setSettlementQuoteError("");
      setAcceptanceError("");
      setQuoteRefreshError("");
      try {
        const quote = await getSettlementQuoteByRequest(session.token, requestId);
        if (active) {
          setSettlementQuote(quote);
          setSettlementQuoteLoaded(true);
        }
      } catch (quoteError) {
        if (active) setSettlementQuoteError(quoteError.message);
      } finally {
        if (active) setSettlementQuoteLoading(false);
      }
    }

    loadQuote();
    return () => { active = false; };
  }, [session.token, selectedRequestDetails?.exitRequestId, selectedRequestDetails?.updatedAt]);

  // -----------------------------------------
  // CALCULATED DASHBOARD VALUES
  // -----------------------------------------

  const totalLoans = loans.length;

  const activeLoans = loans.filter(
    (loan) => String(loan.loanStatus).trim().toUpperCase() === "ACTIVE"
  ).length;

  const totalOutstanding = loans.reduce(
    (sum, loan) =>
      sum + outstandingPaise(loan),
    0
  ) / 100;

  // Derive every group from the product type supplied by the loan API.
  // No product names, account counts or balances are seeded into this view.
  const loanTypeGroups = Array.from(loans.reduce((groups, loan) => {
    const key = loanTypeKey(loan.productType);
    const group = groups.get(key) || {
      key, label: formatLoanType(loan.productType), count: 0, activeCount: 0, outstanding: 0,
    };
    group.count += 1;
    if (String(loan.loanStatus).trim().toUpperCase() === "ACTIVE") group.activeCount += 1;
    group.outstanding += outstandingPaise(loan);
    groups.set(key, group);
    return groups;
  }, new Map()).values()).sort((a, b) => a.label.localeCompare(b.label));
  const detailsLoan = loans.find((loan) => loan.loanId === detailsLoanId);

  // -----------------------------------------
  // SEARCH LOANS
  // -----------------------------------------

  const filteredLoans = loans.filter((loan) => {

    const keyword = search.toLowerCase().trim();

    return (loanTypeFilter === "ALL" || loanTypeKey(loan.productType) === loanTypeFilter) && (
      String(loan.loanId).includes(keyword) ||
      String(loan.loanReference || "")
        .toLowerCase()
        .includes(keyword) ||
      String(loan.productType || "")
        .toLowerCase()
        .includes(keyword) ||
      formatLoanType(loan.productType).toLowerCase()
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
    { id: "cooling-off", icon: "↶", label: "Cooling-off Exit" },
    { id: "preclosure", icon: "↗", label: "Pre-closure" },
    { id: "overview", icon: "▦", label: "Overview" },
    { id: "my-loans", icon: "◫", label: "My Loans" },
  ];

  const coolingPageLoans = loans.filter((loan) => loan.loanStatus === "ACTIVE");
  const coolingPageLoan = coolingPageLoans.find((loan) => String(loan.loanId) === coolingOffLoanId);
  const coolingPageEligibility = eligibilityResults[coolingOffLoanId];
  const coolingPageActiveRequest = coolingOffRequests.find((request) => String(request.loanId) === coolingOffLoanId &&
    ["PENDING", "UNDER_REVIEW", "MORE_INFO_REQUIRED", "APPROVED"].includes(request.status));
  const coolingPageRequests = [...coolingOffRequests].sort((a, b) => Number(b.exitRequestId) - Number(a.exitRequestId));
  const coolingReasonBytes = new TextEncoder().encode(requestReason.trim()).length;

  // Reuse the existing quote, information and payment details inline on the new page.
  const coolingOffDetails = selectedRequestId != null && (
        <div
          ref={coolingOffDetailsRef}
          className={activeMenu === "cooling-off" ? "mt-6" : "fixed inset-0 z-[55] flex items-center justify-center bg-black/50 p-4"}
          role="presentation"
          onMouseDown={(event) => {
            if (activeMenu !== "cooling-off" && event.target === event.currentTarget) setSelectedRequestId(null);
          }}
        >
          <section
            role={activeMenu === "cooling-off" ? "region" : "dialog"}
            aria-modal={activeMenu === "cooling-off" ? undefined : true}
            aria-labelledby="exit-request-details-title"
            className={activeMenu === "cooling-off" ? "w-full rounded-2xl border border-gray-200 bg-white p-6 sm:p-8" : "max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="dashboard-eyebrow">Cooling-Off Exit</p>
                <h2 id="exit-request-details-title" className="mt-2 text-2xl font-semibold">
                  Exit Request Details
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setSelectedRequestId(null)}
                aria-label="Close request details"
                className="rounded-full px-3 py-2 text-xl text-gray-500 hover:bg-gray-100 hover:text-gray-900"
              >
                ×
              </button>
            </div>

            {requestListError && (
              <p role="alert" className="mt-5 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                {requestListError}
              </p>
            )}

            {selectedRequestDetails && (() => {
              const loanRequests = coolingOffRequests
                .filter((request) => String(request.loanId) === String(selectedRequestDetails.loanId))
                .sort((a, b) => {
                  const dateA = Date.parse(a.requestDate || a.createdAt || "") || 0;
                  const dateB = Date.parse(b.requestDate || b.createdAt || "") || 0;
                  if (dateA !== dateB) return dateB - dateA;
                  return Number(b.exitRequestId) - Number(a.exitRequestId);
                });
              const statusMessage = requestStatusMessages[selectedRequestDetails.status];
              const quoteStatus = settlementQuote?.quoteStatus;
              const quoteIsAcceptedOrPaid = ["ACCEPTED", "PAID"].includes(quoteStatus);
              const quoteIsExpired = !quoteIsAcceptedOrPaid && (
                quoteStatus === "EXPIRED" || (quoteStatus === "AVAILABLE" && settlementQuote?.expired === true)
              );
              const canGenerateFirstQuote = selectedRequestDetails.status === "APPROVED";
              const canGenerateQuote = settlementQuoteLoaded && !settlementQuoteError && !quoteIsAcceptedOrPaid && (
                (!settlementQuote && canGenerateFirstQuote) ||
                (settlementQuote && quoteIsExpired && selectedRequestDetails.status === "APPROVED")
              );
              const canAcceptQuote = selectedRequestDetails.status === "APPROVED" &&
                settlementQuote?.quoteStatus === "AVAILABLE" &&
                settlementQuote?.expired === false &&
                settlementQuote?.quoteId != null;

              return (
                <>
                  {loanRequests.length > 1 && (
                    <div className="mt-6">
                      <p className="mb-2 text-sm font-medium text-gray-700">Other requests for this loan</p>
                      <div className="flex flex-wrap gap-2">
                        {loanRequests.map((request) => (
                          <button
                            key={request.exitRequestId}
                            type="button"
                            onClick={() => setSelectedRequestId(request.exitRequestId)}
                            className={`rounded-lg border px-3 py-2 text-sm ${
                              String(request.exitRequestId) === String(selectedRequestId)
                                ? "border-green-700 bg-green-50 text-green-900"
                                : "border-gray-200 text-gray-700 hover:bg-gray-50"
                            }`}
                          >
                            #{request.exitRequestId} · {requestStatusLabels[request.status] || request.status || "N/A"}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-gray-50 p-4">
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium text-gray-700">Request Status</span>
                      <RequestStatusBadge status={selectedRequestDetails.status} />
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRefreshRequest(selectedRequestId)}
                      disabled={refreshingRequestId === String(selectedRequestId)}
                      className="rounded-lg border border-green-700 bg-white px-4 py-2 text-sm font-semibold text-green-800 hover:bg-green-50 disabled:opacity-60"
                    >
                      {refreshingRequestId === String(selectedRequestId) ? "Refreshing..." : "Refresh Status"}
                    </button>
                  </div>

                  {requestDetailsLoading && (
                    <p className="mt-4 text-sm text-gray-500">Refreshing request details...</p>
                  )}
                  {(requestDetailsError || requestRefreshErrors[String(selectedRequestId)]) && (
                    <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                      {requestDetailsError || requestRefreshErrors[String(selectedRequestId)]}
                    </p>
                  )}

                  {statusMessage && (
                    <p className="mt-4 rounded-lg bg-green-50 p-4 text-sm text-green-900">
                      {statusMessage}
                    </p>
                  )}

                  <RequestInformation
                    key={selectedRequestDetails.exitRequestId}
                    token={session.token}
                    requestId={selectedRequestDetails.exitRequestId}
                    status={selectedRequestDetails.status}
                    onSubmitted={(updated) => {
                      setSelectedRequestDetails(updated);
                      setCoolingOffRequests((current) => current.map((request) =>
                        String(request.exitRequestId) === String(updated.exitRequestId) ? updated : request,
                      ));
                      setRequestRefreshCount((count) => count + 1);
                    }}
                  />

                  <div className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                    <DetailField label="Exit Request ID" value={selectedRequestDetails.exitRequestId} />
                    <DetailField label="Loan ID" value={selectedRequestDetails.loanId} />
                    <DetailField label="Loan Reference" value={selectedRequestDetails.loanReference} />
                    <DetailField label="Loan Status" value={loans.find((loan) => String(loan.loanId) === String(selectedRequestDetails.loanId))?.loanStatus} />
                    <DetailField label="Outstanding Amount" value={formatQuoteCurrency(loans.find((loan) => String(loan.loanId) === String(selectedRequestDetails.loanId))?.outstandingAmount)} />
                    <DetailField label="Closure Date" value={formatDate(loans.find((loan) => String(loan.loanId) === String(selectedRequestDetails.loanId))?.closureDate)} />
                    <DetailField label="Reason for Exit" value={selectedRequestDetails.reason} />
                    <DetailField label="Request Date" value={formatDate(selectedRequestDetails.requestDate || selectedRequestDetails.createdAt)} />
                    <DetailField label="Cooling-Off Expiry Date" value={formatDate(selectedRequestDetails.expiryDate)} />
                    <DetailField label="Assigned Officer ID" value={selectedRequestDetails.officerId} />
                    <DetailField label="Decision Date" value={formatDate(selectedRequestDetails.decidedAt)} />
                    <DetailField label="Last Updated Date" value={formatDate(selectedRequestDetails.updatedAt)} />
                  </div>

                  <section className="mt-8 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm sm:p-6">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="dashboard-eyebrow">Settlement Quote</p>
                        <h3 className="mt-2 text-xl font-semibold">
                          {settlementQuote ? `Settlement Quote — #${settlementQuote.quoteId ?? "N/A"}` : "Settlement Quote"}
                        </h3>
                      </div>
                      {settlementQuote?.quoteStatus && (
                        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
                          quoteStatus === "ACCEPTED" || quoteStatus === "PAID"
                            ? "bg-green-100 text-green-800"
                            : quoteIsExpired
                              ? "bg-red-100 text-red-800"
                              : "bg-blue-100 text-blue-800"
                        }`}>
                          {quoteStatus}
                        </span>
                      )}
                    </div>

                    {settlementQuoteLoading && (
                      <p className="mt-4 text-sm text-gray-500">Loading settlement quote...</p>
                    )}
                    {settlementQuoteError && (
                      <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                        {settlementQuoteError}
                      </p>
                    )}
                    {!settlementQuoteLoading && !settlementQuoteError && settlementQuoteLoaded && !settlementQuote && (
                      <p className="mt-4 text-sm text-gray-600">No settlement quote generated yet.</p>
                    )}

                    {settlementQuote && (
                      <>
                        {quoteIsExpired && (
                          <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
                            <strong>Quote Expired.</strong> This quote is no longer valid. You can request a new quote if the approved exit request is still eligible.
                          </p>
                        )}
                        {quoteStatus === "PAID" && (
                          <p className="mt-4 rounded-lg bg-green-50 p-3 text-sm text-green-900">
                            This settlement quote has been paid.
                          </p>
                        )}
                        {(quoteStatus === "ACCEPTED" || quoteStatus === "PAID") && (
                          <section className="mt-4 rounded-xl border border-green-200 bg-green-50 p-4 text-green-950">
                            <h4 className="font-semibold">Settlement Quote Accepted Successfully</h4>
                            <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                              <div><dt className="font-medium">Quote ID</dt><dd>{settlementQuote.quoteId ?? "N/A"}</dd></div>
                              <div><dt className="font-medium">Loan Reference</dt><dd>{selectedRequestDetails.loanReference || "N/A"}</dd></div>
                              <div><dt className="font-medium">Quote Status</dt><dd>{settlementQuote.quoteStatus}</dd></div>
                              <div><dt className="font-medium">Accepted At</dt><dd>{formatDateTime(settlementQuote.acceptedAt)}</dd></div>
                            </dl>
                            <div className="mt-4 rounded-lg bg-white/80 p-3">
                              <p className="text-sm font-medium">Total Accepted Amount</p>
                              <p className="mt-1 text-xl font-bold">{formatQuoteCurrency(settlementQuote.totalAmount)}</p>
                            </div>
                            <SettlementPayment
                              key={settlementQuote.quoteId}
                              token={session.token}
                              quoteId={settlementQuote.quoteId}
                              approved={selectedRequestDetails.status === "APPROVED"}
                              username={session.username}
                              onRefresh={() => handleRefreshRequest(selectedRequestDetails.exitRequestId)}
                            />
                          </section>
                        )}
                        <dl className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                          <DetailField label="Quote ID" value={settlementQuote.quoteId} />
                          <DetailField label="Loan ID" value={settlementQuote.loanId} />
                          <DetailField label="Exit Request ID" value={settlementQuote.exitRequestId} />
                          <DetailField label="Quote Type" value={settlementQuote.quoteType} />
                          <DetailField label="Principal Amount" value={formatQuoteCurrency(settlementQuote.principalAmount)} />
                          <DetailField label="Interest Amount" value={formatQuoteCurrency(settlementQuote.interestAmount)} />
                          <DetailField label="Processing Fee" value={formatQuoteCurrency(settlementQuote.chargesAmount)} />
                          <DetailField label="Tax Amount" value={formatQuoteCurrency(settlementQuote.taxAmount)} />
                          <DetailField label="Quote Generated Date" value={formatDateTime(settlementQuote.createdAt)} />
                          <DetailField label="Valid Until" value={formatDateTime(settlementQuote.validUntil)} />
                          <DetailField label="Accepted At" value={formatDateTime(settlementQuote.acceptedAt)} />
                        </dl>
                        <div className="mt-5 rounded-xl bg-lime-50 p-4">
                          <p className="text-sm font-medium text-gray-700">Total Settlement Amount</p>
                          <p className="mt-1 text-2xl font-bold text-[#102018]">
                            {formatQuoteCurrency(settlementQuote.totalAmount)}
                          </p>
                        </div>
                      </>
                    )}

                    {canGenerateQuote && (
                      <button
                        type="button"
                        onClick={handleGenerateSettlementQuote}
                        disabled={quoteGenerating}
                        className="mt-5 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {quoteGenerating
                          ? "Generating quote..."
                          : quoteIsExpired
                            ? "Generate New Quote"
                            : "Generate Settlement Quote"}
                      </button>
                    )}

                    {canAcceptQuote && (
                      <button
                        type="button"
                        onClick={() => {
                          setAcceptanceAcknowledged(false);
                          setAcceptanceError("");
                          setAcceptanceConfirmationOpen(true);
                        }}
                        className="ml-0 mt-4 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 sm:ml-3"
                      >
                        Accept Settlement Quote
                      </button>
                    )}
                    {acceptanceError && !acceptanceConfirmationOpen && (
                      <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                        {acceptanceError}
                      </p>
                    )}
                    {quoteRefreshError && (
                      <p role="alert" className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
                        {quoteRefreshError}
                      </p>
                    )}
                  </section>
                </>
              );
            })()}

            {requestDetailsLoading && !selectedRequestDetails && (
              <p className="mt-6 text-gray-500">Loading request details...</p>
            )}
            {requestDetailsError && !selectedRequestDetails && (
              <p role="alert" className="mt-6 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                {requestDetailsError}
              </p>
            )}
          </section>
        </div>
      );

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
          <a href="#noc-dashboard" className="sidebar-link">
            <span aria-hidden="true">▤</span>
            NOC Certificates
          </a>

          {menuItems.map((item) => (

            <button
              key={item.id}
              className={
                activeMenu === item.id
                  ? "sidebar-link active"
                  : "sidebar-link"
              }
              onClick={() => changeMenu(item.id)}
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

        </div>

        <button
          type="button"
          className="officer-logout-button"
          onClick={onLogout}
        >
          Logout
        </button>

      </aside>

      {/* MAIN DASHBOARD */}

      <section className="dashboard-main">

        {/* TOP BAR */}

        <header className="dashboard-topbar" style={{ flexWrap: "wrap", gap: "1rem" }}>

          <div className="dashboard-search" style={{ flex: "1 1 16rem", minWidth: 0 }}>
            <span>⌕</span>

            <input
              aria-label="Search loans"
              disabled={["grievance", "collateral", "preclosure", "cooling-off"].includes(activeMenu)}
              placeholder={["grievance", "collateral", "preclosure", "cooling-off"].includes(activeMenu) ? "Use the selected feature below" : "Search loan ID, reference, type..."}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <nav aria-label="Borrower features" className="flex shrink-0 flex-wrap items-center gap-2">
            {[
              { id: "grievance", label: "Grievance" },
              { id: "collateral", label: "Collateral" },
            ].map((feature) => (
              <button
                key={feature.id}
                type="button"
                aria-pressed={activeMenu === feature.id}
                onClick={() => changeMenu(feature.id)}
                className={
                  "rounded-full border px-4 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-lime-700 " +
                  (activeMenu === feature.id
                    ? "border-black bg-black text-white"
                    : "border-gray-200 bg-white text-gray-700 hover:border-lime-600 hover:bg-lime-50")
                }
              >
                {feature.label}
              </button>
            ))}
          </nav>

          <div className="topbar-actions">
            <div className="topbar-avatar">
              {session.username
                ?.slice(0, 2)
                .toUpperCase()}
            </div>
          </div>

        </header>

        <div className="dashboard-content">
          {activeMenu === "cooling-off" ? (
            <section className="min-w-0 space-y-6" aria-label="Cooling-off exit">
              <header className="flex flex-wrap items-start justify-between gap-4">
                <div><p className="dashboard-eyebrow">Loan Closure</p>
                  <h2 className="mt-2 text-3xl font-semibold">Cooling-off Exit</h2>
                  <p className="dashboard-muted mt-2">Exit a recently disbursed loan within its cooling-off period.</p>
                </div>
                <button type="button" className="dashboard-outline-button"
                  disabled={loading || requestListLoading || requestSubmitting || acceptingQuote || refreshingRequestId != null}
                  onClick={() => {
                    setRefreshCount((count) => count + 1);
                    setRequestRefreshCount((count) => count + 1);
                    if (coolingPageLoan) handleCheckEligibility(coolingPageLoan.loanId);
                    if (selectedRequestId != null) handleRefreshRequest(selectedRequestId);
                  }}>Refresh Status</button>
              </header>
              <div className="rounded-2xl bg-[#24351c] p-6 text-white">
                <p className="font-semibold">Eligibility → Request → Officer approval → Quotation → Acceptance → Payment → Closure</p>
                <p className="mt-2 text-sm">Review the settlement amount, processing fee and payment deadline before accepting your quotation.</p>
              </div>

              <form onSubmit={handleSubmitCoolingOffRequest} className="space-y-4 rounded-2xl border border-gray-200 bg-white p-6">
                <h3 className="text-xl font-semibold">Request Cooling-off Exit</h3>
                {loading && <p>Loading your loans...</p>}
                {error && <p role="alert" className="text-red-700">{error}</p>}
                {!loading && !error && !coolingPageLoans.length && <p>No active loans are available for a new request.</p>}
                <label className="block text-sm font-semibold">Loan
                  <select required value={coolingOffLoanId} disabled={loading || requestSubmitting || acceptingQuote}
                    className="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3"
                    onChange={(event) => {
                      const id = event.target.value;
                      setCoolingOffLoanId(id); setRequestReason(""); setSelectedRequestId(null);
                      setRequestErrors((current) => ({ ...current, [id]: "" }));
                      if (id) handleCheckEligibility(id);
                    }}>
                    <option value="">Select an active loan</option>
                    {coolingPageLoans.map((loan) => <option key={loan.loanId} value={loan.loanId}>
                      {formatLoanType(loan.productType)} · Loan #{loan.loanId} · {formatCurrency(loan.outstandingAmount)}
                    </option>)}
                  </select>
                </label>
                {coolingPageLoan && <>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <DetailField label="Loan Reference" value={coolingPageLoan.loanReference} />
                    <DetailField label="Outstanding Amount" value={formatCurrency(coolingPageLoan.outstandingAmount)} />
                    <DetailField label="Cooling-off Duration" value={`${coolingPageLoan.coolingOffDays ?? 0} days`} />
                    <DetailField label="Cooling-off Expiry" value={formatDateTime(coolingPageEligibility?.expiryDate ?? coolingPageLoan.coolingOffExpiry)} />
                  </div>
                  <button type="button" disabled={Boolean(eligibilityLoading[coolingOffLoanId]) || requestSubmitting}
                    onClick={() => handleCheckEligibility(coolingPageLoan.loanId)}
                    className="rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 disabled:opacity-50">
                    {eligibilityLoading[coolingOffLoanId] ? "Checking eligibility..." : "Check Eligibility"}
                  </button>
                </>}
                {eligibilityErrors[coolingOffLoanId] && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{eligibilityErrors[coolingOffLoanId]}</p>}
                {coolingPageEligibility && !eligibilityLoading[coolingOffLoanId] && <div
                  className={`rounded-xl p-4 ${coolingPageEligibility.eligible ? "bg-green-50 text-green-900" : "bg-amber-50 text-amber-900"}`}>
                  <p className="font-semibold">{coolingPageEligibility.eligible ? "Eligible for Cooling-off Exit" : "Not Eligible for a New Cooling-off Request"}</p>
                  <p className="mt-2 text-sm">{coolingPageEligibility.reason}</p>
                </div>}
                {coolingPageActiveRequest && <div className="rounded-xl bg-blue-50 p-4 text-sm text-blue-900">
                  <p>Request #{coolingPageActiveRequest.exitRequestId} is already active for this loan. Continue with the existing request.</p>
                  <button type="button" className="mt-2 font-semibold underline" disabled={requestSubmitting}
                    onClick={() => setSelectedRequestId(coolingPageActiveRequest.exitRequestId)}>View Existing Request</button>
                </div>}
                <label className="block text-sm font-semibold">Reason for exit
                  <textarea required rows={3} maxLength={1000} value={requestReason} disabled={requestSubmitting}
                    onChange={(event) => setRequestReason(event.target.value)}
                    className="mt-2 w-full rounded-xl border border-gray-300 p-3" placeholder="Tell us why you want to exit this loan" />
                </label>
                {coolingReasonBytes > 1000 && <p role="alert" className="text-sm text-red-700">Shorten your reason; it exceeds the allowed length.</p>}
                {requestErrors[coolingOffLoanId] && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{requestErrors[coolingOffLoanId]}</p>}
                <p className="text-sm text-gray-500">Eligibility is confirmed on submission. A loan cannot have active cooling-off and pre-closure requests together.</p>
                <button type="submit" className="rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={requestSubmitting || acceptingQuote || loading || requestListLoading || Boolean(requestListError) || !coolingPageLoan || !requestReason.trim()
                    || coolingReasonBytes > 1000 || !coolingPageEligibility?.eligible || Boolean(eligibilityLoading[coolingOffLoanId]) || Boolean(coolingPageActiveRequest)}>
                  {requestSubmitting ? "Submitting..." : "Submit Cooling-off Exit Request"}
                </button>
              </form>

              <section aria-label="Your cooling-off requests">
                <h3 className="mb-4 text-xl font-semibold">Your Cooling-off Exit Requests</h3>
                {requestListLoading && <p>Loading requests...</p>}
                {requestListError && <p role="alert" className="mb-4 rounded-xl bg-red-50 p-4 text-red-700">{requestListError}</p>}
                {!requestListLoading && !requestListError && !coolingPageRequests.length && <p className="rounded-xl bg-white p-6">No cooling-off exit requests submitted yet.</p>}
                <div className="grid gap-4 lg:grid-cols-2">{coolingPageRequests.map((request) => <article key={request.exitRequestId}
                  className="min-w-0 rounded-2xl border border-gray-200 bg-white p-5">
                  <div className="flex flex-wrap items-center justify-between gap-3"><h4 className="font-semibold">Request #{request.exitRequestId} · Loan #{request.loanId}</h4>
                    <RequestStatusBadge status={request.status} /></div>
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <DetailField label="Loan Reference" value={request.loanReference} />
                    <DetailField label="Request Date" value={formatDateTime(request.requestDate || request.createdAt)} />
                    <DetailField label="Assigned Officer" value={request.officerId ?? "Unassigned"} />
                    <DetailField label="Cooling-off Expiry" value={formatDateTime(request.expiryDate)} />
                  </div>
                  <button type="button" disabled={requestSubmitting || acceptingQuote}
                    className="mt-4 w-full rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 disabled:opacity-50"
                    onClick={() => setSelectedRequestId(request.exitRequestId)}>View Request Details</button>
                </article>)}</div>
              </section>
              {coolingOffDetails}
            </section>
          ) : activeMenu === "preclosure" ? (
            <PreClosure session={session} loans={loans} loansLoading={loading} loansError={error}
              initialLoanId={preClosureLoanId} onLoansRefresh={() => setRefreshCount((count) => count + 1)} />
          ) : activeMenu === "grievance" ? (
            <GrievanceDashboard session={session} />
          ) : activeMenu === "collateral" ? (
            <CollateralReleaseDashboard session={session} />
          ) : (
            <>


          {/* HEADING */}

          <div className="dashboard-intro">

            <div>
              <p className="dashboard-eyebrow">
                {greeting}, {session.username}
              </p>

              <h1>
                {activeMenu === "my-loans" ? "My Loans" : "Your Loan Dashboard"}
              </h1>

              <p className="dashboard-muted">
                View and manage your existing loan accounts.
              </p>
            </div>

            <button
              className="dashboard-outline-button"
              onClick={() => {
                setRefreshCount((count) => count + 1);
                setRequestRefreshCount((count) => count + 1);
              }}
              disabled={loading || requestListLoading}
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

              {activeMenu === "overview" && (
                <>
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

              <section className="mt-8" aria-labelledby="loan-types-heading">
                <h2 id="loan-types-heading" className="text-xl font-semibold">Loans by Type</h2>
                <p className="mt-2 text-sm text-gray-600">Account counts and outstanding balances across all your loan accounts.</p>
                <div className="mt-5 grid gap-5">
                  {loanTypeGroups.map((group) => (
                    <article key={group.key} className="loan-summary-card loan-type-summary !mt-0">
                      <div className="min-w-0">
                        <p className="dashboard-eyebrow">Loan Type</p>
                        <h3 className="mt-2 text-2xl font-semibold text-white">{group.label}</h3>
                        <p className="mt-2">{group.count} total {group.count === 1 ? "loan" : "loans"} · {group.activeCount} active</p>
                      </div>
                      <div className="loan-summary-amount min-w-0">
                        <span>Total Outstanding</span>
                        <strong>{formatCurrency(group.outstanding / 100)}</strong>
                      </div>
                      <div className="loan-summary-action min-w-0">
                        <button type="button" className="rounded-full border border-white/40 px-5 py-3 text-sm font-semibold text-white hover:bg-white/10"
                          onClick={() => { setLoanTypeFilter(group.key); setSearch(""); setActiveMenu("my-loans"); }}
                          aria-label={`View ${group.label} accounts`}>
                          View Accounts →
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              </section>

                </>
              )}

              {/* MY LOANS */}

              {activeMenu === "my-loans" && (
              <section className="mt-10">

                <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
                  <p className="text-sm text-gray-600">Showing {filteredLoans.length} of {totalLoans} loan accounts</p>
                  <label className="flex items-center gap-3 text-sm font-semibold">
                    Loan Type
                    <select value={loanTypeFilter} onChange={(event) => setLoanTypeFilter(event.target.value)}
                      className="rounded-xl border border-gray-300 bg-white px-4 py-2">
                      <option value="ALL">All Loan Types</option>
                      {loanTypeGroups.map((group) => <option key={group.key} value={group.key}>{group.label}</option>)}
                    </select>
                  </label>
                </div>

                {filteredLoans.length === 0 ? (
                  <p className="rounded-xl bg-white p-6 text-gray-500">
                    No loans match your search.
                  </p>
                ) : (
                  <div className="grid gap-5 md:grid-cols-2">

                    {filteredLoans.map((loan) => {
                      const loanRequests = coolingOffRequests
                        .filter((request) => String(request.loanId) === String(loan.loanId))
                        .sort((a, b) => {
                          const dateA = Date.parse(a.requestDate || a.createdAt || "") || 0;
                          const dateB = Date.parse(b.requestDate || b.createdAt || "") || 0;
                          if (dateA !== dateB) return dateB - dateA;
                          return Number(b.exitRequestId) - Number(a.exitRequestId);
                        });
                      const latestRequest = loanRequests[0];

                      return (

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

                        <div className="mt-5 rounded-xl bg-gray-50 p-4">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <span className="text-sm font-medium text-gray-700">Cooling-Off Request</span>
                            {latestRequest && <RequestStatusBadge status={latestRequest.status} />}
                          </div>

                          {requestListLoading ? (
                            <p className="mt-2 text-sm text-gray-500">Loading request status...</p>
                          ) : requestListError ? (
                            <>
                              <p role="alert" className="mt-2 text-sm text-red-700">{requestListError}</p>
                              <button
                                type="button"
                                onClick={() => setRequestRefreshCount((count) => count + 1)}
                                disabled={requestListLoading}
                                className="mt-3 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100 disabled:opacity-60"
                              >
                                Retry Request Status
                              </button>
                            </>
                          ) : latestRequest ? (
                            <>
                              <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
                                <div><dt className="text-gray-500">Exit Request ID</dt><dd className="font-medium">{latestRequest.exitRequestId ?? "N/A"}</dd></div>
                                <div><dt className="text-gray-500">Request Date</dt><dd className="font-medium">{formatDate(latestRequest.requestDate)}</dd></div>
                              </dl>
                              <div className="mt-3 flex flex-wrap gap-2">
                                <button
                                  type="button"
                                  onClick={() => setSelectedRequestId(latestRequest.exitRequestId)}
                                  className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100"
                                >
                                  View Exit Request Details{loanRequests.length > 1 ? ` (${loanRequests.length})` : ""}
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleRefreshRequest(latestRequest.exitRequestId)}
                                  disabled={refreshingRequestId === String(latestRequest.exitRequestId)}
                                  className="rounded-lg border border-green-700 bg-white px-3 py-2 text-sm font-semibold text-green-800 hover:bg-green-50 disabled:opacity-60"
                                >
                                  {refreshingRequestId === String(latestRequest.exitRequestId) ? "Refreshing..." : "Refresh Status"}
                                </button>
                              </div>
                              {requestRefreshErrors[String(latestRequest.exitRequestId)] && (
                                <p role="alert" className="mt-2 text-sm text-red-700">
                                  {requestRefreshErrors[String(latestRequest.exitRequestId)]}
                                </p>
                              )}
                            </>
                          ) : (
                            <p className="mt-2 text-sm text-gray-500">No cooling-off exit request submitted.</p>
                          )}
                        </div>

                        <button
                          onClick={() => {
                            setDetailsLoanId(loan.loanId);
                          }}
                          className="mt-7 w-full rounded-xl bg-black py-3 text-sm font-semibold text-white hover:bg-gray-800"
                        >
                          View Loan Details →
                        </button>

                        <button
                          type="button"
                          onClick={() => {
                            setCoolingOffLoanId(String(loan.loanId));
                            changeMenu("cooling-off");
                            handleCheckEligibility(loan.loanId);
                          }}
                          disabled={Boolean(eligibilityLoading[String(loan.loanId)])}
                          className="mt-3 w-full rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 hover:bg-green-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {eligibilityLoading[String(loan.loanId)]
                            ? "Checking eligibility..."
                            : "Check Cooling-Off Eligibility"}
                        </button>

                        {loan.loanStatus === "ACTIVE" && <button type="button"
                          onClick={() => { setPreClosureLoanId(loan.loanId); setActiveMenu("preclosure"); }}
                          className="mt-3 w-full rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 hover:bg-green-50">
                          Request Pre-closure / Foreclosure
                        </button>}

                        {eligibilityErrors[String(loan.loanId)] && (
                          <p
                            role="alert"
                            className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700"
                          >
                            {eligibilityErrors[String(loan.loanId)]}
                          </p>
                        )}

                        {eligibilityResults[String(loan.loanId)] && (() => {
                          const result = eligibilityResults[String(loan.loanId)];
                          const hasExistingRequest =
                            String(result.reason || "").toLowerCase() ===
                            "a cooling-off exit request already exists";

                          return (
                            <section
                              aria-live="polite"
                              className={`mt-4 rounded-xl border p-4 ${
                                result.eligible
                                  ? "border-green-200 bg-green-50 text-green-900"
                                  : "border-red-200 bg-red-50 text-red-900"
                              }`}
                            >
                              <h4 className="font-semibold">
                                {result.eligible
                                  ? "Eligible for Cooling-Off Exit"
                                  : "Not Eligible for New Cooling-Off Exit"}
                              </h4>
                              <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
                                <div>
                                  <dt className="font-medium">Loan ID</dt>
                                  <dd>{result.loanId ?? "N/A"}</dd>
                                </div>
                                {result.eligible && (
                                  <div>
                                    <dt className="font-medium">Cooling-off duration</dt>
                                    <dd>{result.coolingOffDays != null ? `${result.coolingOffDays} days` : "N/A"}</dd>
                                  </div>
                                )}
                                <div>
                                  <dt className="font-medium">Cooling-off expiry</dt>
                                  <dd>{formatDate(result.expiryDate)}</dd>
                                </div>
                                <div className="sm:col-span-2">
                                  <dt className="font-medium">Reason</dt>
                                  <dd>{result.reason || "N/A"}</dd>
                                </div>
                              </dl>
                              {hasExistingRequest && (
                                <p className="mt-3 text-sm font-medium">
                                  An exit request has already been submitted for this loan. You cannot create another active request.
                                </p>
                              )}
                              {result.eligible && !hasExistingRequest && !latestRequest && !requestResults[String(loan.loanId)] && (
                                <button
                                  type="button"
                                  onClick={() => {
                                    setRequestLoanId(loan.loanId);
                                    setRequestReason("");
                                    setRequestErrors((current) => ({ ...current, [String(loan.loanId)]: "" }));
                                  }}
                                  className="mt-4 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800"
                                >
                                  Request Cooling-Off Exit
                                </button>
                              )}
                            </section>
                          );
                        })()}

                        {requestResults[String(loan.loanId)] && (() => {
                          const request = requestResults[String(loan.loanId)];
                          return (
                            <section
                              aria-live="polite"
                              className="mt-4 rounded-xl border border-green-200 bg-green-50 p-4 text-green-900"
                            >
                              <h4 className="font-semibold">Cooling-Off Exit Request Submitted</h4>
                              <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
                                <div><dt className="font-medium">Exit Request ID</dt><dd>{request.exitRequestId ?? "N/A"}</dd></div>
                                <div><dt className="font-medium">Loan Reference</dt><dd>{request.loanReference || "N/A"}</dd></div>
                                <div><dt className="font-medium">Request Date</dt><dd>{formatDate(request.requestDate)}</dd></div>
                                <div><dt className="font-medium">Status</dt><dd>{request.status || "N/A"}</dd></div>
                                <div className="sm:col-span-2"><dt className="font-medium">Reason</dt><dd>{request.reason || "N/A"}</dd></div>
                              </dl>
                            </section>
                          );
                        })()}

                        {requestErrors[String(loan.loanId)] && (
                          <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                            {requestErrors[String(loan.loanId)]}
                          </p>
                        )}

                      </article>
                      );
                    })}

                  </div>
                )}

              </section>
              )}

            </>
          )}

        
            </>
          )}
        </div>
      </section>

      {detailsLoan && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setDetailsLoanId(null);
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="loan-details-title"
            className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="dashboard-eyebrow">Loan Information</p>
                <h2 id="loan-details-title" className="mt-2 text-2xl font-semibold">
                  Complete Loan Details
                </h2>
              </div>
              <div className="flex items-center gap-3">
                <LoanStatus status={detailsLoan.loanStatus} />
                <button
                  type="button"
                  onClick={() => setDetailsLoanId(null)}
                  aria-label="Close loan details"
                  className="rounded-full px-3 py-2 text-xl text-gray-500 hover:bg-gray-100 hover:text-gray-900"
                >
                  ×
                </button>
              </div>
            </div>

            <div className="mt-8 grid gap-7 sm:grid-cols-2 lg:grid-cols-3">
              <DetailField label="Loan ID" value={detailsLoan.loanId} />
              <DetailField label="Loan Reference" value={detailsLoan.loanReference} />
              <DetailField label="Loan Type" value={formatLoanType(detailsLoan.productType)} />
              <DetailField label="Principal Amount" value={formatCurrency(detailsLoan.principalAmount)} />
              <DetailField label="Outstanding Amount" value={formatCurrency(detailsLoan.outstandingAmount)} />
              <DetailField label="Interest Rate" value={detailsLoan.interestRate != null ? `${detailsLoan.interestRate}%` : "N/A"} />
              <DetailField label="Tenure" value={detailsLoan.tenureMonths != null ? `${detailsLoan.tenureMonths} months` : "N/A"} />
              <DetailField label="Disbursement Date" value={formatDate(detailsLoan.disbursementDate)} />
              <DetailField label="Maturity Date" value={formatDate(detailsLoan.maturityDate)} />
              <DetailField label="Cooling-Off Duration" value={detailsLoan.coolingOffDays != null ? `${detailsLoan.coolingOffDays} days` : "N/A"} />
              <DetailField label="Cooling-Off Expiry" value={formatDate(detailsLoan.coolingOffExpiry)} />
              <DetailField label="Closure Date" value={formatDate(detailsLoan.closureDate)} />
            </div>
          </section>
        </div>
      )}

      {activeMenu !== "cooling-off" && coolingOffDetails}

      {acceptanceConfirmationOpen && settlementQuote && selectedRequestDetails && (
        <div
          className="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 p-4"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !acceptingQuote) {
              setAcceptanceConfirmationOpen(false);
              setAcceptanceAcknowledged(false);
              setAcceptanceError("");
            }
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="accept-settlement-title"
            className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
          >
            <h2 id="accept-settlement-title" className="text-2xl font-semibold">
              Confirm Settlement Quote Acceptance
            </h2>
            <p className="mt-2 text-sm text-gray-600">
              Review the settlement details before confirming. This action cannot be repeated once accepted.
            </p>

            <div className="mt-6 grid gap-5 rounded-xl bg-gray-50 p-5 sm:grid-cols-2">
              <DetailField label="Loan Reference" value={selectedRequestDetails.loanReference} />
              <DetailField label="Exit Request ID" value={selectedRequestDetails.exitRequestId} />
              <DetailField label="Quote ID" value={settlementQuote.quoteId} />
              <DetailField label="Principal Amount" value={formatQuoteCurrency(settlementQuote.principalAmount)} />
              <DetailField label="Interest Amount" value={formatQuoteCurrency(settlementQuote.interestAmount)} />
              <DetailField label="Processing Fee" value={formatQuoteCurrency(settlementQuote.chargesAmount)} />
              <DetailField label="Tax" value={formatQuoteCurrency(settlementQuote.taxAmount)} />
              <DetailField label="Quote Valid Until" value={formatDateTime(settlementQuote.validUntil)} />
              <div className="rounded-lg bg-lime-100 p-3 sm:col-span-2">
                <p className="text-sm font-medium text-gray-700">Total Settlement Amount</p>
                <p className="mt-1 text-2xl font-bold text-[#102018]">
                  {formatQuoteCurrency(settlementQuote.totalAmount)}
                </p>
              </div>
            </div>

            <label className="mt-5 flex cursor-pointer items-start gap-3 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={acceptanceAcknowledged}
                onChange={(event) => {
                  setAcceptanceAcknowledged(event.target.checked);
                  setAcceptanceError("");
                }}
                className="mt-1 h-4 w-4 accent-lime-700"
              />
              <span>I understand and accept the settlement amount displayed above.</span>
            </label>

            {acceptanceError && (
              <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                {acceptanceError}
              </p>
            )}

            <div className="mt-6 flex flex-col-reverse justify-end gap-3 sm:flex-row">
              <button
                type="button"
                onClick={() => {
                  setAcceptanceConfirmationOpen(false);
                  setAcceptanceAcknowledged(false);
                  setAcceptanceError("");
                }}
                disabled={acceptingQuote}
                className="rounded-xl border border-gray-300 px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-60"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleAcceptSettlementQuote}
                disabled={!acceptanceAcknowledged || acceptingQuote}
                className="rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {acceptingQuote ? "Confirming acceptance..." : "Confirm Acceptance"}
              </button>
            </div>
          </section>
        </div>
      )}

      {requestLoanId != null && (() => {
        const requestLoan = loans.find((loan) => String(loan.loanId) === String(requestLoanId));
        const eligibility = eligibilityResults[String(requestLoanId)];
        if (!requestLoan || !eligibility) return null;

        return (
          <div
            className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
            role="presentation"
            onMouseDown={(event) => {
              if (event.target === event.currentTarget && !requestSubmitting) setRequestLoanId(null);
            }}
          >
            <section
              role="dialog"
              aria-modal="true"
              aria-labelledby="cooling-off-request-title"
              className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
            >
              <h2 id="cooling-off-request-title" className="text-2xl font-semibold">
                Request Cooling-Off Exit
              </h2>
              <p className="mt-2 text-sm text-gray-500">
                Submit a request for this eligible loan. The lender will review it.
              </p>

              <form onSubmit={handleSubmitCoolingOffRequest} className="mt-6 space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="block text-sm font-medium text-gray-700">
                    Loan ID
                    <input readOnly value={requestLoan.loanId} className="mt-2 w-full rounded-lg border bg-gray-50 px-3 py-2.5 text-gray-700" />
                  </label>
                  <label className="block text-sm font-medium text-gray-700">
                    Loan Reference
                    <textarea readOnly rows={2} value={requestLoan.loanReference || "N/A"} className="mt-2 w-full resize-none rounded-lg border bg-gray-50 px-3 py-2.5 text-gray-700" />
                  </label>
                </div>
                <label className="block text-sm font-medium text-gray-700">
                  Cooling-Off Expiry Date
                  <input readOnly value={formatDate(eligibility.expiryDate)} className="mt-2 w-full rounded-lg border bg-gray-50 px-3 py-2.5 text-gray-700" />
                </label>
                <label className="block text-sm font-medium text-gray-700">
                  Reason for Exit
                  <textarea
                    value={requestReason}
                    onChange={(event) => setRequestReason(event.target.value.slice(0, 1000))}
                    maxLength={1000}
                    required
                    rows={4}
                    placeholder="Tell us why you would like to exit this loan."
                    className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2.5"
                  />
                  <span className="mt-1 block text-right text-xs text-gray-500">
                    {requestReason.length}/1000
                  </span>
                </label>
                {requestErrors[String(requestLoanId)] && (
                  <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
                    {requestErrors[String(requestLoanId)]}
                  </p>
                )}
                <div className="flex flex-col-reverse justify-end gap-3 pt-2 sm:flex-row">
                  <button
                    type="button"
                    onClick={() => setRequestLoanId(null)}
                    disabled={requestSubmitting}
                    className="rounded-xl border border-gray-300 px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={requestSubmitting || !requestReason.trim()}
                    className="rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {requestSubmitting ? "Submitting..." : "Submit Request"}
                  </button>
                </div>
              </form>
            </section>
          </div>
        );
      })()}

    </main>
  );
}

export default BorrowerDashboard;

// import { useEffect, useRef, useState } from "react";
// import {
//   acceptSettlementQuote,
//   checkCoolingOffEligibility,
//   generateSettlementQuote,
//   getCoolingOffRequestById,
//   getMyCoolingOffRequests,
//   getMyLoans,
//   getSettlementQuoteByRequest,
//   submitCoolingOffRequest,
// } from "../api";

// // -----------------------------------------
// // FORMAT CURRENCY
// // -----------------------------------------

// function formatCurrency(amount) {
//   if (amount == null) return "N/A";

//   return new Intl.NumberFormat("en-IN", {
//     style: "currency",
//     currency: "INR",
//     maximumFractionDigits: 2,
//   }).format(amount);
// }

// function formatQuoteCurrency(amount) {
//   if (amount == null) return "N/A";
//   return new Intl.NumberFormat("en-IN", {
//     style: "currency",
//     currency: "INR",
//     minimumFractionDigits: 2,
//     maximumFractionDigits: 2,
//   }).format(amount);
// }

// // -----------------------------------------
// // FORMAT DATE
// // -----------------------------------------

// function formatDate(date) {
//   if (!date) return "N/A";

//   // Oracle dates are returned as local date-time strings.
//   // Display the date portion without timezone conversion.
//   const datePart = String(date).slice(0, 10);
//   const parts = datePart.split("-");

//   if (parts.length !== 3) return datePart;

//   const [year, month, day] = parts;

//   return `${day}-${month}-${year}`;
// }

// function formatDateTime(date) {
//   if (!date) return "N/A";
//   const [datePart, timePart] = String(date).split("T");
//   const formattedDate = formatDate(datePart);
//   return timePart ? `${formattedDate} ${timePart.slice(0, 5)}` : formattedDate;
// }

// // -----------------------------------------
// // FORMAT LOAN TYPE
// // -----------------------------------------

// function formatLoanType(type) {
//   if (!type) return "Loan";

//   return type
//     .replaceAll("_", " ")
//     .toLowerCase()
//     .replace(/\b\w/g, (letter) => letter.toUpperCase());
// }

// // -----------------------------------------
// // STATUS BADGE
// // -----------------------------------------

// function LoanStatus({ status }) {
//   const colors = {
//     ACTIVE: "bg-green-100 text-green-700",
//     CLOSED: "bg-gray-100 text-gray-700",
//     EXIT_COMPLETED: "bg-blue-100 text-blue-700",
//     PENDING: "bg-yellow-100 text-yellow-700",
//     DEFAULTED: "bg-red-100 text-red-700",
//     CANCELLED: "bg-gray-100 text-gray-600",
//   };

//   return (
//     <span
//       className={`rounded-full px-3 py-1 text-xs font-semibold ${
//         colors[status] || "bg-gray-100 text-gray-700"
//       }`}
//     >
//       {status?.replaceAll("_", " ") || "UNKNOWN"}
//     </span>
//   );
// }

// const requestStatusStyles = {
//   PENDING: "bg-yellow-100 text-yellow-800",
//   UNDER_REVIEW: "bg-blue-100 text-blue-800",
//   MORE_INFO_REQUIRED: "bg-orange-100 text-orange-800",
//   APPROVED: "bg-green-100 text-green-800",
//   REJECTED: "bg-red-100 text-red-800",
//   CANCELLED: "bg-gray-100 text-gray-700",
//   EXPIRED: "bg-gray-100 text-gray-700",
//   COMPLETED: "bg-emerald-100 text-emerald-800",
// };

// const requestStatusLabels = {
//   PENDING: "Pending",
//   UNDER_REVIEW: "Under Review",
//   MORE_INFO_REQUIRED: "More Information Required",
//   APPROVED: "Approved",
//   REJECTED: "Rejected",
//   CANCELLED: "Cancelled",
//   EXPIRED: "Expired",
//   COMPLETED: "Completed",
// };

// const requestStatusMessages = {
//   PENDING: "Your exit request has been submitted and is awaiting review.",
//   UNDER_REVIEW: "A bank officer is reviewing your request.",
//   MORE_INFO_REQUIRED: "Additional information is required. Contact the bank or await the information-submission feature.",
//   APPROVED: "Your cooling-off exit request has been approved. Settlement and payment processing are still required.",
//   REJECTED: "Your cooling-off exit request was rejected.",
//   COMPLETED: "Your cooling-off exit process has been completed.",
// };

// function RequestStatusBadge({ status }) {
//   const label = requestStatusLabels[status] || status?.replaceAll("_", " ") || "N/A";
//   return (
//     <span className={`rounded-full px-3 py-1 text-xs font-semibold ${requestStatusStyles[status] || "bg-gray-100 text-gray-700"}`}>
//       {label}
//     </span>
//   );
// }

// // -----------------------------------------
// // LOAN DETAIL FIELD
// // -----------------------------------------

// function DetailField({ label, value }) {
//   return (
//     <div>
//       <p className="text-xs text-gray-500">
//         {label}
//       </p>

//       <p className="mt-2 break-words text-sm font-semibold text-gray-900">
//         {value ?? "N/A"}
//       </p>
//     </div>
//   );
// }

// // -----------------------------------------
// // MAIN DASHBOARD
// // -----------------------------------------

// function BorrowerDashboard({ session, onLogout }) {

//   const [activeMenu, setActiveMenu] = useState("overview");

//   const [loans, setLoans] = useState([]);

//   const [selectedLoanId, setSelectedLoanId] = useState(null);
//   const [detailsLoanId, setDetailsLoanId] = useState(null);

//   const [loading, setLoading] = useState(true);

//   const [error, setError] = useState("");

//   const [search, setSearch] = useState("");

//   const [refreshCount, setRefreshCount] = useState(0);
//   const [eligibilityResults, setEligibilityResults] = useState({});
//   const [eligibilityLoading, setEligibilityLoading] = useState({});
//   const [eligibilityErrors, setEligibilityErrors] = useState({});
//   const [requestLoanId, setRequestLoanId] = useState(null);
//   const [requestReason, setRequestReason] = useState("");
//   const [requestSubmitting, setRequestSubmitting] = useState(false);
//   const [requestErrors, setRequestErrors] = useState({});
//   const [requestResults, setRequestResults] = useState({});
//   const [coolingOffRequests, setCoolingOffRequests] = useState([]);
//   const [requestListLoading, setRequestListLoading] = useState(true);
//   const [requestListError, setRequestListError] = useState("");
//   const [requestRefreshCount, setRequestRefreshCount] = useState(0);
//   const [selectedRequestId, setSelectedRequestId] = useState(null);
//   const [selectedRequestDetails, setSelectedRequestDetails] = useState(null);
//   const [requestDetailsLoading, setRequestDetailsLoading] = useState(false);
//   const [requestDetailsError, setRequestDetailsError] = useState("");
//   const [refreshingRequestId, setRefreshingRequestId] = useState(null);
//   const [requestRefreshErrors, setRequestRefreshErrors] = useState({});
//   const [settlementQuote, setSettlementQuote] = useState(null);
//   const [settlementQuoteLoading, setSettlementQuoteLoading] = useState(false);
//   const [settlementQuoteLoaded, setSettlementQuoteLoaded] = useState(false);
//   const [settlementQuoteError, setSettlementQuoteError] = useState("");
//   const [quoteGenerating, setQuoteGenerating] = useState(false);
//   const [acceptanceConfirmationOpen, setAcceptanceConfirmationOpen] = useState(false);
//   const [acceptanceAcknowledged, setAcceptanceAcknowledged] = useState(false);
//   const [acceptingQuote, setAcceptingQuote] = useState(false);
//   const [acceptanceError, setAcceptanceError] = useState("");
//   const [quoteRefreshError, setQuoteRefreshError] = useState("");
//   const acceptanceSubmittingRef = useRef(false);

//   async function handleCheckEligibility(loanId) {
//     const loanKey = String(loanId);
//     setEligibilityLoading((current) => ({ ...current, [loanKey]: true }));
//     setEligibilityErrors((current) => ({ ...current, [loanKey]: "" }));
//     setEligibilityResults((current) => {
//       const next = { ...current };
//       delete next[loanKey];
//       return next;
//     });

//     try {
//       const result = await checkCoolingOffEligibility(session.token, loanId);
//       setEligibilityResults((current) => ({ ...current, [loanKey]: result }));
//     } catch (eligibilityError) {
//       setEligibilityErrors((current) => ({
//         ...current,
//         [loanKey]: eligibilityError.message,
//       }));
//     } finally {
//       setEligibilityLoading((current) => ({ ...current, [loanKey]: false }));
//     }
//   }

//   async function handleSubmitCoolingOffRequest(event) {
//     event.preventDefault();
//     const loan = loans.find((item) => String(item.loanId) === String(requestLoanId));
//     const reason = requestReason.trim();
//     const loanKey = String(requestLoanId);

//     if (!loan || !reason || reason.length > 1000) {
//       setRequestErrors((current) => ({
//         ...current,
//         [loanKey]: !loan
//           ? "Select a loan from your account before submitting."
//           : "Enter a reason of up to 1000 characters.",
//       }));
//       return;
//     }

//     setRequestSubmitting(true);
//     setRequestErrors((current) => ({ ...current, [loanKey]: "" }));

//     try {
//       const result = await submitCoolingOffRequest(session.token, loan.loanId, reason);
//       setRequestResults((current) => ({ ...current, [loanKey]: result }));
//       setRequestReason("");
//       setRequestLoanId(null);
//       setRequestRefreshCount((count) => count + 1);
//     } catch (requestError) {
//       setRequestErrors((current) => ({ ...current, [loanKey]: requestError.message }));
//     } finally {
//       setRequestSubmitting(false);
//     }
//   }

//   // -----------------------------------------
//   // FETCH LOANS FROM BACKEND
//   // -----------------------------------------

//   useEffect(() => {
//     let active = true;

//     async function loadLoans() {
//       setLoading(true);
//       setError("");

//       try {
//         const data = await getMyLoans(session.token);

//         if (!Array.isArray(data)) {
//           throw new Error("Invalid loan response from server");
//         }

//         if (active) {
//           setLoans(data);
//         }

//       } catch (err) {
//         if (active) {
//           setError(err.message);
//           setLoans([]);
//         }

//       } finally {
//         if (active) {
//           setLoading(false);
//         }
//       }
//     }

//     loadLoans();

//     return () => {
//       active = false;
//     };

//   }, [session.token, refreshCount]);

//   useEffect(() => {
//     let active = true;

//     async function loadCoolingOffRequests() {
//       setRequestListLoading(true);
//       setRequestListError("");
//       try {
//         const data = await getMyCoolingOffRequests(session.token);
//         const requestList = Array.isArray(data)
//           ? data
//           : Array.isArray(data?.requests)
//             ? data.requests
//             : null;
//         if (!requestList) throw new Error("Invalid cooling-off request response from server.");
//         if (active) {
//           setCoolingOffRequests(requestList);
//           setRequestResults((current) => {
//             const latestByLoan = new Map();
//             for (const request of requestList) {
//               const key = String(request.loanId);
//               const previous = latestByLoan.get(key);
//               const requestDate = Date.parse(request.requestDate || request.createdAt || "") || 0;
//               const previousDate = Date.parse(previous?.requestDate || previous?.createdAt || "") || 0;
//               const newer = requestDate > previousDate || (
//                 requestDate === previousDate && Number(request.exitRequestId) > Number(previous?.exitRequestId)
//               );
//               if (!previous || newer) latestByLoan.set(key, request);
//             }
//             const next = { ...current };
//             for (const [key, request] of latestByLoan) {
//               if (next[key]) next[key] = request;
//             }
//             return next;
//           });
//         }
//       } catch (loadError) {
//         if (active) setRequestListError(loadError.message);
//       } finally {
//         if (active) setRequestListLoading(false);
//       }
//     }

//     loadCoolingOffRequests();
//     return () => { active = false; };
//   }, [session.token, requestRefreshCount]);

//   useEffect(() => {
//     if (selectedRequestId == null) {
//       setSelectedRequestDetails(null);
//       return undefined;
//     }

//     let active = true;
//     async function loadRequestDetails() {
//       setRequestDetailsLoading(true);
//       setRequestDetailsError("");
//       setSelectedRequestDetails(null);
//       try {
//         const data = await getCoolingOffRequestById(session.token, selectedRequestId);
//         if (active) {
//           setSelectedRequestDetails(data);
//           setCoolingOffRequests((current) => {
//             const exists = current.some((item) => String(item.exitRequestId) === String(data.exitRequestId));
//             return exists
//               ? current.map((item) => String(item.exitRequestId) === String(data.exitRequestId) ? data : item)
//               : [...current, data];
//           });
//         }
//       } catch (loadError) {
//         if (active) setRequestDetailsError(loadError.message);
//       } finally {
//         if (active) setRequestDetailsLoading(false);
//       }
//     }

//     loadRequestDetails();
//     return () => { active = false; };
//   }, [session.token, selectedRequestId]);

//   async function handleRefreshRequest(requestId) {
//     const key = String(requestId);
//     setRefreshingRequestId(key);
//     setRequestRefreshErrors((current) => ({ ...current, [key]: "" }));
//     try {
//       const latest = await getCoolingOffRequestById(session.token, requestId);
//       setCoolingOffRequests((current) => current.map((item) =>
//         String(item.exitRequestId) === key ? latest : item,
//       ));
//       if (String(selectedRequestId) === key) setSelectedRequestDetails(latest);
//     } catch (refreshError) {
//       setRequestRefreshErrors((current) => ({ ...current, [key]: refreshError.message }));
//     } finally {
//       setRefreshingRequestId(null);
//     }
//   }

//   async function handleGenerateSettlementQuote() {
//     if (!selectedRequestDetails?.exitRequestId) return;
//     setQuoteGenerating(true);
//     setSettlementQuoteError("");
//     try {
//       const quote = await generateSettlementQuote(
//         session.token,
//         selectedRequestDetails.exitRequestId,
//       );
//       setSettlementQuote(quote);
//       setSettlementQuoteLoaded(true);
//     } catch (quoteError) {
//       setSettlementQuoteError(quoteError.message);
//     } finally {
//       setQuoteGenerating(false);
//     }
//   }

//   async function handleAcceptSettlementQuote() {
//     if (acceptanceSubmittingRef.current) return;

//     const quoteId = settlementQuote?.quoteId;
//     const canAccept = selectedRequestDetails?.status === "APPROVED" &&
//       settlementQuote?.quoteStatus === "AVAILABLE" &&
//       settlementQuote?.expired === false &&
//       quoteId != null;

//     if (!canAccept) {
//       setAcceptanceError("This quote is not currently available for acceptance.");
//       return;
//     }
//     if (!acceptanceAcknowledged) {
//       setAcceptanceError("Confirm that you understand and accept the settlement amount.");
//       return;
//     }

//     acceptanceSubmittingRef.current = true;
//     setAcceptingQuote(true);
//     setAcceptanceError("");
//     setQuoteRefreshError("");

//     try {
//       const acceptedQuote = await acceptSettlementQuote(session.token, quoteId);
//       setSettlementQuote(acceptedQuote);
//       setAcceptanceConfirmationOpen(false);

//       try {
//         const latestQuote = await getSettlementQuoteByRequest(
//           session.token,
//           acceptedQuote.exitRequestId ?? selectedRequestDetails.exitRequestId,
//         );
//         if (latestQuote) setSettlementQuote(latestQuote);
//       } catch (refreshError) {
//         setQuoteRefreshError(`Quote accepted, but refreshing its latest details failed: ${refreshError.message}`);
//       }
//     } catch (error) {
//       setAcceptanceError(error.message);
//     } finally {
//       acceptanceSubmittingRef.current = false;
//       setAcceptingQuote(false);
//     }
//   }

//   useEffect(() => {
//     const requestId = selectedRequestDetails?.exitRequestId;
//     let active = true;

//     if (requestId == null) {
//       setSettlementQuote(null);
//       setSettlementQuoteLoaded(false);
//       setSettlementQuoteError("");
//       return () => { active = false; };
//     }

//     async function loadQuote() {
//       setSettlementQuote(null);
//       setSettlementQuoteLoaded(false);
//       setSettlementQuoteLoading(true);
//       setSettlementQuoteError("");
//       setAcceptanceError("");
//       setQuoteRefreshError("");
//       try {
//         const quote = await getSettlementQuoteByRequest(session.token, requestId);
//         if (active) {
//           setSettlementQuote(quote);
//           setSettlementQuoteLoaded(true);
//         }
//       } catch (quoteError) {
//         if (active) setSettlementQuoteError(quoteError.message);
//       } finally {
//         if (active) setSettlementQuoteLoading(false);
//       }
//     }

//     loadQuote();
//     return () => { active = false; };
//   }, [session.token, selectedRequestDetails?.exitRequestId, selectedRequestDetails?.updatedAt]);

//   // -----------------------------------------
//   // CALCULATED DASHBOARD VALUES
//   // -----------------------------------------

//   const totalLoans = loans.length;

//   const activeLoans = loans.filter(
//     (loan) => loan.loanStatus === "ACTIVE"
//   ).length;

//   const totalOutstanding = loans.reduce(
//     (sum, loan) =>
//       sum + Number(loan.outstandingAmount || 0),
//     0
//   );

//   // Select active loan first, otherwise first loan
//   const primaryLoan =
//     loans.find((loan) => loan.loanStatus === "ACTIVE") ||
//     loans[0] ||
//     null;

//   const selectedLoan =
//     loans.find((loan) => loan.loanId === selectedLoanId) ||
//     primaryLoan;
//   const detailsLoan = loans.find((loan) => loan.loanId === detailsLoanId);

//   // -----------------------------------------
//   // SEARCH LOANS
//   // -----------------------------------------

//   const filteredLoans = loans.filter((loan) => {

//     const keyword = search.toLowerCase();

//     return (
//       String(loan.loanId).includes(keyword) ||
//       String(loan.loanReference || "")
//         .toLowerCase()
//         .includes(keyword) ||
//       String(loan.productType || "")
//         .toLowerCase()
//         .includes(keyword)
//     );
//   });

//   // -----------------------------------------
//   // GREETING
//   // -----------------------------------------

//   const hour = new Date().getHours();

//   const greeting =
//     hour < 12
//       ? "Good morning"
//       : hour < 17
//       ? "Good afternoon"
//       : "Good evening";

//   // -----------------------------------------
//   // SIDEBAR MENU
//   // -----------------------------------------

//   const menuItems = [
//     { id: "overview", icon: "▦", label: "Overview" },
//     { id: "my-loans", icon: "◫", label: "My Loans" },
//   ];

//   // -----------------------------------------
//   // RENDER
//   // -----------------------------------------

//   return (
//     <main className="borrower-shell">

//       {/* SIDEBAR */}

//       <aside className="borrower-sidebar">

//         <div className="dashboard-brand dashboard-brand-light">
//           <span className="dashboard-brand-mark" />
//           Clear<span>Close</span>
//         </div>

//         <p className="sidebar-heading">
//           Borrower Portal
//         </p>

//         <nav className="sidebar-nav">

//           {menuItems.map((item) => (

//             <button
//               key={item.id}
//               className={
//                 activeMenu === item.id
//                   ? "sidebar-link active"
//                   : "sidebar-link"
//               }
//               onClick={() => setActiveMenu(item.id)}
//             >
//               <span>{item.icon}</span>
//               {item.label}
//             </button>

//           ))}

//         </nav>

//         {/* PROFILE */}

//         <div className="borrower-profile">

//           <div className="profile-avatar">
//             {session.username
//               ?.slice(0, 2)
//               .toUpperCase()}
//           </div>

//           <div>
//             <strong>{session.username}</strong>
//             <span>Borrower Account</span>
//           </div>

//           <button
//             className="logout-button"
//             title="Sign out"
//             onClick={onLogout}
//           >
//             ↗
//           </button>

//         </div>

//       </aside>

//       {/* MAIN DASHBOARD */}

//       <section className="dashboard-main">

//         {/* TOP BAR */}

//         <header className="dashboard-topbar">

//           <div className="dashboard-search">
//             <span>⌕</span>

//             <input
//               placeholder="Search loan ID, reference, type..."
//               value={search}
//               onChange={(e) => setSearch(e.target.value)}
//             />
//           </div>

//           <div className="topbar-actions">
//             <div className="topbar-avatar">
//               {session.username
//                 ?.slice(0, 2)
//                 .toUpperCase()}
//             </div>
//           </div>

//         </header>

//         <div className="dashboard-content">

//           {/* HEADING */}

//           <div className="dashboard-intro">

//             <div>
//               <p className="dashboard-eyebrow">
//                 {greeting}, {session.username}
//               </p>

//               <h1>
//                 Your Loan Dashboard
//               </h1>

//               <p className="dashboard-muted">
//                 View and manage your existing loan accounts.
//               </p>
//             </div>

//             <button
//               className="dashboard-outline-button"
//               onClick={() =>
//                 setRefreshCount((count) => count + 1)
//               }
//               disabled={loading}
//             >
//               Refresh Loans ↻
//             </button>

//           </div>

//           {/* LOADING */}

//           {loading && (
//             <p className="mt-8 text-gray-500">
//               Loading your loans...
//             </p>
//           )}

//           {/* ERROR */}

//           {error && !loading && (
//             <p
//               role="alert"
//               className="mt-8 rounded-xl bg-red-50 p-5 text-red-600"
//             >
//               {error}
//             </p>
//           )}

//           {/* NO LOANS */}

//           {!loading && !error && loans.length === 0 && (
//             <div className="mt-8 rounded-2xl bg-white p-8">
//               <h2 className="text-xl font-semibold">
//                 No Loans Found
//               </h2>

//               <p className="mt-3 text-gray-500">
//                 No existing loan accounts are linked
//                 to your borrower account.
//               </p>
//             </div>
//           )}

//           {/* REAL DATA */}

//           {!loading && !error && loans.length > 0 && (
//             <>

//               {/* SUMMARY */}

//               <section className="mt-8 grid gap-5 sm:grid-cols-3">

//                 <div className="rounded-2xl bg-white p-6 shadow-sm">
//                   <p className="text-sm text-gray-500">
//                     Total Loans
//                   </p>

//                   <h2 className="mt-3 text-3xl font-bold">
//                     {totalLoans}
//                   </h2>
//                 </div>

//                 <div className="rounded-2xl bg-white p-6 shadow-sm">
//                   <p className="text-sm text-gray-500">
//                     Active Loans
//                   </p>

//                   <h2 className="mt-3 text-3xl font-bold">
//                     {activeLoans}
//                   </h2>
//                 </div>

//                 <div className="rounded-2xl bg-lime-100 p-6 shadow-sm">
//                   <p className="text-sm text-gray-600">
//                     Total Outstanding
//                   </p>

//                   <h2 className="mt-3 text-2xl font-bold">
//                     {formatCurrency(totalOutstanding)}
//                   </h2>
//                 </div>

//               </section>

//               {/* SELECTED LOAN SUMMARY */}

//               {selectedLoan && (
//                 <section className="loan-summary-card mt-8">

//                   <div>
//                     <p className="dashboard-eyebrow">
//                       Selected Loan
//                     </p>

//                     <h2>
//                       {formatLoanType(
//                         selectedLoan.productType
//                       )}
//                     </h2>

//                     <p>
//                       Loan ID: {selectedLoan.loanId}
//                     </p>

//                     <p>
//                       Reference: {selectedLoan.loanReference}
//                     </p>
//                   </div>

//                   <div className="loan-summary-amount">
//                     <span>Outstanding Amount</span>

//                     <strong>
//                       {formatCurrency(
//                         selectedLoan.outstandingAmount
//                       )}
//                     </strong>
//                   </div>

//                   <div className="loan-summary-action">
//                     <span>Loan Status</span>

//                     <LoanStatus
//                       status={selectedLoan.loanStatus}
//                     />
//                   </div>

//                 </section>
//               )}

//               {/* MY LOANS */}

//               <section className="mt-10">

//                 <h2 className="mb-6 text-2xl font-semibold">
//                   My Loans
//                 </h2>

//                 {filteredLoans.length === 0 ? (
//                   <p className="rounded-xl bg-white p-6 text-gray-500">
//                     No loans match your search.
//                   </p>
//                 ) : (
//                   <div className="grid gap-5 md:grid-cols-2">

//                     {filteredLoans.map((loan) => {
//                       const loanRequests = coolingOffRequests
//                         .filter((request) => String(request.loanId) === String(loan.loanId))
//                         .sort((a, b) => {
//                           const dateA = Date.parse(a.requestDate || a.createdAt || "") || 0;
//                           const dateB = Date.parse(b.requestDate || b.createdAt || "") || 0;
//                           if (dateA !== dateB) return dateB - dateA;
//                           return Number(b.exitRequestId) - Number(a.exitRequestId);
//                         });
//                       const latestRequest = loanRequests[0];

//                       return (

//                       <article
//                         key={loan.loanId}
//                         className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm"
//                       >

//                         <div className="flex items-start justify-between gap-3">

//                           <div>
//                             <h3 className="text-xl font-semibold">
//                               {formatLoanType(
//                                 loan.productType
//                               )}
//                             </h3>

//                             <p className="mt-2 text-xs text-gray-500">
//                               {loan.loanReference}
//                             </p>
//                           </div>

//                           <LoanStatus
//                             status={loan.loanStatus}
//                           />

//                         </div>

//                         <div className="mt-6 grid grid-cols-2 gap-5">

//                           <DetailField
//                             label="Loan ID"
//                             value={loan.loanId}
//                           />

//                           <DetailField
//                             label="Loan Tenure"
//                             value={`${loan.tenureMonths} months`}
//                           />

//                           <DetailField
//                             label="Principal Amount"
//                             value={formatCurrency(
//                               loan.principalAmount
//                             )}
//                           />

//                           <DetailField
//                             label="Outstanding Amount"
//                             value={formatCurrency(
//                               loan.outstandingAmount
//                             )}
//                           />

//                           <DetailField
//                             label="Interest Rate"
//                             value={`${loan.interestRate}%`}
//                           />

//                           <DetailField
//                             label="Cooling-Off Period"
//                             value={`${loan.coolingOffDays} days`}
//                           />

//                         </div>

//                         <div className="mt-5 rounded-xl bg-gray-50 p-4">
//                           <div className="flex flex-wrap items-center justify-between gap-2">
//                             <span className="text-sm font-medium text-gray-700">Cooling-Off Request</span>
//                             {latestRequest && <RequestStatusBadge status={latestRequest.status} />}
//                           </div>

//                           {requestListLoading ? (
//                             <p className="mt-2 text-sm text-gray-500">Loading request status...</p>
//                           ) : requestListError ? (
//                             <p role="alert" className="mt-2 text-sm text-red-700">{requestListError}</p>
//                           ) : latestRequest ? (
//                             <>
//                               <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
//                                 <div><dt className="text-gray-500">Exit Request ID</dt><dd className="font-medium">{latestRequest.exitRequestId ?? "N/A"}</dd></div>
//                                 <div><dt className="text-gray-500">Request Date</dt><dd className="font-medium">{formatDate(latestRequest.requestDate)}</dd></div>
//                               </dl>
//                               <div className="mt-3 flex flex-wrap gap-2">
//                                 <button
//                                   type="button"
//                                   onClick={() => setSelectedRequestId(latestRequest.exitRequestId)}
//                                   className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100"
//                                 >
//                                   View Exit Request Details{loanRequests.length > 1 ? ` (${loanRequests.length})` : ""}
//                                 </button>
//                                 <button
//                                   type="button"
//                                   onClick={() => handleRefreshRequest(latestRequest.exitRequestId)}
//                                   disabled={refreshingRequestId === String(latestRequest.exitRequestId)}
//                                   className="rounded-lg border border-green-700 bg-white px-3 py-2 text-sm font-semibold text-green-800 hover:bg-green-50 disabled:opacity-60"
//                                 >
//                                   {refreshingRequestId === String(latestRequest.exitRequestId) ? "Refreshing..." : "Refresh Status"}
//                                 </button>
//                               </div>
//                               {requestRefreshErrors[String(latestRequest.exitRequestId)] && (
//                                 <p role="alert" className="mt-2 text-sm text-red-700">
//                                   {requestRefreshErrors[String(latestRequest.exitRequestId)]}
//                                 </p>
//                               )}
//                             </>
//                           ) : (
//                             <p className="mt-2 text-sm text-gray-500">No cooling-off exit request submitted.</p>
//                           )}
//                         </div>

//                         <button
//                           onClick={() => {
//                             setSelectedLoanId(loan.loanId);
//                             setDetailsLoanId(loan.loanId);
//                           }}
//                           className="mt-7 w-full rounded-xl bg-black py-3 text-sm font-semibold text-white hover:bg-gray-800"
//                         >
//                           View Loan Details →
//                         </button>

//                         <button
//                           type="button"
//                           onClick={() => handleCheckEligibility(loan.loanId)}
//                           disabled={Boolean(eligibilityLoading[String(loan.loanId)])}
//                           className="mt-3 w-full rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 hover:bg-green-50 disabled:cursor-not-allowed disabled:opacity-60"
//                         >
//                           {eligibilityLoading[String(loan.loanId)]
//                             ? "Checking eligibility..."
//                             : "Check Cooling-Off Eligibility"}
//                         </button>

//                         {eligibilityErrors[String(loan.loanId)] && (
//                           <p
//                             role="alert"
//                             className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700"
//                           >
//                             {eligibilityErrors[String(loan.loanId)]}
//                           </p>
//                         )}

//                         {eligibilityResults[String(loan.loanId)] && (() => {
//                           const result = eligibilityResults[String(loan.loanId)];
//                           const hasExistingRequest =
//                             String(result.reason || "").toLowerCase() ===
//                             "a cooling-off exit request already exists";

//                           return (
//                             <section
//                               aria-live="polite"
//                               className={`mt-4 rounded-xl border p-4 ${
//                                 result.eligible
//                                   ? "border-green-200 bg-green-50 text-green-900"
//                                   : "border-red-200 bg-red-50 text-red-900"
//                               }`}
//                             >
//                               <h4 className="font-semibold">
//                                 {result.eligible
//                                   ? "Eligible for Cooling-Off Exit"
//                                   : "Not Eligible for New Cooling-Off Exit"}
//                               </h4>
//                               <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
//                                 <div>
//                                   <dt className="font-medium">Loan ID</dt>
//                                   <dd>{result.loanId ?? "N/A"}</dd>
//                                 </div>
//                                 {result.eligible && (
//                                   <div>
//                                     <dt className="font-medium">Cooling-off duration</dt>
//                                     <dd>{result.coolingOffDays != null ? `${result.coolingOffDays} days` : "N/A"}</dd>
//                                   </div>
//                                 )}
//                                 <div>
//                                   <dt className="font-medium">Cooling-off expiry</dt>
//                                   <dd>{formatDate(result.expiryDate)}</dd>
//                                 </div>
//                                 <div className="sm:col-span-2">
//                                   <dt className="font-medium">Reason</dt>
//                                   <dd>{result.reason || "N/A"}</dd>
//                                 </div>
//                               </dl>
//                               {hasExistingRequest && (
//                                 <p className="mt-3 text-sm font-medium">
//                                   An exit request has already been submitted for this loan. You cannot create another active request.
//                                 </p>
//                               )}
//                               {result.eligible && !hasExistingRequest && !latestRequest && !requestResults[String(loan.loanId)] && (
//                                 <button
//                                   type="button"
//                                   onClick={() => {
//                                     setRequestLoanId(loan.loanId);
//                                     setRequestReason("");
//                                     setRequestErrors((current) => ({ ...current, [String(loan.loanId)]: "" }));
//                                   }}
//                                   className="mt-4 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800"
//                                 >
//                                   Request Cooling-Off Exit
//                                 </button>
//                               )}
//                             </section>
//                           );
//                         })()}

//                         {requestResults[String(loan.loanId)] && (() => {
//                           const request = requestResults[String(loan.loanId)];
//                           return (
//                             <section
//                               aria-live="polite"
//                               className="mt-4 rounded-xl border border-green-200 bg-green-50 p-4 text-green-900"
//                             >
//                               <h4 className="font-semibold">Cooling-Off Exit Request Submitted</h4>
//                               <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
//                                 <div><dt className="font-medium">Exit Request ID</dt><dd>{request.exitRequestId ?? "N/A"}</dd></div>
//                                 <div><dt className="font-medium">Loan Reference</dt><dd>{request.loanReference || "N/A"}</dd></div>
//                                 <div><dt className="font-medium">Request Date</dt><dd>{formatDate(request.requestDate)}</dd></div>
//                                 <div><dt className="font-medium">Status</dt><dd>{request.status || "N/A"}</dd></div>
//                                 <div className="sm:col-span-2"><dt className="font-medium">Reason</dt><dd>{request.reason || "N/A"}</dd></div>
//                               </dl>
//                             </section>
//                           );
//                         })()}

//                         {requestErrors[String(loan.loanId)] && (
//                           <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                             {requestErrors[String(loan.loanId)]}
//                           </p>
//                         )}

//                       </article>
//                       );
//                     })}

//                   </div>
//                 )}

//               </section>

//             </>
//           )}

//         </div>
//       </section>

//       {detailsLoan && (
//         <div
//           className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
//           role="presentation"
//           onMouseDown={(event) => {
//             if (event.target === event.currentTarget) setDetailsLoanId(null);
//           }}
//         >
//           <section
//             role="dialog"
//             aria-modal="true"
//             aria-labelledby="loan-details-title"
//             className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
//           >
//             <div className="flex items-start justify-between gap-4">
//               <div>
//                 <p className="dashboard-eyebrow">Loan Information</p>
//                 <h2 id="loan-details-title" className="mt-2 text-2xl font-semibold">
//                   Complete Loan Details
//                 </h2>
//               </div>
//               <div className="flex items-center gap-3">
//                 <LoanStatus status={detailsLoan.loanStatus} />
//                 <button
//                   type="button"
//                   onClick={() => setDetailsLoanId(null)}
//                   aria-label="Close loan details"
//                   className="rounded-full px-3 py-2 text-xl text-gray-500 hover:bg-gray-100 hover:text-gray-900"
//                 >
//                   ×
//                 </button>
//               </div>
//             </div>

//             <div className="mt-8 grid gap-7 sm:grid-cols-2 lg:grid-cols-3">
//               <DetailField label="Loan ID" value={detailsLoan.loanId} />
//               <DetailField label="Loan Reference" value={detailsLoan.loanReference} />
//               <DetailField label="Loan Type" value={formatLoanType(detailsLoan.productType)} />
//               <DetailField label="Principal Amount" value={formatCurrency(detailsLoan.principalAmount)} />
//               <DetailField label="Outstanding Amount" value={formatCurrency(detailsLoan.outstandingAmount)} />
//               <DetailField label="Interest Rate" value={detailsLoan.interestRate != null ? `${detailsLoan.interestRate}%` : "N/A"} />
//               <DetailField label="Tenure" value={detailsLoan.tenureMonths != null ? `${detailsLoan.tenureMonths} months` : "N/A"} />
//               <DetailField label="Disbursement Date" value={formatDate(detailsLoan.disbursementDate)} />
//               <DetailField label="Maturity Date" value={formatDate(detailsLoan.maturityDate)} />
//               <DetailField label="Cooling-Off Duration" value={detailsLoan.coolingOffDays != null ? `${detailsLoan.coolingOffDays} days` : "N/A"} />
//               <DetailField label="Cooling-Off Expiry" value={formatDate(detailsLoan.coolingOffExpiry)} />
//               <DetailField label="Closure Date" value={formatDate(detailsLoan.closureDate)} />
//             </div>
//           </section>
//         </div>
//       )}

//       {selectedRequestId != null && (
//         <div
//           className="fixed inset-0 z-[55] flex items-center justify-center bg-black/50 p-4"
//           role="presentation"
//           onMouseDown={(event) => {
//             if (event.target === event.currentTarget) setSelectedRequestId(null);
//           }}
//         >
//           <section
//             role="dialog"
//             aria-modal="true"
//             aria-labelledby="exit-request-details-title"
//             className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
//           >
//             <div className="flex items-start justify-between gap-4">
//               <div>
//                 <p className="dashboard-eyebrow">Cooling-Off Exit</p>
//                 <h2 id="exit-request-details-title" className="mt-2 text-2xl font-semibold">
//                   Exit Request Details
//                 </h2>
//               </div>
//               <button
//                 type="button"
//                 onClick={() => setSelectedRequestId(null)}
//                 aria-label="Close request details"
//                 className="rounded-full px-3 py-2 text-xl text-gray-500 hover:bg-gray-100 hover:text-gray-900"
//               >
//                 ×
//               </button>
//             </div>

//             {requestListError && (
//               <p role="alert" className="mt-5 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                 {requestListError}
//               </p>
//             )}

//             {selectedRequestDetails && (() => {
//               const loanRequests = coolingOffRequests
//                 .filter((request) => String(request.loanId) === String(selectedRequestDetails.loanId))
//                 .sort((a, b) => {
//                   const dateA = Date.parse(a.requestDate || a.createdAt || "") || 0;
//                   const dateB = Date.parse(b.requestDate || b.createdAt || "") || 0;
//                   if (dateA !== dateB) return dateB - dateA;
//                   return Number(b.exitRequestId) - Number(a.exitRequestId);
//                 });
//               const statusMessage = requestStatusMessages[selectedRequestDetails.status];
//               const quoteStatus = settlementQuote?.quoteStatus;
//               const quoteIsAcceptedOrPaid = ["ACCEPTED", "PAID"].includes(quoteStatus);
//               const quoteIsExpired = !quoteIsAcceptedOrPaid && (
//                 quoteStatus === "EXPIRED" || (quoteStatus === "AVAILABLE" && settlementQuote?.expired === true)
//               );
//               const canGenerateFirstQuote = ["PENDING", "UNDER_REVIEW", "APPROVED"].includes(selectedRequestDetails.status);
//               const canGenerateQuote = settlementQuoteLoaded && !settlementQuoteError && !quoteIsAcceptedOrPaid && (
//                 (!settlementQuote && canGenerateFirstQuote) ||
//                 (settlementQuote && quoteIsExpired && selectedRequestDetails.status === "APPROVED")
//               );
//               const canAcceptQuote = selectedRequestDetails.status === "APPROVED" &&
//                 settlementQuote?.quoteStatus === "AVAILABLE" &&
//                 settlementQuote?.expired === false &&
//                 settlementQuote?.quoteId != null;

//               return (
//                 <>
//                   {loanRequests.length > 1 && (
//                     <div className="mt-6">
//                       <p className="mb-2 text-sm font-medium text-gray-700">Other requests for this loan</p>
//                       <div className="flex flex-wrap gap-2">
//                         {loanRequests.map((request) => (
//                           <button
//                             key={request.exitRequestId}
//                             type="button"
//                             onClick={() => setSelectedRequestId(request.exitRequestId)}
//                             className={`rounded-lg border px-3 py-2 text-sm ${
//                               String(request.exitRequestId) === String(selectedRequestId)
//                                 ? "border-green-700 bg-green-50 text-green-900"
//                                 : "border-gray-200 text-gray-700 hover:bg-gray-50"
//                             }`}
//                           >
//                             #{request.exitRequestId} · {requestStatusLabels[request.status] || request.status || "N/A"}
//                           </button>
//                         ))}
//                       </div>
//                     </div>
//                   )}

//                   <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-gray-50 p-4">
//                     <div className="flex items-center gap-3">
//                       <span className="text-sm font-medium text-gray-700">Request Status</span>
//                       <RequestStatusBadge status={selectedRequestDetails.status} />
//                     </div>
//                     <button
//                       type="button"
//                       onClick={() => handleRefreshRequest(selectedRequestId)}
//                       disabled={refreshingRequestId === String(selectedRequestId)}
//                       className="rounded-lg border border-green-700 bg-white px-4 py-2 text-sm font-semibold text-green-800 hover:bg-green-50 disabled:opacity-60"
//                     >
//                       {refreshingRequestId === String(selectedRequestId) ? "Refreshing..." : "Refresh Status"}
//                     </button>
//                   </div>

//                   {requestDetailsLoading && (
//                     <p className="mt-4 text-sm text-gray-500">Refreshing request details...</p>
//                   )}
//                   {(requestDetailsError || requestRefreshErrors[String(selectedRequestId)]) && (
//                     <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                       {requestDetailsError || requestRefreshErrors[String(selectedRequestId)]}
//                     </p>
//                   )}

//                   {statusMessage && (
//                     <p className="mt-4 rounded-lg bg-green-50 p-4 text-sm text-green-900">
//                       {statusMessage}
//                     </p>
//                   )}

//                   <div className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
//                     <DetailField label="Exit Request ID" value={selectedRequestDetails.exitRequestId} />
//                     <DetailField label="Loan ID" value={selectedRequestDetails.loanId} />
//                     <DetailField label="Loan Reference" value={selectedRequestDetails.loanReference} />
//                     <DetailField label="Reason for Exit" value={selectedRequestDetails.reason} />
//                     <DetailField label="Request Date" value={formatDate(selectedRequestDetails.requestDate || selectedRequestDetails.createdAt)} />
//                     <DetailField label="Cooling-Off Expiry Date" value={formatDate(selectedRequestDetails.expiryDate)} />
//                     <DetailField label="Assigned Officer ID" value={selectedRequestDetails.officerId} />
//                     <DetailField label="Decision Date" value={formatDate(selectedRequestDetails.decidedAt)} />
//                     <DetailField label="Last Updated Date" value={formatDate(selectedRequestDetails.updatedAt)} />
//                   </div>

//                   <section className="mt-8 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm sm:p-6">
//                     <div className="flex flex-wrap items-start justify-between gap-3">
//                       <div>
//                         <p className="dashboard-eyebrow">Settlement Quote</p>
//                         <h3 className="mt-2 text-xl font-semibold">
//                           {settlementQuote ? `Settlement Quote — #${settlementQuote.quoteId ?? "N/A"}` : "Settlement Quote"}
//                         </h3>
//                       </div>
//                       {settlementQuote?.quoteStatus && (
//                         <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
//                           quoteStatus === "ACCEPTED" || quoteStatus === "PAID"
//                             ? "bg-green-100 text-green-800"
//                             : quoteIsExpired
//                               ? "bg-red-100 text-red-800"
//                               : "bg-blue-100 text-blue-800"
//                         }`}>
//                           {quoteStatus}
//                         </span>
//                       )}
//                     </div>

//                     {settlementQuoteLoading && (
//                       <p className="mt-4 text-sm text-gray-500">Loading settlement quote...</p>
//                     )}
//                     {settlementQuoteError && (
//                       <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                         {settlementQuoteError}
//                       </p>
//                     )}
//                     {!settlementQuoteLoading && !settlementQuoteError && settlementQuoteLoaded && !settlementQuote && (
//                       <p className="mt-4 text-sm text-gray-600">No settlement quote generated yet.</p>
//                     )}

//                     {settlementQuote && (
//                       <>
//                         {quoteIsExpired && (
//                           <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
//                             <strong>Quote Expired.</strong> This quote is no longer valid. You can request a new quote if the approved exit request is still eligible.
//                           </p>
//                         )}
//                         {quoteStatus === "PAID" && (
//                           <p className="mt-4 rounded-lg bg-green-50 p-3 text-sm text-green-900">
//                             This settlement quote has been paid.
//                           </p>
//                         )}
//                         {quoteStatus === "ACCEPTED" && (
//                           <section className="mt-4 rounded-xl border border-green-200 bg-green-50 p-4 text-green-950">
//                             <h4 className="font-semibold">Settlement Quote Accepted Successfully</h4>
//                             <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
//                               <div><dt className="font-medium">Quote ID</dt><dd>{settlementQuote.quoteId ?? "N/A"}</dd></div>
//                               <div><dt className="font-medium">Loan Reference</dt><dd>{selectedRequestDetails.loanReference || "N/A"}</dd></div>
//                               <div><dt className="font-medium">Quote Status</dt><dd>{settlementQuote.quoteStatus}</dd></div>
//                               <div><dt className="font-medium">Accepted At</dt><dd>{formatDateTime(settlementQuote.acceptedAt)}</dd></div>
//                             </dl>
//                             <div className="mt-4 rounded-lg bg-white/80 p-3">
//                               <p className="text-sm font-medium">Total Accepted Amount</p>
//                               <p className="mt-1 text-xl font-bold">{formatQuoteCurrency(settlementQuote.totalAmount)}</p>
//                             </div>
//                             <p className="mt-4 text-sm font-semibold">Next Step: Awaiting Payment Integration</p>
//                             <p className="mt-1 text-sm">Your settlement amount has been accepted. Payment processing will be available in a later step.</p>
//                           </section>
//                         )}
//                         <dl className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
//                           <DetailField label="Quote ID" value={settlementQuote.quoteId} />
//                           <DetailField label="Loan ID" value={settlementQuote.loanId} />
//                           <DetailField label="Exit Request ID" value={settlementQuote.exitRequestId} />
//                           <DetailField label="Quote Type" value={settlementQuote.quoteType} />
//                           <DetailField label="Principal Amount" value={formatQuoteCurrency(settlementQuote.principalAmount)} />
//                           <DetailField label="Interest Amount" value={formatQuoteCurrency(settlementQuote.interestAmount)} />
//                           <DetailField label="Charges Amount" value={formatQuoteCurrency(settlementQuote.chargesAmount)} />
//                           <DetailField label="Tax Amount" value={formatQuoteCurrency(settlementQuote.taxAmount)} />
//                           <DetailField label="Quote Generated Date" value={formatDateTime(settlementQuote.createdAt)} />
//                           <DetailField label="Valid Until" value={formatDateTime(settlementQuote.validUntil)} />
//                           <DetailField label="Accepted At" value={formatDateTime(settlementQuote.acceptedAt)} />
//                         </dl>
//                         <div className="mt-5 rounded-xl bg-lime-50 p-4">
//                           <p className="text-sm font-medium text-gray-700">Total Settlement Amount</p>
//                           <p className="mt-1 text-2xl font-bold text-[#102018]">
//                             {formatQuoteCurrency(settlementQuote.totalAmount)}
//                           </p>
//                         </div>
//                       </>
//                     )}

//                     {canGenerateQuote && (
//                       <button
//                         type="button"
//                         onClick={handleGenerateSettlementQuote}
//                         disabled={quoteGenerating}
//                         className="mt-5 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-60"
//                       >
//                         {quoteGenerating
//                           ? "Generating quote..."
//                           : quoteIsExpired
//                             ? "Generate New Quote"
//                             : "Generate Settlement Quote"}
//                       </button>
//                     )}

//                     {canAcceptQuote && (
//                       <button
//                         type="button"
//                         onClick={() => {
//                           setAcceptanceAcknowledged(false);
//                           setAcceptanceError("");
//                           setAcceptanceConfirmationOpen(true);
//                         }}
//                         className="ml-0 mt-4 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 sm:ml-3"
//                       >
//                         Accept Settlement Quote
//                       </button>
//                     )}
//                     {acceptanceError && !acceptanceConfirmationOpen && (
//                       <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                         {acceptanceError}
//                       </p>
//                     )}
//                     {quoteRefreshError && (
//                       <p role="alert" className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
//                         {quoteRefreshError}
//                       </p>
//                     )}
//                   </section>
//                 </>
//               );
//             })()}

//             {requestDetailsLoading && !selectedRequestDetails && (
//               <p className="mt-6 text-gray-500">Loading request details...</p>
//             )}
//             {requestDetailsError && !selectedRequestDetails && (
//               <p role="alert" className="mt-6 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                 {requestDetailsError}
//               </p>
//             )}
//           </section>
//         </div>
//       )}

//       {acceptanceConfirmationOpen && settlementQuote && selectedRequestDetails && (
//         <div
//           className="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 p-4"
//           role="presentation"
//           onMouseDown={(event) => {
//             if (event.target === event.currentTarget && !acceptingQuote) {
//               setAcceptanceConfirmationOpen(false);
//               setAcceptanceAcknowledged(false);
//               setAcceptanceError("");
//             }
//           }}
//         >
//           <section
//             role="dialog"
//             aria-modal="true"
//             aria-labelledby="accept-settlement-title"
//             className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
//           >
//             <h2 id="accept-settlement-title" className="text-2xl font-semibold">
//               Confirm Settlement Quote Acceptance
//             </h2>
//             <p className="mt-2 text-sm text-gray-600">
//               Review the settlement details before confirming. This action cannot be repeated once accepted.
//             </p>

//             <div className="mt-6 grid gap-5 rounded-xl bg-gray-50 p-5 sm:grid-cols-2">
//               <DetailField label="Loan Reference" value={selectedRequestDetails.loanReference} />
//               <DetailField label="Exit Request ID" value={selectedRequestDetails.exitRequestId} />
//               <DetailField label="Quote ID" value={settlementQuote.quoteId} />
//               <DetailField label="Principal Amount" value={formatQuoteCurrency(settlementQuote.principalAmount)} />
//               <DetailField label="Interest Amount" value={formatQuoteCurrency(settlementQuote.interestAmount)} />
//               <DetailField label="Charges" value={formatQuoteCurrency(settlementQuote.chargesAmount)} />
//               <DetailField label="Tax" value={formatQuoteCurrency(settlementQuote.taxAmount)} />
//               <DetailField label="Quote Valid Until" value={formatDateTime(settlementQuote.validUntil)} />
//               <div className="rounded-lg bg-lime-100 p-3 sm:col-span-2">
//                 <p className="text-sm font-medium text-gray-700">Total Settlement Amount</p>
//                 <p className="mt-1 text-2xl font-bold text-[#102018]">
//                   {formatQuoteCurrency(settlementQuote.totalAmount)}
//                 </p>
//               </div>
//             </div>

//             <label className="mt-5 flex cursor-pointer items-start gap-3 text-sm text-gray-700">
//               <input
//                 type="checkbox"
//                 checked={acceptanceAcknowledged}
//                 onChange={(event) => {
//                   setAcceptanceAcknowledged(event.target.checked);
//                   setAcceptanceError("");
//                 }}
//                 className="mt-1 h-4 w-4 accent-lime-700"
//               />
//               <span>I understand and accept the settlement amount displayed above.</span>
//             </label>

//             {acceptanceError && (
//               <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                 {acceptanceError}
//               </p>
//             )}

//             <div className="mt-6 flex flex-col-reverse justify-end gap-3 sm:flex-row">
//               <button
//                 type="button"
//                 onClick={() => {
//                   setAcceptanceConfirmationOpen(false);
//                   setAcceptanceAcknowledged(false);
//                   setAcceptanceError("");
//                 }}
//                 disabled={acceptingQuote}
//                 className="rounded-xl border border-gray-300 px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-60"
//               >
//                 Cancel
//               </button>
//               <button
//                 type="button"
//                 onClick={handleAcceptSettlementQuote}
//                 disabled={!acceptanceAcknowledged || acceptingQuote}
//                 className="rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
//               >
//                 {acceptingQuote ? "Confirming acceptance..." : "Confirm Acceptance"}
//               </button>
//             </div>
//           </section>
//         </div>
//       )}

//       {requestLoanId != null && (() => {
//         const requestLoan = loans.find((loan) => String(loan.loanId) === String(requestLoanId));
//         const eligibility = eligibilityResults[String(requestLoanId)];
//         if (!requestLoan || !eligibility) return null;

//         return (
//           <div
//             className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
//             role="presentation"
//             onMouseDown={(event) => {
//               if (event.target === event.currentTarget && !requestSubmitting) setRequestLoanId(null);
//             }}
//           >
//             <section
//               role="dialog"
//               aria-modal="true"
//               aria-labelledby="cooling-off-request-title"
//               className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8"
//             >
//               <h2 id="cooling-off-request-title" className="text-2xl font-semibold">
//                 Request Cooling-Off Exit
//               </h2>
//               <p className="mt-2 text-sm text-gray-500">
//                 Submit a request for this eligible loan. The lender will review it.
//               </p>

//               <form onSubmit={handleSubmitCoolingOffRequest} className="mt-6 space-y-4">
//                 <div className="grid gap-4 sm:grid-cols-2">
//                   <label className="block text-sm font-medium text-gray-700">
//                     Loan ID
//                     <input readOnly value={requestLoan.loanId} className="mt-2 w-full rounded-lg border bg-gray-50 px-3 py-2.5 text-gray-700" />
//                   </label>
//                   <label className="block text-sm font-medium text-gray-700">
//                     Loan Reference
//                     <input readOnly value={requestLoan.loanReference || "N/A"} className="mt-2 w-full rounded-lg border bg-gray-50 px-3 py-2.5 text-gray-700" />
//                   </label>
//                 </div>
//                 <label className="block text-sm font-medium text-gray-700">
//                   Cooling-Off Expiry Date
//                   <input readOnly value={formatDate(eligibility.expiryDate)} className="mt-2 w-full rounded-lg border bg-gray-50 px-3 py-2.5 text-gray-700" />
//                 </label>
//                 <label className="block text-sm font-medium text-gray-700">
//                   Reason for Exit
//                   <textarea
//                     value={requestReason}
//                     onChange={(event) => setRequestReason(event.target.value.slice(0, 1000))}
//                     maxLength={1000}
//                     required
//                     rows={4}
//                     placeholder="Tell us why you would like to exit this loan."
//                     className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2.5"
//                   />
//                   <span className="mt-1 block text-right text-xs text-gray-500">
//                     {requestReason.length}/1000
//                   </span>
//                 </label>
//                 {requestErrors[String(requestLoanId)] && (
//                   <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
//                     {requestErrors[String(requestLoanId)]}
//                   </p>
//                 )}
//                 <div className="flex flex-col-reverse justify-end gap-3 pt-2 sm:flex-row">
//                   <button
//                     type="button"
//                     onClick={() => setRequestLoanId(null)}
//                     disabled={requestSubmitting}
//                     className="rounded-xl border border-gray-300 px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-60"
//                   >
//                     Cancel
//                   </button>
//                   <button
//                     type="submit"
//                     disabled={requestSubmitting || !requestReason.trim()}
//                     className="rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-60"
//                   >
//                     {requestSubmitting ? "Submitting..." : "Submit Request"}
//                   </button>
//                 </div>
//               </form>
//             </section>
//           </div>
//         );
//       })()}

//     </main>
//   );
// }

// export default BorrowerDashboard;
