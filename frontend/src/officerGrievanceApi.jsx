const API_URL = "http://localhost:8086";
const BASE = "/api/grievances";

async function request(session, path, options = {}) {
  if (!session?.token || session.accountType !== "OFFICER") {
    throw new Error("Sign in with an officer account.");
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
  });

  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    if (response.ok) throw new Error("The server returned an invalid response.");
  }

  if (!response.ok) {
    const messages = {
      401: "Your session expired. Sign in again.",
      403: "You cannot access or update this complaint.",
      404: "The grievance was not found.",
      409: "This grievance changed. Refresh before updating it.",
    };
    const error = new Error(
      data?.detail || data?.message || messages[response.status] ||
      `Request failed (${response.status}).`
    );
    error.status = response.status;
    throw error;
  }
  return data;
}

export async function getAssignedGrievances(session, signal) {
  if (!session?.userId) throw new Error("Officer identity is missing; sign in again.");
  const data = await request(session,
    `${BASE}/officer/${encodeURIComponent(session.userId)}`, { signal });
  if (!Array.isArray(data)) throw new Error("Unexpected grievance list response.");
  return data;
}

export function updateAssignedGrievance(session, grievanceId, change, signal) {
  return request(session, `${BASE}/${encodeURIComponent(grievanceId)}/status`, {
    method: "PATCH",
    // Existing controller accepts status + remarks. It does not support expectedStatus.
    body: JSON.stringify({ status: change.status, remarks: change.remarks }),
    signal,
  });
}
