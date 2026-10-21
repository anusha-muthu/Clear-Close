
const API_URL = "http://localhost:8080";

async function getErrorMessage(response, fallbackMessage) {
  try {
    const body = await response.json();
    return body.message || body.error || body.detail || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

// Borrower Login
export async function loginBorrower(username, password) {
  const response = await fetch(
    `http://localhost:8081/api/auth/borrower/login`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        username: username.trim(),
        password,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      await getErrorMessage(
        response,
        `Login failed (HTTP ${response.status})`,
      ),
    );
  }

  return response.json();
}

// Fetch all loans of logged-in borrower
export async function getMyLoans(token) {
  const response = await fetch(
    `${API_URL}/api/borrower/loans/list`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    const fallback = response.status === 401
      ? "Session expired. Please log in again."
      : response.status === 403
        ? "You are not allowed to access these loans."
        : "Unable to fetch loans. Please try again.";

    throw new Error(await getErrorMessage(response, fallback));
  }

  return response.json();
}

// Fetch one specific loan
export async function getLoanById(token, loanId) {
  const response = await fetch(
    `${API_URL}/api/borrower/loans/${loanId}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error(
      await getErrorMessage(response, "Unable to fetch loan details."),
    );
  }

  return response.json();
}

// Check cooling-off eligibility for one of the borrower's loans.
export async function checkCoolingOffEligibility(token, loanId) {
  const response = await fetch(
    `${API_URL}/api/cooling-off/eligibility/${encodeURIComponent(loanId)}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  if (!response.ok) {
    const fallback = response.status === 401
      ? "Your session has expired. Please log in again."
      : response.status === 403
        ? "You are not allowed to check eligibility for this loan."
        : response.status === 404
          ? "Loan or cooling-off eligibility information was not found."
          : "Unable to check cooling-off eligibility. Please try again.";

    throw new Error(await getErrorMessage(response, fallback));
  }

  return response.json();
}

// Submit a cooling-off exit request for one of the borrower's loans.
export async function submitCoolingOffRequest(token, loanId, reason) {
  const response = await fetch(`${API_URL}/api/cooling-off/requests`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ loanId, reason }),
  });

  if (!response.ok) {
    const fallback = response.status === 400
      ? "The request could not be submitted. Check the loan and reason, then try again."
      : response.status === 401
        ? "Your session has expired. Please log in again."
        : response.status === 403
          ? "You are not allowed to submit a request for this loan."
          : response.status === 404
            ? "Loan or cooling-off request service was not found."
            : response.status === 409
              ? "This loan can no longer accept a cooling-off request. Check eligibility and try again."
              : "Unable to submit the cooling-off request. Please try again later.";

    throw new Error(await getErrorMessage(response, fallback));
  }

  return response.json();
}

// Fetch all cooling-off exit requests belonging to the logged-in borrower.
export async function getMyCoolingOffRequests(token) {
  const response = await fetch(`${API_URL}/api/cooling-off/requests`, {
    method: "GET",
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const fallback = response.status === 401
      ? "Your session has expired. Please log in again."
      : response.status === 403
        ? "You are not allowed to view these cooling-off requests."
        : response.status === 404
          ? "Cooling-off requests were not found."
          : "Unable to load cooling-off requests. Please try again.";
    throw new Error(await getErrorMessage(response, fallback));
  }

  return response.json();
}

// Fetch the latest backend details for one cooling-off exit request.
export async function getCoolingOffRequestById(token, requestId) {
  const response = await fetch(
    `${API_URL}/api/cooling-off/requests/${encodeURIComponent(requestId)}`,
    {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    },
  );

  if (!response.ok) {
    const fallback = response.status === 401
      ? "Your session has expired. Please log in again."
      : response.status === 403
        ? "You are not allowed to view this cooling-off request."
        : response.status === 404
          ? "Cooling-off request was not found."
          : "Unable to load cooling-off request details. Please try again.";
    throw new Error(await getErrorMessage(response, fallback));
  }

  return response.json();
}

async function coolingOffQuoteRequest(path, token, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const fallback = response.status === 401
      ? "Your session has expired. Please log in again."
      : response.status === 403
        ? "You are not allowed to access this settlement quote."
        : response.status === 404
          ? "Settlement quote or exit request was not found."
          : response.status === 409
            ? "The backend cannot process this quote in its current state."
            : "Unable to load the settlement quote. Please try again.";
    const error = new Error(await getErrorMessage(response, fallback));
    error.status = response.status;
    throw error;
  }

  return response.json();
}

export function generateSettlementQuote(token, requestId) {
  return coolingOffQuoteRequest(
    `/api/cooling-off/requests/${encodeURIComponent(requestId)}/quote`,
    token,
    { method: "POST" },
  );
}

export async function getSettlementQuoteByRequest(token, requestId) {
  try {
    return await coolingOffQuoteRequest(
      `/api/cooling-off/requests/${encodeURIComponent(requestId)}/quote`,
      token,
    );
  } catch (error) {
    // No quote is a normal state for a valid request; callers can show the generate action.
    if (error.status === 404) return null;
    throw error;
  }
}

export function getSettlementQuoteById(token, quoteId) {
  return coolingOffQuoteRequest(
    `/api/cooling-off/quotes/${encodeURIComponent(quoteId)}`,
    token,
  );
}

export function acceptSettlementQuote(token, quoteId) {
  return coolingOffQuoteRequest(
    `/api/cooling-off/quotes/${encodeURIComponent(quoteId)}/accept`,
    token,
    { method: "POST" },
  );
}
