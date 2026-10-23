const API_URL = "http://localhost:8080";
const AUTH_URL = "http://localhost:8081";

// Portal permissions and dashboard routes.
// Add matching routes in App.jsx.
export const OFFICER_PORTALS = {
  closure: {
    title: "Loan Closure Officer Login",
    description: "Manage cooling-off and loan closure requests.",
    roles: ["LOAN_CLOSURE_OFFICER", "ADMIN"],
    dashboard: "#loan-closure-dashboard",
  },

  grievance: {
    title: "Grievance Officer Login",
    description: "Manage complaints assigned to you.",
    roles: [
      "LOAN_CLOSURE_OFFICER",
      "GRIEVANCE_OFFICER",
      "COLLATERAL_OFFICER",
      "SENIOR_GRIEVANCE_OFFICER",
    ],
    dashboard: "#grievance-officer-dashboard",
  },

  collateral: {
    title: "Collateral Release Officer Login",
    description: "Manage collateral release requests.",
    roles: ["COLLATERAL_OFFICER", "ADMIN"],
    dashboard: "#collateral-officer-dashboard",
  },
};

// -----------------------------------------
// RESPONSE HANDLING
// -----------------------------------------

async function readResponse(response, isLogin = false) {
  const text = await response.text();

  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    // Handle non-JSON error responses below.
  }

  if (!response.ok) {
    const messages = {
      400: "The request contains invalid information.",
      401: isLogin
        ? "Invalid username or password."
        : "Session expired. Please log in again.",
      403: isLogin
        ? "Access denied. Your account may not be active."
        : "Access denied. Check officer permissions or assignment.",
      404: isLogin
        ? "The login endpoint was not found."
        : "The requested resource was not found.",
      409: "The request cannot be updated in its current state.",
      500: "The server encountered an error. Check backend logs.",
    };

    const error = new Error(
      data?.detail ||
      data?.message ||
      messages[response.status] ||
      `Request failed (${response.status}).`
    );

    error.status = response.status;
    throw error;
  }

  if (text && data === null) {
    throw new Error("The server returned an invalid JSON response.");
  }

  return data;
}

// -----------------------------------------
// COMMON AUTHENTICATED REQUEST
// -----------------------------------------

async function apiRequest(
  path,
  token,
  method = "GET",
  body = null
) {
  if (!token) {
    throw new Error("Please log in to continue.");
  }

  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body !== null
        ? { "Content-Type": "application/json" }
        : {}),
    },
    ...(body !== null
      ? { body: JSON.stringify(body) }
      : {}),
  });

  return readResponse(response);
}

// -----------------------------------------
// OFFICER LOGIN
// portal: "closure" | "grievance" | "collateral"
// -----------------------------------------

export async function loginOfficer(
  username,
  password,
  portal = "closure"
) {
  const config = Object.hasOwn(OFFICER_PORTALS, portal)
    ? OFFICER_PORTALS[portal]
    : null;

  if (!config) {
    throw new Error("Unknown officer portal.");
  }

  const normalizedUsername = String(username || "").trim();

  if (!normalizedUsername || !password) {
    throw new Error("Enter your username and password.");
  }

  const response = await fetch(
    `${AUTH_URL}/api/auth/officer/login`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username: normalizedUsername,
        password,
      }),
    }
  );

  const data = await readResponse(response, true);

  if (
    typeof data?.token !== "string" ||
    !data.token.trim() ||
    data.accountType !== "OFFICER"
  ) {
    throw new Error("A valid officer session is required.");
  }

  if (!config.roles.includes(data.role)) {
    throw new Error(
      "Your officer role cannot access this portal."
    );
  }

  return data;
}

// Validate the session role before choosing a dashboard.
export function getOfficerDashboard(portal, role) {
  const config = Object.hasOwn(OFFICER_PORTALS, portal)
    ? OFFICER_PORTALS[portal]
    : null;

  if (!config || !config.roles.includes(role)) {
    throw new Error("Your role cannot access this dashboard.");
  }

  return config.dashboard;
}

// -----------------------------------------
// COOLING-OFF REQUESTS
// -----------------------------------------

export function getOfficerInformationHistory(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${encodeURIComponent(requestId)}/information-history`,
    token
  );
}

export function getOfficerRequests(token) {
  return apiRequest(
    "/api/officer/cooling-off/requests",
    token
  );
}

export function getOfficerRequestDetails(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${encodeURIComponent(requestId)}`,
    token
  );
}

export function getOfficerSummary(token) {
  return apiRequest(
    "/api/officer/cooling-off/summary",
    token
  );
}

export function assignRequest(token, requestId) {
  return apiRequest(
    `/api/officer/cooling-off/requests/${encodeURIComponent(requestId)}/assign`,
    token,
    "PATCH"
  );
}

export function makeOfficerDecision(
  token,
  requestId,
  decision,
  remarks
) {
  const allowedDecisions = [
    "APPROVED",
    "REJECTED",
    "MORE_INFO_REQUIRED",
  ];

  const normalizedRemarks = String(remarks || "").trim();

  if (!allowedDecisions.includes(decision)) {
    throw new Error("Choose a valid officer decision.");
  }

  if (!normalizedRemarks) {
    throw new Error("Decision remarks are required.");
  }

  if (normalizedRemarks.length > 1000) {
    throw new Error(
      "Decision remarks must be 1000 characters or fewer."
    );
  }

  return apiRequest(
    `/api/officer/cooling-off/requests/${encodeURIComponent(requestId)}/decision`,
    token,
    "PATCH",
    {
      decision,
      remarks: normalizedRemarks,
    }
  );
}
