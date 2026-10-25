const GRIEVANCE_URL = "http://localhost:8091";

const BASE_PATH = "/api/grievances";

function positiveId(value, message) {
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error(message);
  return id;
}

async function grievanceRequest(path, session, options = {}) {
  if (!session?.token) throw new Error("Please sign in first.");
  const response = await fetch(`${GRIEVANCE_URL}${BASE_PATH}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
  });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; }
  catch { if (response.ok) throw new Error("The server returned an invalid JSON response."); }
  if (!response.ok) {
    const messages = {
      400: "Check your submitted information.",
      401: "Your session expired. Please sign in again.",
      403: "You cannot access this grievance.",
      404: "The grievance or endpoint was not found.",
      409: "A grievance already exists or the request conflicts with its current state.",
    };
    const error = new Error(data?.detail || data?.message || messages[response.status]
      || `Grievance request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  if (data === null) throw new Error("Empty server response. Refresh before retrying a submission.");
  return data;
}

function list(data) {
  if (!Array.isArray(data)) throw new Error("The server returned an unexpected list response.");
  return data;
}

export async function getMyGrievances(session, signal) {
  const borrowerId = positiveId(session?.userId, "Please sign in with a valid borrower account.");
  return list(await grievanceRequest(`/borrower/${borrowerId}`, session, { signal }));
}

export async function submitGrievance(session, form) {
  if (!session?.token || session.accountType !== "BORROWER") {
    throw new Error("Please sign in with a borrower account.");
  }
  const loanAccountId = positiveId(form?.loanAccountId, "Enter a valid numeric loan account ID.");
  const subject = String(form?.subject || "").trim();
  const details = String(form?.description || "").trim();
  const category = String(form?.category || "").trim().toUpperCase();
  if (!subject || !details) throw new Error("Enter a subject and description.");
  if (!["DELAY", "SETTLEMENT", "SYSTEM", "OTHER"].includes(category)) {
    throw new Error("Choose a valid complaint category.");
  }
  const description = `${subject}\n\n${details}`;
  if (description.length > 1000) {
    throw new Error("Subject and description together must be at most 1,000 characters, including the two separating line breaks.");
  }
  const saved = await grievanceRequest("", session, {
    method: "POST",
    body: JSON.stringify({ loanAccountId, category, description }),
  });
  if (saved?.grievanceId == null) throw new Error("Unexpected saved response. Refresh before submitting again.");
  return saved;
}

export function getGrievanceHistory(session, grievanceId, signal) {
  return grievanceRequest(
    `/${encodeURIComponent(grievanceId)}/history`,
    session,
    { signal }
  );
}
