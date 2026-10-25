const COLLATERAL_URL = "http://localhost:8091";

const BASE = "/api/collateral-release/requests";

async function request(path, session, options = {}, basePath = BASE) {
  if (!session?.token) throw new Error("Please sign in first.");

  const controller = new AbortController();
  const abortFromCaller = () => controller.abort();
  options.signal?.addEventListener("abort", abortFromCaller, { once: true });
  if (options.signal?.aborted) controller.abort();

  let timedOut = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, 30000);

  try {
    const response = await fetch(`${COLLATERAL_URL}${basePath}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${session.token}`,
        ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
    });

    const data = await response.json().catch(() => null);
    if (!response.ok) {
      const messages = {
        400: "Check the submitted request information.",
        401: "Your session expired. Please sign in again.",
        403: "You cannot access this request.",
        404: "The requested endpoint or record was not found.",
        409: "Request conflicts with the current state. Refresh the list.",
      };
      const error = new Error(data?.detail || data?.message || messages[response.status]
        || `Collateral request failed (${response.status}).`);
      error.status = response.status;
      throw error;
    }
    if (data === null) {
      throw new Error("Unexpected server response. Refresh before submitting again.");
    }
    return data;
  } catch (error) {
    if (timedOut) {
      throw new Error("Request timed out. Refresh the list before retrying; it may already have been saved.");
    }
    throw error;
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abortFromCaller);
  }
}

export async function getMyCollateralRequests(session, signal) {
  const data = await request("/my-requests", session, { signal });
  if (!Array.isArray(data)) throw new Error("Unexpected request-list response.");
  return data;
}

export async function submitCollateralRequest(session, form, signal) {
  if (!session?.token || session.accountType !== "BORROWER") {
    throw new Error("Sign in as a borrower.");
  }
  const collateralId = Number(form.collateralId);
  const loanAccountId = Number(form.loanAccountId);
  if (!Number.isSafeInteger(collateralId) || collateralId <= 0
      || !Number.isSafeInteger(loanAccountId) || loanAccountId <= 0) {
    throw new Error("Enter valid collateral and loan IDs.");
  }
  const data = await request("/submit", session, {
    signal,
    method: "POST",
    body: JSON.stringify({
      collateralId,
      loanAccountId,
      reason: form.reason || "LOAN_FULLY_PAID",
    }),
  });
  if (data?.requestId == null) {
    throw new Error("Unexpected saved response. Refresh before retrying.");
  }
  return data;
}

function positiveId(value, name) {
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error(`Enter a valid ${name}.`);
  return id;
}

export async function getCollateralsByLoan(session, loanId, signal) {
  const id = positiveId(loanId, "loan account ID");
  const data = await request(`/loan/${id}`, session, { signal }, "/api/collateral");
  if (!Array.isArray(data) || data.some((asset) =>
      !asset || !Number.isSafeInteger(Number(asset.collateralId)) || Number(asset.collateralId) <= 0 ||
      Number(asset.loanAccountId) !== id)) {
    throw new Error("Unexpected collateral list response. Reload the loan's collateral.");
  }
  return data;
}

export async function getCollateralEligibility(session, collateralId, signal) {
  const id = positiveId(collateralId, "collateral ID");
  const data = await request(`/${id}/eligibility`, session, { signal }, "/api/collateral");
  if (!data || Number(data.collateralId) !== id ||
      !Number.isSafeInteger(Number(data.loanAccountId)) || Number(data.loanAccountId) <= 0 ||
      typeof data.canSubmitRequest !== "boolean" || typeof data.eligible !== "boolean" ||
      typeof data.message !== "string") {
    throw new Error("Unexpected eligibility response. Check eligibility again.");
  }
  return data;
}
