// Direct connection to your collateral service. Use 8080 only after gateway routing is verified.
const API_URL = "http://localhost:8087";
const BASE = "/api/collateral-release/requests";

async function request(session, path, method = "GET", body, signal) {
  if (!session?.token || session.accountType !== "OFFICER" ||
      !["COLLATERAL_OFFICER", "ADMIN"].includes(session.role)) {
    throw new Error("Sign in as a collateral officer.");
  }
  const response = await fetch(`${API_URL}${path}`, {
    method,
    signal,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; }
  catch { if (response.ok) throw new Error("Invalid server response."); }
  if (!response.ok) {
    const error = new Error(data?.detail || data?.message || `Collateral request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
}

export async function getAssignedReleaseRequests(session, signal) {
  const data = await request(session, `${BASE}/assigned`, "GET", undefined, signal);
  if (!Array.isArray(data)) throw new Error("Unexpected assigned-request response.");
  return data;
}

export function decideReleaseRequest(session, requestId, decision, signal) {
  if (!["APPROVE", "REJECT"].includes(decision)) throw new Error("Choose APPROVE or REJECT.");
  return request(session, `${BASE}/${encodeURIComponent(requestId)}/decision`, "PATCH", { decision }, signal);
}

export function completeReleaseRequest(session, requestId, signal) {
  return request(session, `${BASE}/${encodeURIComponent(requestId)}/complete-release`, "PATCH", undefined, signal);
}
