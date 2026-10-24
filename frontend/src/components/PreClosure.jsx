import { useEffect, useRef, useState } from "react";
import { preClosureRequest } from "../api";
import SettlementPayment from "./SettlementPayment";

const money = (amount) => amount == null ? "N/A" : new Intl.NumberFormat("en-IN", {
  style: "currency", currency: "INR", maximumFractionDigits: 2,
}).format(amount);
const date = (value) => value ? String(value).replace("T", " ").slice(0, 16) : "N/A";
const label = (value) => String(value || "Unknown").replaceAll("_", " ");
const button = "rounded-xl border border-green-700 bg-white px-4 py-3 text-sm font-semibold text-green-800 disabled:cursor-not-allowed disabled:opacity-50";
const messages = {
  PENDING: "Your request is awaiting assignment to a loan closure officer.",
  UNDER_REVIEW: "A loan closure officer is reviewing this request.",
  APPROVED: "Approved. You can now generate and accept a settlement quotation.",
  PAYMENT_PENDING: "Quotation accepted. Complete payment before its deadline. After payment verification, closure will be processed automatically.",
  COMPLETED: "Pre-closure completed. The loan has been closed with zero outstanding.",
  REJECTED: "This request was rejected. Contact your bank for clarification.",
  CANCELLED: "This request has been cancelled.",
};

function Status({ value }) {
  const tone = ["APPROVED", "COMPLETED", "PAID"].includes(value) ? "bg-green-100 text-green-800"
    : value === "REJECTED" ? "bg-red-100 text-red-800" : "bg-amber-100 text-amber-900";
  return <span className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${tone}`}>{label(value)}</span>;
}

function Field({ title, children }) {
  return <div className="min-w-0"><dt className="text-sm text-gray-500">{title}</dt>
    <dd className="mt-1 font-semibold" style={{ overflowWrap: "anywhere" }}>{children ?? "N/A"}</dd></div>;
}

// One shared screen keeps the borrower and officer views on the same request/quote model.
export default function PreClosure({ session, officer = false, loans = [], initialLoanId,
  loansLoading = false, loansError = "", onLoansRefresh }) {
  const [requests, setRequests] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [details, setDetails] = useState(null);
  const [loanId, setLoanId] = useState(initialLoanId == null ? "" : String(initialLoanId));
  const [reason, setReason] = useState("");
  const [rejection, setRejection] = useState("");
  const [acknowledged, setAcknowledged] = useState(false);
  const [loading, setLoading] = useState(true);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [assignmentFilter, setAssignmentFilter] = useState("ALL");
  const [decisionIntent, setDecisionIntent] = useState(null);
  const submitting = useRef(false);
  const mounted = useRef(false);
  const detailsAnchor = useRef(null);
  const selected = loans.find((loan) => String(loan.loanId) === loanId && loan.loanStatus === "ACTIVE");
  const quote = details?.quote;

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (selectedId != null) detailsAnchor.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [selectedId]);

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError("");
    async function load() {
      try {
        let data = await preClosureRequest(session.token, officer ? "/officer/requests?includeHistory=true" : "");
        if (!Array.isArray(data)) throw new Error("Invalid pre-closure request list received.");
        if (officer) {
          const results = await Promise.allSettled(data.map((id) =>
            preClosureRequest(session.token, `/officer/${encodeURIComponent(id)}`)));
          const failures = results.filter((result) => result.status === "rejected");
          data = results.filter((result) => result.status === "fulfilled").map((result) => result.value);
          if (current && failures.length) setError(`${failures.length} request(s) could not be loaded. ${failures[0].reason.message}`);
        }
        if (current) setRequests(data);
      } catch (failure) {
        if (current) setError(failure.message);
      } finally {
        if (current) setLoading(false);
      }
    }
    load();
    return () => { current = false; };
  }, [session.token, officer, revision]);

  useEffect(() => {
    if (selectedId == null) return;
    let current = true;
    setDetailsLoading(true);
    preClosureRequest(session.token, `${officer ? "/officer" : ""}/${encodeURIComponent(selectedId)}`)
      .then((data) => { if (current) setDetails(data); })
      .catch((failure) => { if (current) setActionError(failure.message); })
      .finally(() => { if (current) setDetailsLoading(false); });
    return () => { current = false; };
  }, [session.token, officer, selectedId, revision]);

  function open(record) {
    if (submitting.current) return;
    setSelectedId(record.closureRequestId);
    setDetails(record);
    setActionError(""); setNotice(""); setAcknowledged(false); setRejection("");
    setDecisionIntent(null);
  }

  async function action(path, body, success) {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true); setActionError(""); setNotice("");
    try {
      const result = await preClosureRequest(session.token, path, {
        method: "POST", ...(body ? { body: JSON.stringify(body) } : {}),
      });
      if (!mounted.current) return;
      if (path === "") {
        setSelectedId(result.closureRequestId); setDetails(result); setReason("");
      }
      setAcknowledged(false);
      setDecisionIntent(null);
      setNotice(success);
      setRevision((value) => value + 1);
    } catch (failure) {
      if (mounted.current) setActionError(failure.message);
    } finally {
      submitting.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  async function refreshAfterPayment() {
    const data = await preClosureRequest(session.token, `/${encodeURIComponent(selectedId)}`);
    if (!mounted.current) return;
    setDetails(data);
    setRevision((value) => value + 1);
    onLoansRefresh?.();
  }

  const expiry = quote?.validUntil ? new Date(quote.validUntil).getTime() : NaN;
  const expired = Boolean(quote?.expired) || (Number.isFinite(expiry) && now >= expiry);
  const assigned = details?.officerId != null && Number(details.officerId) === Number(session.userId);
  const canReview = officer && assigned && details?.status === "UNDER_REVIEW";
  const activeLoans = loans.filter((loan) => loan.loanStatus === "ACTIVE");
  const reasonBytes = new TextEncoder().encode(reason.trim()).length;
  const rejectionBytes = new TextEncoder().encode(rejection.trim()).length;
  const quotePath = `/${encodeURIComponent(selectedId)}`;
  const visibleRequests = requests.filter((record) => {
    const mine = Number(record.officerId) === Number(session.userId);
    const matchesAssignment = assignmentFilter === "ALL" || (assignmentFilter === "MINE" && mine)
      || (assignmentFilter === "UNASSIGNED" && record.officerId == null);
    const text = [record.closureRequestId, record.loanId, record.loan?.borrowerName,
      record.loan?.borrowerId, record.loan?.loanReference, record.loan?.productType].join(" ").toLowerCase();
    return (!officer || matchesAssignment) && (statusFilter === "ALL" || statusFilter === record.status)
      && text.includes(search.trim().toLowerCase());
  });
  const summary = [
    ["Available Requests", requests.length],
    ["Awaiting Review", requests.filter((record) => ["PENDING", "UNDER_REVIEW"].includes(record.status)).length],
    ["Awaiting Payment / Closure", requests.filter((record) => ["APPROVED", "PAYMENT_PENDING"].includes(record.status)).length],
    ["Completed", requests.filter((record) => record.status === "COMPLETED").length],
  ];

  return <section className="min-w-0 space-y-6" aria-label="Loan pre-closure">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div><p className="dashboard-eyebrow">Loan Closure</p><h2 className="mt-2 text-3xl font-semibold">Pre-closure / Foreclosure</h2>
        <p className="dashboard-muted mt-2">{officer ? "Review early repayment requests before quotation and payment." : "Repay your outstanding loan before its scheduled maturity."}</p></div>
      <button type="button" className={button} disabled={busy || loading || detailsLoading}
        onClick={() => { setActionError(""); setDecisionIntent(null); setRevision((value) => value + 1); onLoansRefresh?.(); }}>Refresh Status</button>
    </header>
    <div className="rounded-2xl bg-[#24351c] p-6 text-white">
      <p className="font-semibold">Request → Officer approval → Quotation → Acceptance → Payment → Closure</p>
      <p className="mt-2 text-sm">{officer ? "Approval authorizes quotation. It does not close the loan." : "Charges depend on your loan product. Review the full quotation before accepting."}</p>
    </div>

    {officer && <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Pre-closure summary" aria-busy={loading}>
      {summary.map(([title, count]) => <div className="officer-summary-card" key={title}><p>{title}</p><h3>{loading ? "…" : count}</h3></div>)}
    </section>}

    {!officer && <form className="space-y-4 rounded-2xl border border-gray-200 bg-white p-6" onSubmit={(event) => {
      event.preventDefault();
      if (selected && reason.trim() && reasonBytes <= 1000) action("", { loanId: Number(loanId), reason: reason.trim() }, "Pre-closure request submitted for officer review.");
    }}>
      <h3 className="text-xl font-semibold">Request Pre-closure</h3>
      {loansLoading && <p>Loading your loans...</p>}
      {loansError && <p role="alert" className="text-red-700">{loansError}</p>}
      {!loansLoading && !loansError && !activeLoans.length && <p>No active loans are available for a new request.</p>}
      <label className="block text-sm font-semibold">Loan
        <select className="mt-2 w-full rounded-xl border border-gray-300 bg-white p-3" value={loanId}
          disabled={busy || loansLoading} required onChange={(event) => setLoanId(event.target.value)}>
          <option value="">Select an active loan</option>
          {activeLoans.map((loan) => <option key={loan.loanId} value={loan.loanId}>
            {label(loan.productType)} · Loan #{loan.loanId} · {money(loan.outstandingAmount)}
          </option>)}
        </select>
      </label>
      {selected && <dl className="grid gap-4 sm:grid-cols-2">
        <Field title="Loan Reference">{selected.loanReference}</Field><Field title="Outstanding Amount">{money(selected.outstandingAmount)}</Field>
      </dl>}
      <label className="block text-sm font-semibold">Reason for pre-closure
        <textarea required rows={3} maxLength={1000} value={reason} disabled={busy} onChange={(event) => setReason(event.target.value)}
          className="mt-2 w-full rounded-xl border border-gray-300 p-3" placeholder="Tell us why you want to repay early" />
      </label>
      {reasonBytes > 1000 && <p role="alert" className="text-red-700">Shorten your reason; it exceeds the allowed length.</p>}
      <p className="text-sm text-gray-500">Eligibility is checked on submission. A loan cannot have an active cooling-off and pre-closure request together.</p>
      <button type="submit" className={button} disabled={busy || loansLoading || !selected || !reason.trim() || reasonBytes > 1000}>
        {busy ? "Please wait..." : "Submit Pre-closure Request"}</button>
    </form>}

    {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{error}</p>}
    {actionError && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{actionError}</p>}
    {notice && <p role="status" className="rounded-xl bg-green-50 p-4 text-green-800">{notice}</p>}
    <section aria-label="Pre-closure requests">
      <h3 className="mb-4 text-xl font-semibold">{officer ? "Requests Available to You" : "Your Pre-closure Requests"}</h3>
      {officer && <div className="mb-5 flex flex-wrap gap-3">
        <input type="search" aria-label="Search pre-closure requests" placeholder="Search borrower, loan reference or request ID"
          value={search} onChange={(event) => setSearch(event.target.value)} className="min-w-0 flex-1 rounded-xl border border-gray-300 bg-white p-3" />
        <select aria-label="Filter pre-closure status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}
          className="rounded-xl border border-gray-300 bg-white p-3">
          <option value="ALL">All Statuses</option>
          {["PENDING", "UNDER_REVIEW", "APPROVED", "PAYMENT_PENDING", "COMPLETED", "REJECTED", "CANCELLED"].map((status) => <option key={status} value={status}>{label(status)}</option>)}
        </select>
        <select aria-label="Filter assignment" value={assignmentFilter} onChange={(event) => setAssignmentFilter(event.target.value)}
          className="rounded-xl border border-gray-300 bg-white p-3">
          <option value="ALL">All Available Requests</option><option value="MINE">Assigned to Me</option><option value="UNASSIGNED">Unassigned</option>
        </select>
      </div>}
      {loading && <p>Loading requests...</p>}
      {!loading && !error && !visibleRequests.length && <p className="rounded-xl bg-white p-6">{requests.length ? "No requests match these filters." : "No pre-closure requests to display."}</p>}
      <div className="grid gap-4 lg:grid-cols-2">{visibleRequests.map((record) => <article key={record.closureRequestId} className="min-w-0 rounded-2xl border border-gray-200 bg-white p-5">
        <div className="flex flex-wrap items-center justify-between gap-3"><h4 className="font-semibold">Request #{record.closureRequestId} · Loan #{record.loanId}</h4><Status value={record.status} /></div>
        {record.loan && <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <Field title="Borrower">{record.loan.borrowerName}</Field><Field title="Loan Type">{label(record.loan.productType)}</Field>
          <Field title="Loan Reference">{record.loan.loanReference}</Field><Field title="Outstanding">{money(record.loan.outstandingAmount)}</Field>
        </dl>}
        <p className="mt-3 text-sm text-gray-500">Requested: {date(record.createdAt)}</p>
        <p className="mt-2 text-sm">Assigned officer: {record.officerId ?? "Unassigned"}</p>
        {record.quote && <p className="mt-2 font-semibold">Settlement: {money(record.quote.totalAmount)}</p>}
        <button type="button" className={`${button} mt-4 w-full`} disabled={busy} onClick={() => open(record)}>View Request Details</button>
      </article>)}</div>
    </section>

    {details && selectedId != null && <section ref={detailsAnchor} className="space-y-5 rounded-2xl border border-gray-200 bg-white p-6" aria-label="Pre-closure request details">
      <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-2xl font-semibold">Pre-closure Request #{details.closureRequestId}</h3>
        <button type="button" className={button} disabled={busy} onClick={() => { setSelectedId(null); setDetails(null); }}>Close Details</button></div>
      <Status value={details.status} />
      {detailsLoading && <p role="status">Refreshing request details...</p>}
      <p className="rounded-xl bg-green-50 p-4 text-green-900">{officer && details.status === "PENDING"
        ? "Assign this request to yourself to review the borrower and loan details."
        : messages[details.status] || "Refresh to see the latest request status."}</p>
      <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        <Field title="Loan ID">{details.loanId}</Field><Field title="Request Date">{date(details.createdAt)}</Field>
        <Field title="Assigned Officer">{details.officerId ?? "Unassigned"}</Field><Field title="Reason for Exit">{details.reason}</Field>
      </dl>
      {details.loan && <dl className="grid gap-5 border-t border-gray-200 pt-5 sm:grid-cols-2 lg:grid-cols-3">
        <Field title="Borrower Name">{details.loan.borrowerName}</Field><Field title="Borrower ID">{details.loan.borrowerId}</Field>
        <Field title="Loan Reference">{details.loan.loanReference}</Field><Field title="Loan Type">{label(details.loan.productType)}</Field>
        <Field title="Principal Amount">{money(details.loan.principalAmount)}</Field><Field title="Outstanding Amount">{money(details.loan.outstandingAmount)}</Field>
        <Field title="Interest Rate">{details.loan.interestRate == null ? "N/A" : `${details.loan.interestRate}%`}</Field>
        <Field title="Loan Status">{label(details.loan.loanStatus)}</Field><Field title="Maturity Date">{date(details.loan.maturityDate)}</Field>
        <Field title="Closure Date">{date(details.loan.closureDate)}</Field>
      </dl>}
      {details.decisionRemarks && <div className="rounded-xl bg-red-50 p-4 text-red-800" style={{ overflowWrap: "anywhere" }}>
        <h4 className="font-semibold">Officer Rejection Reason</h4><p className="mt-2 whitespace-pre-wrap">{details.decisionRemarks}</p>
      </div>}

      {officer && <div className="space-y-4 border-t border-gray-200 pt-5">
        <h4 className="text-xl font-semibold">Officer Review</h4>
        {assigned && <p className="text-sm text-green-800">This request is assigned to you.</p>}
        {["PENDING", "UNDER_REVIEW"].includes(details.status) && !assigned && details.officerId == null &&
          <button type="button" className={button} disabled={busy || detailsLoading}
            onClick={() => action(`/officer/${selectedId}/assign`, null, "Request assigned to you for review.")}>Assign to Me</button>}
        {canReview && <>
          <button type="button" className={button} disabled={busy || detailsLoading}
            onClick={() => setDecisionIntent("approve")}>Approve Pre-closure</button>
          <label className="block text-sm font-semibold">Rejection reason
            <textarea rows={3} maxLength={1000} value={rejection} disabled={busy} onChange={(event) => setRejection(event.target.value)}
              className="mt-2 w-full rounded-xl border border-gray-300 p-3" />
          </label>
          {rejectionBytes > 1000 && <p role="alert" className="text-sm text-red-700">Shorten the rejection reason; it exceeds the allowed length.</p>}
          <button type="button" className="rounded-xl border border-red-700 px-4 py-3 text-sm font-semibold text-red-700 disabled:opacity-50"
            disabled={busy || detailsLoading || !rejection.trim() || rejectionBytes > 1000}
            onClick={() => setDecisionIntent("reject")}>Reject Request</button>
          {decisionIntent && <div className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-5" role="group" aria-label="Confirm officer decision">
            <h5 className="font-semibold">Confirm {decisionIntent === "approve" ? "approval" : "rejection"} of Request #{selectedId}</h5>
            <p className="text-sm">{decisionIntent === "approve" ? "The borrower will be able to generate and accept a settlement quotation, then pay." : "The borrower will not be able to generate a quotation or pay for this request."}</p>
            {decisionIntent === "reject" && <p className="whitespace-pre-wrap text-sm" style={{ overflowWrap: "anywhere" }}>{rejection.trim()}</p>}
            <div className="flex flex-wrap gap-3">
              <button type="button" className={button} disabled={busy || detailsLoading || (decisionIntent === "reject" && (!rejection.trim() || rejectionBytes > 1000))}
                onClick={() => action(`/officer/${selectedId}/${decisionIntent}`, decisionIntent === "reject" ? { reason: rejection.trim() } : null,
                  decisionIntent === "approve" ? "Approved. The borrower may now generate a quotation." : "Request rejected.")}>{busy ? "Saving..." : "Confirm Decision"}</button>
              <button type="button" className={button} disabled={busy} onClick={() => setDecisionIntent(null)}>Cancel</button>
            </div>
          </div>}
        </>}
        {!canReview && !["PENDING", "UNDER_REVIEW"].includes(details.status) && <p className="text-sm text-gray-600">
          {["APPROVED", "PAYMENT_PENDING"].includes(details.status)
            ? "The review decision is recorded. Payment verification and final loan closure proceed automatically after the borrower pays."
            : details.status === "COMPLETED" ? "Loan closure is complete. This request is available here for reference."
              : "This request is closed for further review decisions."}
        </p>}
      </div>}

      <section className="space-y-4 border-t border-gray-200 pt-5" aria-label="Pre-closure quotation">
        <h4 className="text-xl font-semibold">Settlement Quotation</h4>
        {officer && <p className="text-sm text-gray-600">Payment: <strong>{quote?.quoteStatus === "PAID" ? "Verified" : "Not yet confirmed"}</strong>
          {quote?.quoteStatus === "PAID" && details.status !== "COMPLETED" && " — final loan closure is pending. Refresh status shortly."}</p>}
        {!quote && <p>{details.status === "APPROVED"
          ? "The request is approved. The borrower can now create a quotation."
          : "No quotation generated yet. Quotation is available only after officer approval."}</p>}
        {quote && <>
          <Status value={quote.quoteStatus} />
          <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            <Field title="Quote ID">{quote.quoteId}</Field><Field title="Outstanding Principal at Quotation">{money(quote.principalAmount)}</Field>
            <Field title={quote.calculation ? `Accrued Interest (${quote.calculation.interestDays} days)` : "Interest"}>{money(quote.interestAmount)}</Field>
            {!quote.legacyFeePolicy && <Field title="Permitted Foreclosure Charges">{money(quote.chargesAmount)}</Field>}
            <Field title={quote.legacyFeePolicy ? "Tax Included in Quotation" : "Tax on Foreclosure Charges"}>{money(quote.taxAmount)}</Field><Field title="Valid Until">{date(quote.validUntil)}</Field>
          </dl>
          {quote.legacyFeePolicy && <p className="text-sm text-amber-900">
            The total includes all charges saved with this earlier quotation. No additional fee is being added.
            {quote.quoteStatus === "AVAILABLE" && " Generate a fresh quotation before accepting."}
          </p>}
          {quote.calculation && <div className="space-y-4 rounded-xl border border-gray-200 p-5">
            <h5 className="font-semibold">How this settlement was calculated</h5>
            <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              <Field title="Interest Paid Through">{date(quote.calculation.interestPaidThrough)}</Field>
              <Field title="Interest Calculated To">{date(quote.calculation.settlementDate)}</Field>
              <Field title="Annual Interest Rate">{quote.calculation.annualInterestRate}%</Field>
              <Field title="Interest Basis">{quote.calculation.dayCountBasis}</Field>
              <Field title="Foreclosure Rule">{quote.calculation.chargeType === "PERCENTAGE"
                ? `${quote.calculation.chargeValue}% of outstanding principal`
                : quote.calculation.chargeType === "FIXED" ? money(quote.calculation.chargeValue) : "No foreclosure fee"}</Field>
              <Field title="Tax Rate on Charges">{quote.calculation.taxRate}%</Field>
            </dl>
            <p className="text-sm text-gray-600">{quote.calculation.chargePolicyReason}</p>
            <p className="text-sm text-gray-600">Interest = outstanding principal × annual rate × unpaid interest days ÷ 36,500.
              {quote.legacyFeePolicy ? " This earlier quotation retains its original charges and tax basis."
                : " Future months of interest are excluded. Tax applies to foreclosure charges only."}</p>
          </div>}
          {details.status === "COMPLETED" && <p className="text-sm text-gray-600">
            This paid quotation records the amounts settled. The current loan balance is zero after payment.
          </p>}
          <div className="rounded-xl bg-green-50 p-5"><p>Total Settlement Amount</p><p className="mt-2 text-2xl font-semibold">{money(quote.totalAmount)}</p></div>
          {expired && !["PAID"].includes(quote.quoteStatus) && <p className="text-amber-900">The payment deadline has passed. No new checkout can be opened.
            {quote.quoteStatus === "AVAILABLE" ? " Generate a fresh quotation before accepting." : " An accepted quotation cannot be replaced. Contact the bank for assistance; refresh payment status if you already paid."}</p>}
        </>}
        {!officer && details.status === "APPROVED" && (!quote || (quote.quoteStatus === "AVAILABLE" && (expired || quote.legacyFeePolicy))) &&
          <button type="button" className={button} disabled={busy || detailsLoading}
            onClick={() => action(`${quotePath}/quote`, null, "Quotation generated. Review all amounts before accepting.")}>{quote ? "Regenerate Quotation" : "Create Quotation"}</button>}
        {!officer && details.status === "APPROVED" && quote?.quoteStatus === "AVAILABLE" && !expired && !quote.legacyFeePolicy && <>
          <label className="flex items-start gap-3 text-sm"><input type="checkbox" checked={acknowledged} disabled={busy}
            onChange={(event) => setAcknowledged(event.target.checked)} className="mt-1" />
            I have reviewed the settlement amount, charges and payment deadline and accept this quotation.</label>
          <button type="button" className={button} disabled={busy || detailsLoading || !acknowledged}
            onClick={() => action(`${quotePath}/accept`, null, "Quotation accepted. You can now pay the settlement amount.")}>Accept Quotation</button>
        </>}
        {!officer && quote && ["ACCEPTED", "PAID"].includes(quote.quoteStatus) &&
          <SettlementPayment key={`${details.closureRequestId}:${quote.quoteId}`} token={session.token} quoteId={quote.quoteId}
            preClosureRequestId={details.closureRequestId} username={session.username}
            approved={details.status === "PAYMENT_PENDING" && quote.quoteStatus === "ACCEPTED" && !expired}
            onRefresh={refreshAfterPayment} />}
      </section>
    </section>}
  </section>;
}
