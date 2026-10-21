const COLLATERAL_URL = (
  import.meta.env.VITE_COLLATERAL_API_URL || "http://localhost:8087"
).replace(/\/$/, "");

async function request(path, session, options = {}) {
  if (!session?.token) throw new Error("Please sign in first.");
  const controller = new AbortController();
  const abortFromCaller = () => controller.abort();
  options.signal?.addEventListener("abort", abortFromCaller, { once: true });
  if (options.signal?.aborted) controller.abort();
  let timedOut = false;
  const timeout = setTimeout(() => { timedOut = true; controller.abort(); }, 30000);
  try {
    const response = await fetch(`${COLLATERAL_URL}/api/collateral-release/requests${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${session.token}`,
        ...(options.body ? { "Content-Type": "application/json" } : {}),
      },
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const fallback = response.status === 401 ? "Your session expired or Collateral rejected your token. Please sign in again."
        : response.status === 403 ? "You are not allowed to access these requests."
        : response.status === 409 ? "A release request already exists. Refresh to view it."
        : `Collateral request failed (HTTP ${response.status}).`;
      throw new Error(body?.message || body?.detail || fallback);
    }
    if (body === null) throw new Error("The server returned an empty response. Refresh the request list before submitting again.");
    return body;
  } catch (error) {
    if (timedOut) throw new Error("The request timed out. Its database outcome is unknown; refresh the list before submitting again.");
    throw error;
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abortFromCaller);
  }
}

export function getMyCollateralRequests(session, signal) {
  return request("/my-requests", session, { signal });
}

export function submitCollateralRequest(session, form) {
  const collateralId = Number(form.collateralId);
  const loanAccountId = Number(form.loanAccountId);
  if (!Number.isSafeInteger(collateralId) || collateralId <= 0
      || !Number.isSafeInteger(loanAccountId) || loanAccountId <= 0) {
    throw new Error("Enter valid numeric collateral and loan IDs.");
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.expectedReleaseDate)) {
    throw new Error("Select a target release date.");
  }
  return request("/submit", session, {
    method: "POST",
    body: JSON.stringify({
      collateralId,
      loanAccountId,
      reason: form.reason,
      expectedReleaseDate: `${form.expectedReleaseDate}T00:00:00`,
    }),
  });
}
