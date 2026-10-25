const API_URL = "http://localhost:8091";
const BASE = "/api/grievances";

async function request(session, path, options = {}) {
  if (!session?.token || session.accountType !== "OFFICER") {
    throw new Error("Sign in with an officer account.");
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30000);

  const forwardAbort = () => controller.abort();
  options.signal?.addEventListener("abort", forwardAbort, { once: true });

  try {
    const response = await fetch(`${API_URL}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${session.token}`,
        ...(options.body !== undefined
          ? { "Content-Type": "application/json" }
          : {}),
        ...(options.headers || {}),
      },
    });

    const text = await response.text();
    let data = null;

    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        throw new Error("The server returned an invalid response.");
      }
    }

    if (!response.ok) {
      const messages = {
        401: "Your session expired. Sign in again.",
        403: "You are not allowed to access this grievance.",
        404: "The grievance endpoint was not found.",
        409: "The grievance changed. Refresh and try again.",
      };

      throw new Error(
        data?.detail ||
        data?.message ||
        messages[response.status] ||
        `Request failed (${response.status})`
      );
    }

    return data;
  } catch (error) {
    if (error.name === "AbortError") {
      throw new Error("Request timed out. Check the grievance service.");
    }
    throw error;
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", forwardAbort);
  }
}

export async function getAssignedGrievances(session, signal) {
  const officerId = session?.officerId ?? session?.userId;

  if (!officerId) {
    throw new Error("Officer identity is missing. Sign in again.");
  }

  const data = await request(
    session,
    `${BASE}/officer/${encodeURIComponent(officerId)}`,
    { signal }
  );

  if (!Array.isArray(data)) {
    throw new Error("Unexpected grievance list response.");
  }

  return data;
}

export async function getSeniorGrievances(session, signal) {
  if (session?.role !== "SENIOR_GRIEVANCE_OFFICER") {
    throw new Error("A senior grievance officer account is required.");
  }

  const data = await request(session, BASE, { signal });

  if (!Array.isArray(data)) {
    throw new Error("Unexpected senior grievance queue response.");
  }

  return data;
}

export async function getSeniorOverdueNotifications(session, signal) {
  const data = await request(
    session,
    `${BASE}/senior/overdue-notifications`,
    { signal }
  );

  if (!Array.isArray(data)) {
    throw new Error("Unexpected overdue notification response.");
  }

  return data;
}

export function updateAssignedGrievance(
  session,
  grievanceId,
  change,
  signal
) {
  return request(
    session,
    `${BASE}/${encodeURIComponent(grievanceId)}/status`,
    {
      method: "PATCH",
      signal,
      body: JSON.stringify({
        status: change.status,
        remarks: change.remarks,
      }),
    }
  );
}

export function assignGrievance(
  grievanceId,
  officerId,
  session,
  signal
) {
  return request(
    session,
    `${BASE}/${encodeURIComponent(grievanceId)}/assign`,
    {
      method: "PATCH",
      signal,
      body: JSON.stringify({
        officerId: Number(officerId),
      }),
    }
  );
}