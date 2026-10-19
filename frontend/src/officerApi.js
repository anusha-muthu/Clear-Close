
const API_URL = "http://localhost:8080";

// Common function for calling backend APIs
async function apiRequest(path, token, method = "GET") {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  const text = await response.text();
  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Session expired. Please log in again.");
    }

    if (response.status === 403) {
      throw new Error("You are not authorized for this action.");
    }

    throw new Error(
      data?.error ||
      data?.message ||
      `Request failed (${response.status})`
    );
  }

  return data;
}

// 1. Officer Login
export async function loginOfficer(username, password) {
  const response = await fetch(
    `${API_URL}/api/auth/officer/login`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ username, password }),
    }
  );

  if (!response.ok) {
    throw new Error("Invalid username or password.");
  }

  const data = await response.json();

  if (
    !data.token ||
    data.accountType !== "OFFICER" ||
    data.role !== "LOAN_CLOSURE_OFFICER"
  ) {
    throw new Error(
      "Only Loan Closure Officers can access this dashboard."
    );
  }

  return data;
}

// 2. Get all accessible requests
export function getOfficerRequests(token) {
  return apiRequest(
    "/api/officer/cooling-off/requests",
    token
  );
}

// 3. Get one request's details
export function getOfficerRequestDetails(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${requestId}`,
    token
  );
}

// 4. Get dashboard summary
export function getOfficerSummary(token) {
  return apiRequest(
    "/api/officer/cooling-off/summary",
    token
  );
}

// 5. Assign request to logged-in officer
export function assignRequest(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${requestId}/assign`,
    token,
    "PATCH"
  );
}
