const GRIEVANCE_URL = (
  import.meta.env.VITE_GRIEVANCE_API_URL || "http://localhost:8086"
).replace(/\/$/, "");

async function grievanceRequest(path, session, options = {}) {
  if (!session?.token || !Number.isSafeInteger(Number(session.userId)) || Number(session.userId) <= 0) {
    throw new Error("Please sign in with a borrower account first.");
  }
  const response = await fetch(`${GRIEVANCE_URL}/api/grievances${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
  });
  if (!response.ok) {
    const fallback = response.status === 401 ? "Your session expired. Please sign in again."
      : response.status === 403 ? "You are not allowed to access these grievances."
      : `Grievance request failed (HTTP ${response.status}).`;
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || body?.detail || fallback);
  }
  return response.json();
}

export function getMyGrievances(session, signal) {
  return grievanceRequest(`/borrower/${encodeURIComponent(session.userId)}`, session, { signal });
}

export function submitGrievance(session, form) {
  const loanAccountId = Number(form.loanAccountId);
  const description = `${form.subject.trim()}\n\n${form.description.trim()}`;
  if (!Number.isSafeInteger(loanAccountId) || loanAccountId <= 0) {
    throw new Error("Enter the numeric loan account ID from your saved loan record.");
  }
  if (description.length > 1000) {
    throw new Error("Subject and description together must be at most 1,000 characters, including the two separating line breaks.");
  }
  return grievanceRequest("", session, {
    method: "POST",
    body: JSON.stringify({
      loanAccountId,
      borrowerId: Number(session.userId),
      category: form.category,
      description,
    }),
  });
}
