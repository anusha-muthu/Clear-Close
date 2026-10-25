
const API_URL = "http://localhost:8091";

// Foreclosure uses the same gateway and JWT as cooling-off, with separate request IDs.
export async function preClosureRequest(token, path = "", options = {}) {
  const response = await fetch(`${API_URL}/api/pre-closures${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
  });
  if (!response.ok) {
    const error = new Error(await getErrorMessage(response,
      `Unable to process pre-closure (HTTP ${response.status}).`, token));
    error.status = response.status;
    throw error;
  }
  return response.json();
}

function preClosurePayment(data) {
  return {
    ...data,
    paymentStatus: data.status,
    amountPaise: Math.round(Number(data.amount) * 100),
    currency: "INR",
    payable: ["NOT_STARTED", "INITIATED"].includes(data.status),
    message: data.status === "SUCCESS"
      ? "Payment verified. Refresh exit status to see the final loan closure."
      : "Pay the accepted quotation using Razorpay Test Mode.",
  };
}

export async function getPreClosurePayment(token, requestId) {
  return preClosurePayment(await preClosureRequest(token, `/${encodeURIComponent(requestId)}/payment`));
}

export async function createPreClosureOrder(token, requestId) {
  return preClosurePayment(await preClosureRequest(token, `/${encodeURIComponent(requestId)}/payment/order`, { method: "POST" }));
}

export async function verifyPreClosurePayment(token, requestId, response) {
  return preClosurePayment(await preClosureRequest(token, `/${encodeURIComponent(requestId)}/payment/verify`, {
    method: "POST",
    body: JSON.stringify({
      razorpayOrderId: response.razorpay_order_id,
      razorpayPaymentId: response.razorpay_payment_id,
      razorpaySignature: response.razorpay_signature,
    }),
  }));
}

async function paymentRequest(path, token, body) {
  const response = await fetch(`${API_URL}/api/payments${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    throw new Error(await getErrorMessage(response, `Payment request failed (HTTP ${response.status}).`));
  }
  return response.json();
}

export function getSettlementPayment(token, quoteId) {
  return paymentRequest(`/quotes/${encodeURIComponent(quoteId)}/status`, token);
}

export function createSettlementOrder(token, quoteId) {
  return paymentRequest("/orders", token, { quoteId });
}

export function verifySettlementPayment(token, checkoutResponse) {
  return paymentRequest("/verify", token, {
    razorpayOrderId: checkoutResponse.razorpay_order_id,
    razorpayPaymentId: checkoutResponse.razorpay_payment_id,
    razorpaySignature: checkoutResponse.razorpay_signature,
  });
}

async function getErrorMessage(response, fallbackMessage, token) {
  if (response.status === 401 && token) {
    // Decode only for the error wording; the backend still validates the JWT.
    // A 401 can mean a service rejected the signature/issuer, not just expiry.
    try {
      const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
      const claims = JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")));
      if (Number.isFinite(claims.exp) && claims.exp * 1000 <= Date.now()) {
        return "Your session has expired. Please log in again.";
      }
    } catch {
      // A malformed token is an authentication failure, not proof of expiry.
    }
    return "Unable to authenticate your login with this service. Please retry.";
  }
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
    `${API_URL}/api/auth/borrower/login`,
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

    throw new Error(await getErrorMessage(response, fallback, token));
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

    throw new Error(await getErrorMessage(response, fallback, token));
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

    throw new Error(await getErrorMessage(response, fallback, token));
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
    throw new Error(await getErrorMessage(response, fallback, token));
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
    throw new Error(await getErrorMessage(response, fallback, token));
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
    const error = new Error(await getErrorMessage(response, fallback, token));
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

export function getBorrowerInformationHistory(token, requestId) {
  return coolingOffQuoteRequest(`/api/cooling-off/requests/${encodeURIComponent(requestId)}/information-history`, token);
}

export function submitAdditionalInformation(token, requestId, response) {
  return coolingOffQuoteRequest(`/api/cooling-off/requests/${encodeURIComponent(requestId)}/additional-information`, token, {
    method: "POST",
    body: JSON.stringify({ response }),
  });
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
