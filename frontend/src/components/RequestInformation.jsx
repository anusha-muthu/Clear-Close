import { useEffect, useRef, useState } from "react";
import { getBorrowerInformationHistory, submitAdditionalInformation, preClosureRequest } from "../api";
import { getOfficerInformationHistory } from "../officerApi";

export default function RequestInformation({ token, requestId, status, officer = false, workflow = "cooling-off", onSubmitted, onRefresh }) {
  const [messages, setMessages] = useState([]);
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [notice, setNotice] = useState("");
  const [refresh, setRefresh] = useState(0);
  const submitting = useRef(false);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setHistoryError("");
      try {
        const getHistory = officer ? getOfficerInformationHistory : getBorrowerInformationHistory;
        const data = workflow === "preclosure"
          ? await preClosureRequest(token, `${officer ? "/officer" : ""}/${encodeURIComponent(requestId)}/information-history`)
          : await getHistory(token, requestId);
        if (!Array.isArray(data)) throw new Error("Invalid request information received.");
        if (active) setMessages(data);
      } catch (error) {
        if (active) setHistoryError(error.message);
      } finally {
        if (active) setLoading(false);
      }
    }
    load();
    return () => { active = false; };
  }, [token, requestId, officer, workflow, status, refresh]);

  async function submit(event) {
    event.preventDefault();
    if (submitting.current || status !== "MORE_INFO_REQUIRED") return;
    const text = response.trim();
    if (!text || text.length > 1000 || new TextEncoder().encode(text).length > 1000) {
      setSubmitError("Enter a response, or shorten it to fit the message limit.");
      return;
    }
    submitting.current = true;
    setSaving(true);
    setSubmitError("");
    setNotice("");
    try {
      const updated = workflow === "preclosure"
        ? await preClosureRequest(token, `/${encodeURIComponent(requestId)}/additional-information`, {
          method: "POST", body: JSON.stringify({ response: text }),
        })
        : await submitAdditionalInformation(token, requestId, text);
      if (!mounted.current) return;
      onSubmitted?.(updated);
      setResponse("");
      setNotice("Your information was submitted to the assigned officer for review. Payment becomes available after approval and quote acceptance.");
      setRefresh((value) => value + 1);
    } catch (error) {
      if (mounted.current) setSubmitError(error.message);
    } finally {
      submitting.current = false;
      if (mounted.current) setSaving(false);
    }
  }

  const awaiting = status === "MORE_INFO_REQUIRED";
  const bytes = new TextEncoder().encode(response.trim()).length;
  return (
    <section className="mt-6 rounded-2xl border border-gray-200 bg-white p-5 text-gray-900 sm:p-6" aria-label="Additional information">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">Additional Information</h3>
          <p className="mt-1 text-sm text-gray-500">{workflow === "preclosure" ? "Pre-closure Request" : "Exit Request"} #{requestId}</p>
        </div>
        <button type="button" onClick={() => {
          // The officer must reload status too: a reply changes MORE_INFO_REQUIRED to UNDER_REVIEW.
          // The parent reload remounts this panel and fetches this request's latest conversation.
          if (onRefresh) onRefresh();
          else setRefresh((value) => value + 1);
        }} disabled={loading || saving}
          className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-semibold hover:bg-gray-50 disabled:opacity-50">
          Refresh Conversation
        </button>
      </div>
      {loading && <p className="mt-3 text-sm text-gray-500">Loading officer remarks and replies...</p>}
      {historyError && <p role="alert" className="mt-3 text-sm text-red-700">{historyError}</p>}
      {!loading && !historyError && messages.length === 0 && (
        <p className="mt-3 text-sm text-gray-600">
          {awaiting ? "No specific officer remarks were recorded for this request." : "No additional information has been requested."}
        </p>
      )}
      {!loading && !historyError && messages.length > 0 && (
        <ol className="mt-4 space-y-3">
          {messages.map((message) => (
            <li key={message.id} className={`rounded-xl border p-4 ${message.actor === "OFFICER" ? "border-amber-200 bg-amber-50" : "border-green-200 bg-green-50"}`}>
              <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <strong>{message.actor === "OFFICER" ? "Officer requested" : "Borrower response"}</strong>
                <time dateTime={message.submittedAt} className="text-xs text-gray-500">{message.submittedAt?.replace("T", " ").slice(0, 16)}</time>
              </div>
              <p className="mt-2 whitespace-pre-wrap break-words text-sm">{message.message || "No remarks recorded."}</p>
            </li>
          ))}
        </ol>
      )}
      {officer && awaiting && (
        <p className="mt-4 text-sm text-amber-900">Awaiting the borrower's response. Once submitted, the same request returns to Under Review for your decision.</p>
      )}
      {!officer && awaiting && (
        <form onSubmit={submit} className="mt-5">
          <label htmlFor={`request-response-${requestId}`} className="block text-sm font-semibold">Provide Additional Information</label>
          <p className="mt-1 text-sm text-gray-600">Read the officer's request above and provide the requested clarification.</p>
          <textarea id={`request-response-${requestId}`} value={response} onChange={(event) => setResponse(event.target.value)}
            maxLength={1000} rows={4} required disabled={saving}
            placeholder="Enter your explanation for the reviewing officer"
            className="mt-3 w-full rounded-xl border border-gray-300 p-3 text-sm disabled:opacity-50" />
          <p className="mt-1 text-xs text-gray-500">{response.length}/1,000 characters</p>
          {bytes > 1000 && <p role="alert" className="mt-1 text-xs text-red-700">Please shorten your reply to fit the message limit.</p>}
          <button type="submit" disabled={saving || loading || !!historyError || !response.trim() || bytes > 1000}
            className="mt-4 rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50">
            {saving ? "Submitting..." : "Submit for Review"}
          </button>
        </form>
      )}
      {submitError && <p role="alert" className="mt-3 text-sm text-red-700">{submitError}</p>}
      {notice && <p role="status" className="mt-3 text-sm text-green-800">{notice}</p>}
    </section>
  );
}
