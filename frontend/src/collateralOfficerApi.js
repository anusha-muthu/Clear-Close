// Direct connection to your collateral service. Use 8080 only after gateway routing is verified.
const API_URL = "http://localhost:8091";
const BASE = "/api/collateral-release/requests";

async function request(session, path, method = "GET", body, signal) {
  if (!session?.token || session.accountType !== "OFFICER" ||
      !["COLLATERAL_OFFICER", "ADMIN"].includes(session.role)) {
    throw new Error("Sign in as a collateral officer.");
  }
  const response = await fetch(`${API_URL}${path}`, {
    method,
    signal,
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; }
  catch { if (response.ok) throw new Error("Invalid server response."); }
  if (!response.ok) {
    const error = new Error(data?.detail || data?.message || `Collateral request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
}

export async function getAssignedReleaseRequests(session, signal) {
  const data = await request(session, `${BASE}/assigned`, "GET", undefined, signal);
  if (!Array.isArray(data)) throw new Error("Unexpected assigned-request response.");
  return data;
}

export function decideReleaseRequest(session, requestId, decision, signal) {
  if (!["APPROVE", "REJECT"].includes(decision)) throw new Error("Choose APPROVE or REJECT.");
  return request(session, `${BASE}/${encodeURIComponent(requestId)}/decision`, "PATCH", { decision }, signal);
}

export function completeReleaseRequest(session, requestId, signal) {
  return request(session, `${BASE}/${encodeURIComponent(requestId)}/complete-release`, "PATCH", undefined, signal);
}

const REGISTER_BASE = "/api/collateral";

function positiveId(value, label) {
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error(`Enter a valid ${label}.`);
  return id;
}

function editableFields(form) {
  const collateralType = String(form.collateralType || "").trim();
  const description = String(form.description || "").trim();
  const custodian = String(form.custodian || "").trim();
  if (!collateralType || collateralType.length > 30) throw new Error("Select a valid collateral type.");
  if (description.length > 500) throw new Error("Description must be 500 characters or fewer.");
  if (custodian.length > 100) throw new Error("Custodian must be 100 characters or fewer.");
  return { collateralType, description, custodian };
}

function savedAsset(data, expectedId) {
  if (!data || !Number.isSafeInteger(Number(data.collateralId)) || Number(data.collateralId) <= 0 ||
      !Number.isSafeInteger(Number(data.loanAccountId)) || Number(data.loanAccountId) <= 0 ||
      (expectedId !== undefined && Number(data.collateralId) !== expectedId)) {
    throw new Error("Unexpected saved response. Refresh the register before retrying.");
  }
  return data;
}

export async function getCollateralRegister(session, signal) {
  const data = await request(session, REGISTER_BASE, "GET", undefined, signal);
  if (!Array.isArray(data)) throw new Error("Unexpected collateral register response.");
  data.forEach((row) => savedAsset(row));
  return data;
}

export async function getCollateralTypes(session, signal) {
  const data = await request(session, `${REGISTER_BASE}/types`, "GET", undefined, signal);
  if (!Array.isArray(data) || data.some((type) => typeof type !== "string" || !type.trim())) {
    throw new Error("Unexpected collateral types response.");
  }
  return [...new Set(data)];
}

export async function registerCollateral(session, form, signal) {
  const loanAccountId = positiveId(form.loanAccountId, "loan account ID");
  const body = { loanAccountId, ...editableFields(form) };
  const fileName = String(form.fileName || "").trim();
  const storagePath = String(form.storagePath || "").trim();
  if (fileName.length > 255 || storagePath.length > 500) throw new Error("Document filename or storage path is too long.");
  if (fileName) body.fileName = fileName;
  if (storagePath) body.storagePath = storagePath;
  const data = savedAsset(await request(session, REGISTER_BASE, "POST", body, signal));
  if (Number(data.loanAccountId) !== loanAccountId) {
    throw new Error("Saved response has an unexpected loan ID. Refresh before retrying.");
  }
  return data;
}

export async function updateCollateral(session, collateralId, form, signal) {
  const id = positiveId(collateralId, "collateral ID");
  // Keep the existing loan ID for validated Collateral request bodies.
  // The form cannot edit it; the supplied backend also ignores loan changes.
  const loanAccountId = positiveId(form.loanAccountId, "loan account ID");
  const data = savedAsset(await request(session, `${REGISTER_BASE}/${id}`, "PUT", {
    loanAccountId, ...editableFields(form),
  }, signal), id);
  if (Number(data.loanAccountId) !== loanAccountId) {
    throw new Error("Saved response has an unexpected loan ID. Refresh before retrying.");
  }
  return data;
}
