const API_URL = (
  import.meta.env.VITE_NOC_API_URL || "http://localhost:8080"
).replace(/\/+$/, "");

const encode = (value) => encodeURIComponent(String(value));

async function request(token, path, options = {}) {
  const { pdf = false, ...fetchOptions } = options;

  let response;

  try {
    response = await fetch(`${API_URL}/api/noc${path}`, {
      ...fetchOptions,
      headers: {
        Accept: pdf ? "application/pdf" : "application/json",
        Authorization: `Bearer ${token}`,
      },
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;

    throw new Error(
      "Unable to connect to the NOC service. Check that the backend and API Gateway are running."
    );
  }

  if (!response.ok) {
    const text = await response.text();
    let data = null;

    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      // The gateway may return a non-JSON error page.
    }

    const fallback = {
      400: "Check the information entered and try again.",
      401: "Unable to authenticate your session. Please sign in again.",
      403: "Your account is not authorized for this NOC operation.",
      404: "Loan or certificate not found for this account.",
      409: "This operation is unavailable in the current certificate state.",
      500: "The NOC operation failed. Refresh the certificates before retrying.",
      503: "The NOC service is currently unavailable.",
    };

    const error = new Error(
      data?.message ||
        data?.detail ||
        data?.remarks ||
        fallback[response.status] ||
        `NOC request failed (HTTP ${response.status}).`
    );

    error.status = response.status;
    throw error;
  }

  if (pdf) {
    const contentType = response.headers.get("content-type") || "";

    if (!contentType.toLowerCase().includes("application/pdf")) {
      throw new Error("The service did not return a PDF certificate.");
    }

    return response.blob();
  }

  if (response.status === 204) return null;

  return response.json();
}

export const getNocEligibility = (token, loanId, signal) =>
  request(token, `/eligibility/${encode(loanId)}`, { signal });

export const getLoanNocs = (token, loanId, signal) =>
  request(token, `/loan/${encode(loanId)}`, { signal });

export const generateNoc = (token, loanId) =>
  request(token, `/generate/${encode(loanId)}`, { method: "POST" });

export const reissueNoc = (token, loanId) =>
  request(token, `/reissue/${encode(loanId)}`, { method: "POST" });

export const retryNoc = (token, nocId) =>
  request(token, `/${encode(nocId)}/retry`, { method: "POST" });

export const verifyNoc = (token, certificateNumber) =>
  request(token, `/verify/${encode(certificateNumber.trim())}`);

export const getNocHistory = (token, nocId, signal) =>
  request(token, `/${encode(nocId)}/history`, { signal });

export async function downloadNoc(token, nocId) {
  const blob = await request(token, `/${encode(nocId)}/download`, {
    pdf: true,
  });

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = `NOC-${nocId}.pdf`;

  document.body.appendChild(link);
  link.click();
  link.remove();

  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}