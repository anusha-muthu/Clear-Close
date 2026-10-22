import { useEffect, useRef, useState } from "react";
import {
  getAssignedGrievances,
  getSeniorGrievances,
  assignGrievance,
  updateAssignedGrievance,
} from "../officerGrievanceApi.jsx";

const CATEGORY_LABELS = {
  DELAY: "NOC / collateral processing delay",
  SETTLEMENT: "Settlement value discrepancy",
  SYSTEM: "Portal system error",
  OTHER: "Other issues",
};

const TRANSITIONS = {
  OPEN: ["IN_PROGRESS", "ESCALATED"],
  IN_PROGRESS: ["RESOLVED", "ESCALATED"],
  ESCALATED: ["IN_PROGRESS", "CLOSED"],
  RESOLVED: ["CLOSED"]
};

const STATUSES = ["OPEN", "IN_PROGRESS","ESCALATED", "RESOLVED"];
const label = (value) => String(value || "Unknown").replaceAll("_", " ");
const time = (value) => value ? new Date(value).toLocaleString() : "Not available";
const field = "w-full rounded-xl border border-[#dfe5df] bg-white px-4 py-3 text-sm";

function Badge({ status }) {
  const color = status === "ESCALATED" ? "bg-red-100 text-red-800" :
    ["RESOLVED", "CLOSED"].includes(status) ? "bg-lime-100 text-lime-800" :
    status === "IN_PROGRESS" ? "bg-sky-100 text-sky-800" : "bg-orange-100 text-orange-800";
  return <span className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${color}`}>{label(status)}</span>;
}

export default function GrievanceOfficerDashboard({ session }) {
  const [tickets, setTickets] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [nextStatus, setNextStatus] = useState("");
  const [remarks, setRemarks] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [refresh, setRefresh] = useState(0);
  const mutation = useRef(null);
  const busy = useRef(false);
  const [targetOfficerId, setTargetOfficerId] = useState("");
  const role = session?.role;
  const isSenior = role === "SENIOR_GRIEVANCE_OFFICER";
  const ownsSelected = selectedId != null && tickets.some((ticket) =>
    String(ticket.grievanceId) === String(selectedId) &&
    String(ticket.assignedTo) === String(session?.userId));
  const token = session?.token;
  const accountType = session?.accountType;
  const userId = session?.userId;
  const selected = tickets.find((ticket) => ticket.grievanceId === selectedId);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError("");
    setTickets([]);
    setSelectedId(null);
    const load = isSenior ? getSeniorGrievances : getAssignedGrievances;
    load({ token, accountType, userId, role }, controller.signal)
      .then((rows) => { if (active) setTickets(rows); })
      .catch((err) => { if (active && err.name !== "AbortError") setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [token, accountType, userId, role, isSenior, refresh]);

  useEffect(() => {
    setNextStatus("");
    setRemarks("");
    setTargetOfficerId("");
  }, [selectedId, selected?.status]);

  useEffect(() => {
    setSaving(false);
    return () => { mutation.current?.abort(); busy.current = false; };
  }, [token, accountType, userId, role]);

  const query = search.trim().toLowerCase();
  const visible = tickets.filter((ticket) =>
    (statusFilter === "ALL" || ticket.status === statusFilter) &&
    [ticket.grievanceId, ticket.loanReference, ticket.loanAccountId,
      ticket.borrowerName, ticket.category, ticket.description]
      .some((value) => String(value ?? "").toLowerCase().includes(query))
  );

  async function save(event) {
    event.preventDefault();
    if (busy.current || !selected || !ownsSelected) return;
    if (!nextStatus || !remarks.trim()) {
      setError("Choose a status and enter action remarks.");
      return;
    }
    const controller = new AbortController();
    mutation.current = controller;
    busy.current = true;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateAssignedGrievance(session, selected.grievanceId, {
        expectedStatus: selected.status,
        status: nextStatus,
        remarks: remarks.trim(),
      }, controller.signal);
      if (controller.signal.aborted) return;
      setTickets((rows) => rows.flatMap((row) => {
        if (row.grievanceId !== updated.grievanceId) return [row];
        const inQueue = isSenior
          ? updated.status === "ESCALATED" || String(updated.assignedTo) === String(userId) ||
            (updated.assignedTo == null && !["RESOLVED", "REJECTED", "CLOSED"].includes(updated.status))
          : String(updated.assignedTo) === String(userId);
        return inQueue ? [updated] : [];
      }));
      setNextStatus("");
      setRemarks("");
      setNotice("Grievance updated.");
    } catch (err) {
      if (err.name !== "AbortError") setError(err.message);
    } finally {
      if (mutation.current === controller) {
        busy.current = false;
        if (!controller.signal.aborted) setSaving(false);
      }
    }
  }

  async function saveAssignment(event) {
    event.preventDefault();
    if (busy.current || !selected || !isSenior) return;
    const controller = new AbortController();
    mutation.current = controller; busy.current = true;
    setSaving(true); setError(""); setNotice("");
    try {
      const updated = await assignGrievance(session, selected.grievanceId, targetOfficerId, controller.signal);
      if (controller.signal.aborted) return;
      setTickets((rows) => rows.flatMap((row) => {
        if (String(row.grievanceId) !== String(updated.grievanceId)) return [row];
        return updated.status === "ESCALATED" || String(updated.assignedTo) === String(userId)
          ? [updated] : [];
      }));
      setTargetOfficerId("");
      setNotice(`Grievance #${updated.grievanceId} assigned to officer #${updated.assignedTo}.`);
    } catch (err) {
      if (!controller.signal.aborted) setError(err.message);
    } finally {
      if (mutation.current === controller) {
        busy.current = false;
        if (!controller.signal.aborted) setSaving(false);
      }
    }
  }

  const availableActions = TRANSITIONS[selected?.status] || [];
  const metrics = [
    [isSenior ? "Senior review queue" : "Assigned to you", tickets.length],
    ["Open", tickets.filter((t) => t.status === "OPEN").length],
    ["In progress", tickets.filter((t) => t.status === "IN_PROGRESS").length],
    ["Escalated", tickets.filter((t) => t.status === "ESCALATED").length],
  ];

  return (
    <main className="min-h-screen bg-[#fafbf8] px-6 py-10 text-[#102018] lg:px-16">
      <header className="mx-auto mb-8 flex max-w-7xl flex-wrap items-center justify-between gap-4 border-b pb-6">
        <div>
          <p className="text-xs font-bold uppercase tracking-widest text-lime-700">Bank Employee Portal</p>
          <h1 className="mt-2 text-4xl" style={{ fontFamily: "'Playfair Display', serif" }}>{isSenior ? "Grievance Review & Assignment" : "Assigned Grievances"}</h1>
          <p className="mt-2 text-sm text-gray-600">{isSenior ? "Review escalated or unassigned complaints and assign the responsible officer." : "Review customer complaints assigned to you and record your actions."}</p>
          <p className="mt-2 text-xs text-gray-500">{session?.username} · {label(session?.role)}</p>
        </div>
        <button type="button" disabled={loading || saving} onClick={() => { setNotice(""); setRefresh((v) => v + 1); }} className="rounded-full border bg-white px-5 py-3 text-sm disabled:opacity-50">Refresh</button>
      </header>

      {error && <p role="alert" className="mx-auto mb-5 max-w-7xl rounded-xl bg-red-50 p-4 text-sm text-red-800">{error}</p>}
      {notice && <p role="status" className="mx-auto mb-5 max-w-7xl rounded-xl bg-lime-50 p-4 text-sm text-lime-800">{notice}</p>}

      <section aria-label="Assignment statistics" className="mx-auto mb-8 grid max-w-7xl grid-cols-2 gap-4 md:grid-cols-4">
        {metrics.map(([name, value]) => <div key={name} className="rounded-2xl border bg-white p-5"><p className="text-sm text-gray-500">{name}</p><p className="mt-2 text-3xl font-bold">{loading || error && !tickets.length ? "—" : value}</p></div>)}
      </section>

      <div className="mx-auto grid max-w-7xl gap-6 lg:grid-cols-3">
        <section className="overflow-hidden rounded-2xl border bg-white lg:col-span-2" aria-label="Assigned grievance list">
          <div className="flex flex-wrap gap-3 border-b p-4">
            <input aria-label="Search assigned grievances" className={`${field} flex-1`} placeholder="Search complaint, customer or loan" value={search} onChange={(e) => setSearch(e.target.value)} />
            <select aria-label="Filter status" className={`${field} sm:w-auto`} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="ALL">All statuses</option>
              {STATUSES.map((status) => <option key={status} value={status}>{label(status)}</option>)}
            </select>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="p-4">Complaint</th><th className="p-4">Customer / loan</th><th className="p-4">Status / officer</th></tr></thead>
              <tbody>
                {visible.map((ticket) => <tr key={ticket.grievanceId} className={`border-t ${selectedId === ticket.grievanceId ? "bg-lime-50" : ""}`}>
                  <td className="p-4"><button type="button" disabled={saving} onClick={() => setSelectedId(ticket.grievanceId)} className="font-bold underline">#{ticket.grievanceId}</button><p className="mt-1 text-xs text-gray-500">{CATEGORY_LABELS[ticket.category] || ticket.category}</p></td>
                  <td className="p-4">{ticket.borrowerName || `Borrower #${ticket.borrowerId}`}<p className="mt-1 text-xs text-gray-500">{ticket.loanReference || ticket.loanAccountId}</p></td>
                  <td className="p-4"><Badge status={ticket.status} /><p className="mt-2 text-xs text-gray-500">{ticket.assignedTo == null ? "Unassigned" : `Officer #${ticket.assignedTo}`}</p></td>
                </tr>)}
              </tbody>
            </table>
            {!visible.length && <p className="p-10 text-center text-sm text-gray-500">{loading ? "Loading assigned complaints..." : error ? "Unable to load grievances. Refresh after resolving the error." : "No assigned grievances match this view."}</p>}
          </div>
        </section>

        <aside className="rounded-2xl border bg-white p-6">
          <h2 className="text-lg font-bold">Complaint details</h2>
          {!selected ? <p className="mt-4 text-sm text-gray-500">Select a complaint to review it.</p> : <>
            <div className="my-4 flex items-center justify-between gap-3"><strong>#{selected.grievanceId}</strong><Badge status={selected.status} /></div>
            <dl className="space-y-4 text-sm">
              <div><dt className="text-gray-500">Category</dt><dd>{CATEGORY_LABELS[selected.category] || selected.category}</dd></div>
              <div><dt className="text-gray-500">Customer</dt><dd>{selected.borrowerName || `Borrower #${selected.borrowerId}`}</dd></div>
              <div><dt className="text-gray-500">Loan</dt><dd>{selected.loanReference} (ID {selected.loanAccountId})</dd></div>
              <div><dt className="text-gray-500">Complaint</dt><dd className="whitespace-pre-wrap break-words">{selected.description}</dd></div>
              <div><dt className="text-gray-500">Created</dt><dd>{time(selected.createdAt)}</dd></div>
              <div><dt className="text-gray-500">Last updated</dt><dd>{time(selected.updatedAt)}</dd></div>
              <div><dt className="text-gray-500">Last action remarks</dt><dd className="whitespace-pre-wrap break-words">{selected.resolutionRemarks || "No remarks recorded."}</dd></div>
            </dl>
            {isSenior && !["RESOLVED", "REJECTED", "CLOSED"].includes(selected.status) && (
              <form onSubmit={saveAssignment} className="mt-6 space-y-4 border-t pt-5">
                <h3 className="font-bold">Assign / reassign officer</h3>
                <p className="text-sm text-gray-600">Choose an active category officer or senior grievance officer. DELAY: collateral; SETTLEMENT: loan closure; SYSTEM / OTHER: grievance.</p>
                <p className="text-xs text-gray-500">Assignment preserves the complaint status. The assigned officer can record the next action.</p>
                <label className="block text-sm font-semibold">Officer ID
                  <input type="number" min="1" step="1" required disabled={saving} value={targetOfficerId} onChange={(e) => setTargetOfficerId(e.target.value)} className={`${field} mt-2`} />
                </label>
                <button type="submit" disabled={saving || !targetOfficerId} className="w-full rounded-full bg-black px-5 py-3 text-sm font-semibold text-white disabled:opacity-50">{saving ? "Saving..." : "Save assignment"}</button>
              </form>
            )}
            {ownsSelected ? (availableActions.length ? <form onSubmit={save} className="mt-6 space-y-4 border-t pt-5">
              <label className="block text-sm font-semibold">Update status
                <select required disabled={saving} className={`${field} mt-2`} value={nextStatus} onChange={(e) => setNextStatus(e.target.value)}>
                  <option value="">Choose an action</option>
                  {availableActions.map((status) => <option key={status} value={status}>{label(status)}</option>)}
                </select>
              </label>
              <label className="block text-sm font-semibold">Action remarks
                <textarea required disabled={saving} maxLength={1000} rows={4} className={`${field} mt-2`} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
              </label>
              <button disabled={saving} type="submit" className="w-full rounded-full bg-black px-5 py-3 text-sm font-semibold text-white disabled:opacity-50">{saving ? "Saving..." : "Save action"}</button>
            </form> : <p className="mt-6 text-sm text-gray-500">No further officer actions are available for this status.</p>) : <p className="mt-5 text-sm text-gray-500">Status updates are available to the assigned officer. Assign this complaint to yourself to review it personally.</p>}
          </>}
        </aside>
      </div>
    </main>
  );
}
