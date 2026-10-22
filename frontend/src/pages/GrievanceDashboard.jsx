
import { useEffect, useRef, useState } from "react";
import {
  getMyGrievances,
  submitGrievance,
  getGrievanceHistory,
} from "../grievanceApi.js";

function toTicket(row) {
  const description = row.description || "";
  const separator = description.indexOf("\n\n");

  const formatTime = (value) =>
    value ? new Date(value).toLocaleString() : "Not available";

  return {
    ...row,
    id: row.grievanceId,
    loanAccountId: String(row.loanAccountId ?? ""),
    subject:
      separator >= 0
        ? description.slice(0, separator)
        : row.category || "Grievance",
    description:
      separator >= 0 ? description.slice(separator + 2) : description,
    createdAt: formatTime(row.createdAt),
    updatedAt: formatTime(row.updatedAt),
  };
}

const emptyForm = {
  loanAccountId: "",
  category: "DELAY",
  subject: "",
  description: "",
};

function statusClass(status) {
  if (status === "OPEN") return "bg-orange-100 text-orange-700";
  if (status === "IN_PROGRESS") return "bg-sky-100 text-sky-700";
  if (status === "RESOLVED" || status === "CLOSED")
    return "bg-lime-100 text-lime-800";
  if (status === "ESCALATED") return "bg-red-100 text-red-700";
  return "bg-gray-100 text-gray-700";
}

function StatusBadge({ status }) {
  return (
    <span
      className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${statusClass(
        status
      )}`}
    >
      {String(status ?? "UNKNOWN").replaceAll("_", " ")}
    </span>
  );
}

export default function GrievanceDashboard({ session }) {
  const [tickets, setTickets] = useState([]);
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [isNewTicketModalOpen, setIsNewTicketModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [newGrievance, setNewGrievance] = useState({ ...emptyForm });
  const [formError, setFormError] = useState("");
  const dialogRef = useRef(null);
  const historyControllerRef = useRef(null);

  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [refreshVersion, setRefreshVersion] = useState(0);

  const [ticketHistory, setTicketHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const token = session?.token;
  const userId = session?.userId;

  // Load the borrower's grievances.
  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    setLoading(true);
    setLoadError("");
    setTickets([]);
    setSelectedTicket(null);
    historyControllerRef.current?.abort();
    setTicketHistory([]);
    setHistoryError("");
    setHistoryLoading(false);

    if (!token || !userId) {
      setLoadError("Please sign in with a borrower account.");
      setLoading(false);
      return () => controller.abort();
    }

    getMyGrievances({ token, userId }, controller.signal)
      .then((rows) => {
        if (!Array.isArray(rows)) {
          throw new Error(
            "The server returned an unexpected grievance list."
          );
        }

        if (active) {
          setTickets(
            rows.map(toTicket).sort((a, b) => b.id - a.id)
          );
        }
      })
      .catch((error) => {
        if (active && error.name !== "AbortError") {
          setLoadError(error.message || "Unable to load grievances.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [token, userId, refreshVersion]);

  useEffect(() => {
    return () => historyControllerRef.current?.abort();
  }, [token, userId]);

  // Open or close the new-grievance dialog.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (isNewTicketModalOpen && !dialog.open) {
      dialog.showModal();
    }

    if (!isNewTicketModalOpen && dialog.open) {
      dialog.close();
    }
  }, [isNewTicketModalOpen]);

  // Load history when the borrower selects a ticket.
  async function handleViewHistory(ticket) {
    historyControllerRef.current?.abort();
    const controller = new AbortController();
    historyControllerRef.current = controller;
    setSelectedTicket(ticket);
    setTicketHistory([]);
    setHistoryError("");
    setHistoryLoading(true);

    try {
      const rows = await getGrievanceHistory(session, ticket.id, controller.signal);
      if (!controller.signal.aborted) setTicketHistory(rows);
    } catch (error) {
      if (!controller.signal.aborted && error.name !== "AbortError") {
        setHistoryError(error.message || "Unable to load grievance history.");
      }
    } finally {
      if (historyControllerRef.current === controller && !controller.signal.aborted) {
        setHistoryLoading(false);
      }
    }
  }

  const query = searchQuery.trim().toLowerCase();

  const filteredTickets = tickets.filter((ticket) => {
    const matchesStatus =
      statusFilter === "ALL" || ticket.status === statusFilter;

    const matchesSearch = [
      ticket.id,
      ticket.subject,
      ticket.loanAccountId,
    ].some((value) =>
      String(value ?? "").toLowerCase().includes(query)
    );

    return matchesStatus && matchesSearch;
  });

  const metrics = [
    {
      label: "Total Tickets",
      value: tickets.length,
      style: "border-gray-200 bg-white text-[#102018]",
    },
    {
      label: "Open",
      value: tickets.filter((t) => t.status === "OPEN").length,
      style: "border-orange-200 bg-orange-50 text-orange-700",
    },
    {
      label: "In Progress",
      value: tickets.filter((t) => t.status === "IN_PROGRESS").length,
      style: "border-sky-200 bg-sky-50 text-sky-700",
    },
    {
      label: "Resolved",
      value: tickets.filter((t) => t.status === "RESOLVED").length,
      style: "border-lime-200 bg-[#f3fce7] text-lime-700",
    },
  ];

  async function handleCreateGrievance(event) {
    event.preventDefault();

    const loanAccountId = newGrievance.loanAccountId.trim();
    const subject = newGrievance.subject.trim();
    const description = newGrievance.description.trim();

    if (!loanAccountId || !subject || !description) {
      setFormError("Please fill in all mandatory fields.");
      return;
    }

    if (isSubmitting) return;

    setFormError("");
    setIsSubmitting(true);

    try {
      const saved = await submitGrievance(session, {
        ...newGrievance,
        loanAccountId,
        subject,
        description,
      });

      const createdTicket = toTicket(saved);

      setTickets((previous) => [
        createdTicket,
        ...previous.filter((ticket) => ticket.id !== createdTicket.id),
      ]);

      setSearchQuery("");
      setStatusFilter("ALL");
      setIsNewTicketModalOpen(false);
      setNewGrievance({ ...emptyForm });

      // Select the newly created ticket and try to load its history.
      await handleViewHistory(createdTicket);
    } catch (error) {
      setFormError(error.message || "Could not submit the grievance.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const fieldClass =
    "w-full rounded-xl border border-[#dfe5df] bg-white px-4 py-3 text-[#102018] focus:outline-none focus:ring-2 focus:ring-lime-500";

  return (
    <div className="min-h-screen bg-white px-6 py-14 text-[#102018] lg:px-16 lg:py-16">
      <header className="mx-auto mb-8 flex max-w-7xl flex-col gap-4 border-b border-[#dfe5df] pb-6 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.25em] text-lime-700">
            Support Desk
          </p>
          <h1
            className="mt-2 text-4xl leading-tight sm:text-5xl"
            style={{ fontFamily: "'Playfair Display', serif" }}
          >
            Grievance Redressal
          </h1>
          <p className="mt-2 text-sm text-gray-500">
            Raise, track, and monitor loan closure support tickets.
          </p>
          <p className="mt-2 text-xs text-gray-500">
            Tickets are loaded from your saved grievances. Refresh to see
            officer updates.
          </p>
        </div>

        <button
          type="button"
          disabled={loading || isSubmitting || !token || !userId}
          onClick={() => {
            setFormError("");
            setIsNewTicketModalOpen(true);
          }}
          className="cursor-pointer whitespace-nowrap rounded-full bg-black px-7 py-3.5 text-sm font-semibold text-white shadow-sm transition hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          + Raise New Grievance
        </button>
      </header>

      <div className="mx-auto mb-5 flex max-w-7xl items-center justify-between gap-4">
        <p
          role={loadError ? "alert" : "status"}
          className={
            loadError ? "text-sm text-red-700" : "text-sm text-gray-500"
          }
        >
          {loadError ||
            (loading ? "Loading grievances…" : "Grievances loaded.")}
        </p>

        <button
          type="button"
          disabled={loading || isSubmitting}
          onClick={() => setRefreshVersion((value) => value + 1)}
          className="rounded-full border px-4 py-2 text-sm disabled:opacity-50"
        >
          Refresh
        </button>
      </div>

      <section
        aria-label="Ticket statistics"
        className="mx-auto mb-10 grid max-w-7xl grid-cols-2 gap-5 md:grid-cols-4"
      >
        {metrics.map((metric) => (
          <div
            key={metric.label}
            className={`rounded-2xl border p-6 shadow-sm ${metric.style}`}
          >
            <p className="text-xs font-bold uppercase tracking-widest">
              {metric.label}
            </p>
            <p className="mt-2 text-3xl font-bold">{metric.value}</p>
          </div>
        ))}
      </section>

      <div className="mx-auto grid max-w-7xl grid-cols-1 items-start gap-8 lg:grid-cols-3">
        {/* Grievance ticket list */}
        <section
          aria-label="Grievance tickets"
          className="overflow-hidden rounded-3xl border border-[#dfe5df] bg-white shadow-sm lg:col-span-2"
        >
          <div className="flex flex-col items-center justify-between gap-4 border-b border-[#dfe5df] bg-zinc-50/50 p-5 sm:flex-row sm:flex-wrap">
            <input
              aria-label="Search tickets"
              type="search"
              placeholder="Search Ticket ID, Loan A/C or Subject..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className={`${fieldClass} sm:max-w-72`}
            />

            <div
              aria-label="Filter by status"
              className="flex flex-wrap gap-1 rounded-xl bg-gray-100 p-1"
            >
              {[
                "ALL",
                "OPEN",
                "IN_PROGRESS",
                "RESOLVED",
                "ESCALATED",
                "CLOSED",
              ].map((status) => (
                <button
                  key={status}
                  type="button"
                  aria-pressed={statusFilter === status}
                  onClick={() => setStatusFilter(status)}
                  className={`cursor-pointer rounded-lg px-3 py-2 text-xs font-bold transition ${
                    statusFilter === status
                      ? "bg-white text-black shadow-sm"
                      : "text-gray-500 hover:text-black"
                  }`}
                >
                  {status.replaceAll("_", " ")}
                </button>
              ))}
            </div>
          </div>

          <div className="overflow-x-auto">
            {filteredTickets.length === 0 ? (
              <div className="p-16 text-center">
                <div aria-hidden="true" className="mb-3 text-4xl">
                  📁
                </div>
                <p className="text-sm font-semibold text-gray-500">
                  {loading
                    ? "Loading grievances…"
                    : loadError
                      ? "Unable to load grievances."
                      : "No grievance tickets match this view."}
                </p>
                <p className="mt-2 text-xs text-gray-400">
                  Raise a ticket or adjust your search and filters.
                </p>
              </div>
            ) : (
              <table className="w-full border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-[#dfe5df] bg-zinc-50/50 text-xs font-bold uppercase tracking-wider text-gray-500">
                    <th scope="col" className="p-4 pl-6">
                      Ticket details
                    </th>
                    <th scope="col" className="p-4">
                      Loan A/C
                    </th>
                    <th scope="col" className="p-4">
                      Status
                    </th>
                    <th scope="col" className="p-4 pr-6">
                      Created Date
                    </th>
                  </tr>
                </thead>

                <tbody className="divide-y divide-[#dfe5df]">
                  {filteredTickets.map((ticket) => (
                    <tr
                      key={ticket.id}
                      onClick={() => handleViewHistory(ticket)}
                      className={`cursor-pointer transition hover:bg-zinc-50 ${
                        selectedTicket?.id === ticket.id
                          ? "bg-[#f3fce7]/60"
                          : ""
                      }`}
                    >
                      <td className="p-4 pl-6">
                        <button
                          type="button"
                          aria-label={`Inspect ticket ${ticket.id}`}
                          onClick={(event) => {
                            event.stopPropagation();
                            handleViewHistory(ticket);
                          }}
                          className="font-bold text-zinc-900 underline-offset-4 hover:underline"
                        >
                          #{ticket.id}
                        </button>
                        <div className="mt-1 max-w-60 truncate text-xs text-gray-500">
                          {ticket.subject}
                        </div>
                      </td>

                      <td className="p-4 font-semibold text-gray-700">
                        {ticket.loanAccountId}
                      </td>

                      <td className="p-4">
                        <StatusBadge status={ticket.status} />
                      </td>

                      <td className="p-4 pr-6 text-xs text-gray-500">
                        {ticket.createdAt}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>

        {/* Selected grievance details and history */}
        <aside className="rounded-3xl border border-[#dfe5df] bg-white p-6 shadow-sm lg:sticky lg:top-28">
          <h2 className="mb-5 border-b border-[#dfe5df] pb-3 text-sm font-bold uppercase tracking-widest text-gray-500">
            Inspection Panel
          </h2>

          {selectedTicket ? (
            <div className="space-y-5">
              <span className="rounded-md bg-gray-100 px-2.5 py-1 text-xs font-bold uppercase tracking-wider text-gray-600">
                {selectedTicket.category}
              </span>

              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3
                  className="text-2xl font-bold"
                  style={{ fontFamily: "'Playfair Display', serif" }}
                >
                  #{selectedTicket.id}
                </h3>
                <StatusBadge status={selectedTicket.status} />
              </div>

              <dl className="space-y-4">
                <div>
                  <dt className="text-xs font-bold uppercase text-gray-500">
                    Loan Account ID
                  </dt>
                  <dd className="mt-1 break-words">
                    {selectedTicket.loanAccountId}
                  </dd>
                </div>

                <div>
                  <dt className="text-xs font-bold uppercase text-gray-500">
                    Subject
                  </dt>
                  <dd className="mt-1 break-words font-semibold">
                    {selectedTicket.subject}
                  </dd>
                </div>

                <div>
                  <dt className="text-xs font-bold uppercase text-gray-500">
                    Description
                  </dt>
                  <dd className="mt-1 whitespace-pre-wrap break-words text-sm leading-relaxed">
                    {selectedTicket.description}
                  </dd>
                </div>

                <div>
                  <dt className="text-xs text-gray-500">
                    Logged Timestamp
                  </dt>
                  <dd className="mt-1 text-sm">
                    {selectedTicket.createdAt}
                  </dd>
                </div>

                <div>
                  <dt className="text-xs text-gray-500">
                    Last System Action
                  </dt>
                  <dd className="mt-1 text-sm">
                    {selectedTicket.updatedAt}
                  </dd>
                </div>
              </dl>


      {/* Compact Grievance Timeline */}
      <div className="mt-5 border-t border-[#dfe5df] pt-4">
        <div className="flex items-center justify-between gap-2">
          <h3
            className="text-base font-semibold"
            style={{ fontFamily: "'Playfair Display', serif" }}
          >
            Grievance Timeline
          </h3>

          {ticketHistory.length > 0 && (
            <span className="rounded-full bg-lime-100 px-2 py-1 text-[10px] font-bold text-lime-800">
              {ticketHistory.length} updates
            </span>
          )}
        </div>

        <p className="mt-1 text-xs text-gray-500">
          Track your complaint's progress.
        </p>

        {historyLoading && (
          <p className="mt-3 text-xs text-gray-500">
            Loading timeline...
          </p>
        )}

        {historyError && (
          <p role="alert" className="mt-3 text-xs text-red-700">
            {historyError}
          </p>
        )}

        {!historyLoading &&
          !historyError &&
          ticketHistory.length === 0 && (
            <p className="mt-3 rounded-lg bg-[#f3fce7] p-3 text-xs text-[#536057]">
              No status updates available yet.
            </p>
          )}

        {!historyLoading && ticketHistory.length > 0 && (
          <div className="mt-4 max-h-64 space-y-0 overflow-y-auto pr-2">
            {ticketHistory.map((entry, index) => (
              <div
                key={entry.historyId ?? index}
                className="relative flex gap-3 pb-4 last:pb-0"
              >
                {/* Timeline dot and connecting line */}
                <div className="relative flex w-3 shrink-0 justify-center">
                  {index !== ticketHistory.length - 1 && (
                    <span className="absolute top-3 h-full w-px bg-lime-200" />
                  )}

                  <span className="relative z-10 mt-1 h-2.5 w-2.5 rounded-full border-2 border-lime-600 bg-white" />
                </div>

                {/* Timeline event */}
                <div className="min-w-0 flex-1 pb-1">
                  <p className="text-xs font-semibold leading-4 text-[#102018]">
                    {String(
                      entry.newStatus ?? entry.action ?? "Update"
                    ).replaceAll("_", " ")}
                  </p>

                  {entry.oldStatus && (
                    <p className="mt-1 text-[10px] text-gray-500">
                      From{" "}
                      {String(entry.oldStatus).replaceAll("_", " ")}
                    </p>
                  )}

                  {(entry.actionRemarks ?? entry.remarks) && (
                    <p className="mt-1 break-words text-xs leading-4 text-[#536057]">
                      {entry.actionRemarks ?? entry.remarks}
                    </p>
                  )}

                  <p className="mt-1 text-[10px] text-gray-400">
                    {entry.actionAt
                      ? new Date(entry.actionAt).toLocaleString([], {
                          day: "2-digit",
                          month: "short",
                          year: "numeric",
                          hour: "2-digit",
                          minute: "2-digit",
                        })
                      : "Time not available"}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      </div>
                ) : (
                  <div className="py-10 text-center text-sm text-gray-500">
                    <div aria-hidden="true" className="mb-3 text-3xl">
                      🔍
                    </div>
                    Select a ticket from the table to inspect its details and
                    timeline.
                  </div>
                )}
              </aside>
            </div>

      {/* New grievance dialog */}
      <dialog
        ref={dialogRef}
        aria-labelledby="new-grievance-title"
        onCancel={(event) => {
          if (isSubmitting) {
            event.preventDefault();
          } else {
            setIsNewTicketModalOpen(false);
          }
        }}
        onClose={() => setIsNewTicketModalOpen(false)}
        className="m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-3xl bg-white p-6 text-[#102018] shadow-xl backdrop:bg-black/40 sm:p-8"
      >
        <div className="mb-6 flex items-center justify-between gap-4">
          <h2
            id="new-grievance-title"
            className="text-2xl font-bold"
            style={{ fontFamily: "'Playfair Display', serif" }}
          >
            Raise Grievance Request
          </h2>

          <button
            type="button"
            disabled={isSubmitting}
            aria-label="Close new grievance form"
            onClick={() => setIsNewTicketModalOpen(false)}
            className="cursor-pointer p-2 text-lg text-gray-500 hover:text-black"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleCreateGrievance} className="space-y-5">
          {formError && (
            <p role="alert" className="text-sm text-red-700">
              {formError}
            </p>
          )}

          <label className="block text-sm font-semibold">
            Loan Account ID *
            <input
              name="loanAccountId"
              type="number"
              min="1"
              step="1"
              required
              placeholder="Numeric loan ID, e.g. 1001"
              value={newGrievance.loanAccountId}
              onChange={(event) =>
                setNewGrievance({
                  ...newGrievance,
                  loanAccountId: event.target.value,
                })
              }
              className={`${fieldClass} mt-2`}
            />
          </label>

          <label className="block text-sm font-semibold">
            Complaint Category *
            <select
              name="category"
              required
              value={newGrievance.category}
              onChange={(event) =>
                setNewGrievance({
                  ...newGrievance,
                  category: event.target.value,
                })
              }
              className={`${fieldClass} mt-2`}
            >
              <option value="DELAY">
                NOC/Collateral Processing Delay
              </option>
              <option value="SETTLEMENT">
                Settlement Value Discrepancy
              </option>
              <option value="SYSTEM">Portal System Error</option>
              <option value="OTHER">Other Issues</option>
            </select>
          </label>

          <label className="block text-sm font-semibold">
            Summary / Subject *
            <input
              name="subject"
              type="text"
              required
              placeholder="Briefly describe your issue"
              value={newGrievance.subject}
              onChange={(event) =>
                setNewGrievance({
                  ...newGrievance,
                  subject: event.target.value,
                })
              }
              className={`${fieldClass} mt-2`}
            />
          </label>

          <label className="block text-sm font-semibold">
            Detailed Description *
            <textarea
              name="description"
              required
              rows={4}
              placeholder="Provide details about the problem..."
              value={newGrievance.description}
              onChange={(event) =>
                setNewGrievance({
                  ...newGrievance,
                  description: event.target.value,
                })
              }
              className={`${fieldClass} mt-2 resize-none`}
            />
          </label>

          <div className="flex flex-wrap justify-end gap-3 pt-3">
            <button
              type="button"
              disabled={isSubmitting}
              onClick={() => setIsNewTicketModalOpen(false)}
              className="cursor-pointer rounded-full border border-black px-6 py-3 text-sm font-semibold transition hover:bg-gray-100"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              className="cursor-pointer rounded-full bg-black px-6 py-3 text-sm font-semibold text-white transition hover:bg-gray-800 disabled:opacity-50"
            >
              {isSubmitting ? "Submitting…" : "Submit Ticket"}
            </button>
          </div>
        </form>
      </dialog>
    </div>
  );
}
