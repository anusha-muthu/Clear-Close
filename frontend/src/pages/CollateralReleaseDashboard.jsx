import { useEffect, useRef, useState } from "react";
import { getMyCollateralRequests, submitCollateralRequest } from "../collateralApi";

function displayDate(value) {
  return value ? String(value).slice(0, 10) : "Not available";
}

function toDisplayRequest(row) {
  if (row.requestId == null) throw new Error("The saved request did not include a request ID. Refresh before retrying.");
  return {
    ...row,
    expectedReleaseDate: displayDate(row.expectedReleaseDate),
    createdAt: displayDate(row.createdAt),
    updatedAt: displayDate(row.updatedAt),
    releaseReference: row.releaseReference || "Not assigned",
  };
}

const emptyForm = {
  collateralId: "",
  loanAccountId: "",
  reason: "LOAN_FULLY_PAID",
  expectedReleaseDate: "",
  releaseReference: "",
};

function StatusBadge({ status }) {
  const color = status === "PENDING" ? "bg-orange-100 text-orange-700"
    : ["APPROVED", "RELEASE_IN_PROGRESS"].includes(status) ? "bg-sky-100 text-sky-700"
    : status === "RELEASED" ? "bg-lime-100 text-lime-800"
    : "bg-gray-100 text-gray-700";
  return <span className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${color}`}>{status.replaceAll("_", " ")}</span>;
}

export default function CollateralReleaseDashboard({ session }) {
  const isBorrowerUser = Boolean(session?.token)
    && session?.accountType === "BORROWER" && session?.role === "BORROWER";
  const isOfficerUser = Boolean(session?.token)
    && session?.accountType === "OFFICER"
    && ["LOAN_CLOSURE_OFFICER", "COLLATERAL_OFFICER", "ADMIN"].includes(session?.role);
  const isAdmin = isOfficerUser && session.role === "ADMIN";
  const currentUserId = session?.userId == null ? null : String(session.userId);
  const [requests, setRequests] = useState([]);
  const [selectedRequestId, setSelectedRequestId] = useState(null);
  const [isNewRequestModalOpen, setIsNewRequestModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [newRequest, setNewRequest] = useState({ ...emptyForm });
  const [formError, setFormError] = useState("");
  const dialogRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const token = session?.token;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setRequests([]);
    setSelectedRequestId(null);
    setLoading(true);
    setLoadError("");
    if (!isBorrowerUser) {
      setLoading(false);
      if (isOfficerUser) setLoadError("Officer queue integration needs an authorized assigned-request endpoint in Collateral.");
      return () => controller.abort();
    }
    getMyCollateralRequests({ token }, controller.signal)
      .then((rows) => {
        if (!Array.isArray(rows)) throw new Error("Unexpected request-list response from Collateral.");
        if (active) setRequests(rows.map(toDisplayRequest).sort((a, b) => b.requestId - a.requestId));
      })
      .catch((error) => { if (active && error.name !== "AbortError") setLoadError(error.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [token, currentUserId, isBorrowerUser, isOfficerUser, refreshVersion]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (isNewRequestModalOpen && isBorrowerUser && !dialog.open) dialog.showModal();
    if ((!isNewRequestModalOpen || !isBorrowerUser) && dialog.open) dialog.close();
  }, [isNewRequestModalOpen, isBorrowerUser]);

  const visibleRequests = requests.filter((request) => {
    if (currentUserId === null) return false;
    if (isBorrowerUser) return String(request.requestedBy) === currentUserId;
    if (isOfficerUser) return isAdmin || String(request.assignedTo) === currentUserId;
    return false;
  });
  const selectedRequest = visibleRequests.find((request) => request.requestId === selectedRequestId) ?? null;
  const query = searchQuery.trim().toLowerCase();
  const filteredRequests = visibleRequests.filter((request) =>
    (statusFilter === "ALL" || request.status === statusFilter)
    && [request.requestId, request.collateralId, request.loanAccountId]
      .some((value) => String(value ?? "").toLowerCase().includes(query)));

  const metrics = [
    ["Total Requests", visibleRequests.length, "border-gray-200 bg-white text-[#102018]"],
    ["Pending Verification", visibleRequests.filter((r) => ["PENDING", "UNDER_REVIEW", "INFO_REQUIRED"].includes(r.status)).length, "border-orange-200 bg-orange-50 text-orange-700"],
    ["Approved / Processing", visibleRequests.filter((r) => ["APPROVED", "RELEASE_IN_PROGRESS", "RELEASE_DELAYED"].includes(r.status)).length, "border-sky-200 bg-sky-50 text-sky-700"],
    ["Released Assets", visibleRequests.filter((r) => r.status === "RELEASED").length, "border-lime-200 bg-[#f3fce7] text-lime-700"],
  ];

  async function handleCreateRequest(event) {
    event.preventDefault();
    if (!isBorrowerUser || currentUserId === null) return;
    const collateralId = Number(newRequest.collateralId);
    const loanAccountId = Number(newRequest.loanAccountId);
    if (!Number.isSafeInteger(collateralId) || collateralId <= 0
        || !Number.isSafeInteger(loanAccountId) || loanAccountId <= 0
        || !newRequest.expectedReleaseDate) {
      setFormError("Enter valid numeric collateral and loan IDs and select a target release date.");
      return;
    }
    if (isSubmitting) return;
    setIsSubmitting(true);
    setFormError("");
    try {
      const saved = await submitCollateralRequest(session, newRequest);
      const createdRecord = toDisplayRequest(saved);
      setRequests((previous) => [createdRecord, ...previous.filter((row) => row.requestId !== createdRecord.requestId)]);
      setSelectedRequestId(createdRecord.requestId);
      setSearchQuery("");
      setStatusFilter("ALL");
      setIsNewRequestModalOpen(false);
      setNewRequest({ ...emptyForm });
    } catch (error) {
      setFormError(error.message || "Unable to submit the collateral release request.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOfficerAction() {
    setLoadError("Officer actions are not connected to a backend endpoint yet.");
  }

  const fieldClass = "mt-2 w-full rounded-xl border border-[#dfe5df] bg-white px-4 py-3 text-sm text-[#102018] focus:outline-none focus:ring-2 focus:ring-lime-500";
  const headingStyle = { fontFamily: "'Playfair Display', serif" };

  if (!isBorrowerUser && !isOfficerUser) {
    return <main className="mx-auto max-w-xl p-8"><h1 className="text-2xl font-bold">Collateral Release {isOfficerUser ? "(Assigned Queue)" : "(My Requests)"}</h1><p className="mt-4">Sign in with an authorized borrower or officer account to continue.</p><a className="mt-4 inline-block underline" href="#home">Back to Home</a></main>;
  }

  return (
    <div className="min-h-screen bg-white px-6 py-14 text-[#102018] lg:px-16 lg:py-16">
      <header className="mx-auto mb-8 flex max-w-7xl flex-col gap-4 border-b border-[#dfe5df] pb-6 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.25em] text-lime-700">{isOfficerUser ? "Bank Operations Console" : "Customer Asset Portal"}</p>
          <h1 className="mt-2 text-4xl leading-tight sm:text-5xl" style={headingStyle}>Collateral Release</h1>
          <p className="mt-2 text-sm text-gray-500">{isOfficerUser ? "Review your assigned collateral release requests." : "Track your own collateral release requests."}</p>
          <p className="mt-2 text-xs text-gray-500">Requests are saved by CollateralService. Refresh to see the latest status.</p>
        </div>
        {isBorrowerUser && <button type="button" disabled={loading || isSubmitting} onClick={() => { setFormError(""); setIsNewRequestModalOpen(true); }} className="cursor-pointer whitespace-nowrap rounded-full bg-black px-7 py-3.5 text-sm font-semibold text-white transition hover:bg-gray-800">+ Request Asset Release</button>}
      </header>

      <div className="mx-auto mb-5 flex max-w-7xl items-center justify-between gap-4">
        <p role={loadError ? "alert" : "status"} className={loadError ? "text-sm text-red-700" : "text-sm text-gray-500"}>
          {loadError || (loading ? "Loading requests…" : "Requests loaded.")}
        </p>
        {isBorrowerUser && <button type="button" disabled={loading || isSubmitting} onClick={() => setRefreshVersion((value) => value + 1)} className="rounded-full border px-4 py-2 text-sm disabled:opacity-50">Refresh</button>}
      </div>
      <section aria-label="Request statistics" className="mx-auto mb-10 grid max-w-7xl grid-cols-2 gap-5 md:grid-cols-4">
        {metrics.map(([label, value, color]) => (
          <div key={label} className={`rounded-2xl border p-6 shadow-sm ${color}`}>
            <p className="text-xs font-bold uppercase tracking-widest">{label}</p><p className="mt-2 text-3xl font-bold">{value}</p>
          </div>
        ))}
      </section>

      <div className="mx-auto grid max-w-7xl grid-cols-1 items-start gap-8 lg:grid-cols-3">
        <section aria-label="Release requests" className="overflow-hidden rounded-3xl border border-[#dfe5df] bg-white shadow-sm lg:col-span-2">
          <div className="flex flex-col gap-4 border-b border-[#dfe5df] bg-zinc-50/50 p-5">
            <input type="search" aria-label="Search release requests" placeholder="Search Request ID, Collateral ID, Loan A/C..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} className={fieldClass} />
            <div aria-label="Filter by status" className="flex flex-wrap gap-1 rounded-xl bg-gray-100 p-1">
              {["ALL", "PENDING", "UNDER_REVIEW", "INFO_REQUIRED", "APPROVED", "RELEASE_IN_PROGRESS", "RELEASE_DELAYED", "REJECTED", "RELEASED"].map((status) => (
                <button key={status} type="button" aria-pressed={statusFilter === status} onClick={() => setStatusFilter(status)} className={`cursor-pointer rounded-lg px-3 py-2 text-xs font-bold transition ${statusFilter === status ? "bg-white text-black shadow-sm" : "text-gray-500 hover:text-black"}`}>{status.replaceAll("_", " ")}</button>
              ))}
            </div>
          </div>
          <div className="overflow-x-auto">
            {filteredRequests.length === 0 ? (
              <div className="p-16 text-center"><div aria-hidden="true" className="mb-3 text-4xl">🛡️</div><p className="text-sm font-semibold text-gray-500">{loading ? "Loading requests…" : loadError ? "Unable to load requests." : "No release requests match this view."}</p><p className="mt-2 text-xs text-gray-400">Create a request or adjust your search and filters.</p></div>
            ) : (
              <table className="w-full border-collapse text-left text-sm">
                <thead><tr className="border-b border-[#dfe5df] bg-zinc-50/50 text-xs font-bold uppercase tracking-wider text-gray-500"><th scope="col" className="p-4 pl-6">Request ID</th><th scope="col" className="p-4">Collateral / Loan IDs</th><th scope="col" className="p-4">Status</th><th scope="col" className="p-4 pr-6">Target Clearance</th></tr></thead>
                <tbody className="divide-y divide-[#dfe5df]">
                  {filteredRequests.map((request) => (
                    <tr key={request.requestId} onClick={() => setSelectedRequestId(request.requestId)} className={`cursor-pointer transition hover:bg-zinc-50 ${selectedRequest?.requestId === request.requestId ? "bg-[#f3fce7]/60" : ""}`}>
                      <td className="p-4 pl-6"><button type="button" onClick={() => setSelectedRequestId(request.requestId)} aria-label={`Inspect release request ${request.requestId}`} className="font-bold text-zinc-900 underline-offset-4 hover:underline">#{request.requestId}</button></td>
                      <td className="p-4"><div className="font-semibold text-gray-700">Collateral: {request.collateralId}</div><div className="mt-0.5 text-xs text-gray-500">Loan A/C: {request.loanAccountId}</div></td>
                      <td className="p-4"><StatusBadge status={request.status} /></td><td className="p-4 pr-6 text-xs text-gray-500">{request.expectedReleaseDate}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>

        <aside className="rounded-3xl border border-[#dfe5df] bg-white p-6 shadow-sm lg:sticky lg:top-28">
          <h2 className="mb-5 border-b border-[#dfe5df] pb-3 text-sm font-bold uppercase tracking-widest text-gray-500">Inspection Panel</h2>
          {selectedRequest ? (
            <div className="space-y-5">
              <p className="break-words text-xs font-bold uppercase tracking-wider text-gray-600">{String(selectedRequest.reason ?? "").replaceAll("_", " ")}</p>
              <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="text-2xl font-bold" style={headingStyle}>#{selectedRequest.requestId}</h3><StatusBadge status={selectedRequest.status} /></div>
              <dl className="space-y-4">
                {[["Collateral Asset ID", selectedRequest.collateralId], ["Loan Account ID", selectedRequest.loanAccountId], ["Release Reference", selectedRequest.releaseReference], ["Submitted", selectedRequest.createdAt], ["Target Release Date", selectedRequest.expectedReleaseDate], ["Last Updated", selectedRequest.updatedAt]].map(([label, value]) => (
                  <div key={label}><dt className="text-xs font-bold uppercase text-gray-500">{label}</dt><dd className="mt-1 break-words text-sm">{value}</dd></div>
                ))}
              </dl>
              {isOfficerUser && ["PENDING", "APPROVED"].includes(selectedRequest.status) && (
                <section aria-label="Officer actions" className="space-y-3 border-t pt-4">
                  <h3 className="text-sm font-bold">Officer Actions</h3>
                  {selectedRequest.status === "PENDING" && <button type="button" disabled title="Backend integration required" onClick={() => handleOfficerAction(selectedRequest.requestId, "APPROVED")} className="w-full rounded-full bg-black px-4 py-3 text-sm font-semibold text-white">Approve Request</button>}
                  {selectedRequest.status === "APPROVED" && <button type="button" disabled title="Backend integration required" onClick={() => handleOfficerAction(selectedRequest.requestId, "RELEASED")} className="w-full rounded-full bg-lime-700 px-4 py-3 text-sm font-semibold text-white">Mark Released</button>}
                </section>
              )}

            </div>
          ) : (
            <div className="py-10 text-center text-sm text-gray-500"><div aria-hidden="true" className="mb-3 text-3xl">🔍</div>Select a release request to inspect its details.</div>
          )}
        </aside>
      </div>

      <dialog ref={dialogRef} aria-labelledby="release-request-title" onCancel={(event) => { if (isSubmitting) event.preventDefault(); else setIsNewRequestModalOpen(false); }} onClose={() => setIsNewRequestModalOpen(false)} className="m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-3xl bg-white p-6 text-[#102018] shadow-xl backdrop:bg-black/40 sm:p-8">
        <div className="mb-6 flex items-center justify-between gap-4"><h2 id="release-request-title" className="text-2xl font-bold" style={headingStyle}>Request Asset Release</h2><button type="button" disabled={isSubmitting} aria-label="Close request form" onClick={() => setIsNewRequestModalOpen(false)} className="cursor-pointer p-2 text-lg text-gray-500 hover:text-black">✕</button></div>
        <form onSubmit={handleCreateRequest} className="space-y-5">
          {formError && <p role="alert" className="text-sm text-red-700">{formError}</p>}
          <label className="block text-sm font-semibold">Collateral Register ID *<input name="collateralId" type="number" min="1" step="1" required placeholder="e.g. 405912" value={newRequest.collateralId} onChange={(e) => setNewRequest({ ...newRequest, collateralId: e.target.value })} className={fieldClass} /></label>
          <label className="block text-sm font-semibold">Loan Account ID *<input name="loanAccountId" type="number" min="1" step="1" required placeholder="Numeric loan account ID" value={newRequest.loanAccountId} onChange={(e) => setNewRequest({ ...newRequest, loanAccountId: e.target.value })} className={fieldClass} /></label>
          <label className="block text-sm font-semibold">Release Reason *<select name="reason" required value={newRequest.reason} onChange={(e) => setNewRequest({ ...newRequest, reason: e.target.value })} className={fieldClass}><option value="LOAN_FULLY_PAID">Loan Settled &amp; Fully Closed</option><option value="COLLATERAL_SUBSTITUTION">Collateral Substitution Approved</option><option value="COURT_MANDATE">Legal Clear / Court Mandate</option></select></label>
          <label className="block text-sm font-semibold">Target Release Date *<input name="expectedReleaseDate" type="date" required value={newRequest.expectedReleaseDate} onChange={(e) => setNewRequest({ ...newRequest, expectedReleaseDate: e.target.value })} className={fieldClass} /></label>
          <p className="text-xs text-gray-500">The release reference is assigned by the officer after processing.</p>
          <div className="flex flex-wrap justify-end gap-3 pt-3"><button type="button" disabled={isSubmitting} onClick={() => setIsNewRequestModalOpen(false)} className="cursor-pointer rounded-full border border-black px-6 py-3 text-sm font-semibold hover:bg-gray-100">Cancel</button><button type="submit" disabled={isSubmitting} className="cursor-pointer rounded-full bg-black px-6 py-3 text-sm font-semibold text-white hover:bg-gray-800">{isSubmitting ? "Submitting…" : "Submit Request"}</button></div>
        </form>
      </dialog>
    </div>
  );
}
