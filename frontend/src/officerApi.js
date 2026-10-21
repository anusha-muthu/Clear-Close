
const API_URL = "http://localhost:8080";

// Common API request function
export function getOfficerInformationHistory(token, requestId) {
  return apiRequest(`/api/officer/cooling-off/requests/${encodeURIComponent(requestId)}/information-history`, token);
}

async function apiRequest(path, token, method = "GET", body = null) {

  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });

  const text = await response.text();

  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }

  if (!response.ok) {
    let message;
    if (response.status === 401) {
      message = "Session expired. Please log in again.";
    } else if (response.status === 400) {
      message = "The request contains invalid information.";
    } else if (response.status === 403) {
      message = "Access denied. Check officer permissions or self-review restrictions.";
    } else if (response.status === 404) {
      message = "The cooling-off request was not found.";
    } else if (response.status === 409) {
      message = "The request cannot be updated in its current state or requires further verification.";
    } else {
      message = `Request failed (${response.status})`;
    }

    const error = new Error(data?.message || data?.detail || data?.error || message);
    error.status = response.status;
    throw error;
  }

  return data;
}

// -----------------------------------------
// 1. OFFICER LOGIN
// -----------------------------------------

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
    throw new Error("Only Loan Closure Officers can access this portal.");
  }

  return data;
}

// -----------------------------------------
// 2. GET OFFICER REQUESTS
// -----------------------------------------

export function getOfficerRequests(token) {
  return apiRequest(
    "/api/officer/cooling-off/requests",
    token
  );
}

// -----------------------------------------
// 3. GET REQUEST DETAILS
// -----------------------------------------

export function getOfficerRequestDetails(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${requestId}`,
    token
  );
}

// -----------------------------------------
// 4. GET DASHBOARD SUMMARY
// -----------------------------------------

export function getOfficerSummary(token) {
  return apiRequest(
    "/api/officer/cooling-off/summary",
    token
  );
}

// -----------------------------------------
// 5. ASSIGN REQUEST
// -----------------------------------------

export function assignRequest(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${requestId}/assign`,
    token,
    "PATCH"
  );
}

// -----------------------------------------
// 6. OFFICER DECISION - STEP 24
// -----------------------------------------

export function makeOfficerDecision(
  token,
  requestId,
  decision,
  remarks
) {
  const allowedDecisions = ["APPROVED", "REJECTED", "MORE_INFO_REQUIRED"];
  const normalizedRemarks = String(remarks || "").trim();

  if (!allowedDecisions.includes(decision)) {
    throw new Error("Choose a valid officer decision.");
  }
  if (!normalizedRemarks) {
    throw new Error("Decision remarks are required.");
  }
  if (normalizedRemarks.length > 1000) {
    throw new Error("Decision remarks must be 1000 characters or fewer.");
  }

  return apiRequest(
    `/api/officer/cooling-off/requests/${requestId}/decision`,
    token,
    "PATCH",
    { decision, remarks: normalizedRemarks }
  );
}
