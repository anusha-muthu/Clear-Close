import { useEffect, useRef, useState } from "react";
import { getCollateralEligibility, getCollateralsByLoan } from "../collateralApi.js";

const field = "mt-2 w-full rounded-xl border border-[#dfe5df] bg-white px-4 py-3 text-sm";

export default function CollateralEligibilityFields({ session, form, setForm, disabled, onCheck }) {
  const [assets, setAssets] = useState([]);
  const [loadedLoan, setLoadedLoan] = useState("");
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState("");
  const [eligibility, setEligibility] = useState(null);
  const [revision, setRevision] = useState(0);
  const listController = useRef(null);
  const checkController = useRef(null);
  const token = session?.token;
  const loan = String(form.loanAccountId);
  const collateral = String(form.collateralId);
  const belongsToLoadedLoan = loadedLoan === loan;

  useEffect(() => () => {
    listController.current?.abort();
    checkController.current?.abort();
  }, [token]);

  function changeLoan(value) {
    listController.current?.abort(); checkController.current?.abort();
    onCheck(null); setAssets([]); setLoadedLoan("");
    setLoading(false); setChecking(false); setError(""); setEligibility(null);
    setForm((previous) => ({ ...previous, loanAccountId: value, collateralId: "" }));
  }

  async function loadAssets() {
    listController.current?.abort(); checkController.current?.abort();
    const controller = new AbortController(); listController.current = controller;
    onCheck(null); setEligibility(null); setError(""); setAssets([]); setLoadedLoan("");
    setChecking(false); setLoading(true);
    setForm((previous) => ({ ...previous, collateralId: "" }));
    try {
      const rows = await getCollateralsByLoan({ token }, loan, controller.signal);
      if (controller.signal.aborted) return;
      setAssets(rows); setLoadedLoan(loan);
    } catch (err) {
      if (!controller.signal.aborted) setError(err.message);
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  useEffect(() => {
    onCheck(null); setEligibility(null); setError("");
    checkController.current?.abort();
    if (!collateral || !belongsToLoadedLoan ||
        !assets.some((asset) => String(asset.collateralId) === collateral)) {
      setChecking(false); return;
    }
    const controller = new AbortController(); checkController.current = controller;
    setChecking(true);
    getCollateralEligibility({ token }, collateral, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        if (String(result.loanAccountId) !== String(Number(loan))) {
          throw new Error("The collateral does not belong to the selected loan. Reload collateral.");
        }
        setEligibility(result);
        onCheck({ loanAccountId: loan, collateralId: collateral, token,
          allowed: result.canSubmitRequest === true });
      })
      .catch((err) => { if (!controller.signal.aborted) setError(err.message); })
      .finally(() => { if (!controller.signal.aborted) setChecking(false); });
    return () => controller.abort();
  }, [token, loan, collateral, belongsToLoadedLoan, assets, revision, onCheck]);

  function changeAsset(value) {
    checkController.current?.abort(); onCheck(null); setEligibility(null); setError("");
    setForm((previous) => ({ ...previous, collateralId: value }));
  }

  return <section aria-label="Select collateral and check eligibility" className="space-y-4">
    <label className="block text-sm font-semibold">Loan Account ID *
      <input name="loanAccountId" type="number" min="1" step="1" required disabled={disabled}
        value={loan} onChange={(event) => changeLoan(event.target.value)}
        placeholder="Enter your loan account ID" className={field} />
    </label>
    <button type="button" disabled={disabled || loading || !loan} onClick={loadAssets}
      className="rounded-full border border-black px-5 py-2 text-sm font-semibold disabled:opacity-50">
      {loading ? "Loading collateral..." : "Load collateral"}
    </button>
    {belongsToLoadedLoan && !assets.length && <p role="status" className="text-sm text-gray-600">No collateral is registered for this loan.</p>}
    <label className="block text-sm font-semibold">Collateral *
      <select name="collateralId" required disabled={disabled || loading || !belongsToLoadedLoan || !assets.length}
        value={collateral} onChange={(event) => changeAsset(event.target.value)} className={field}>
        <option value="">Select collateral linked to this loan</option>
        {assets.map((asset) => <option key={asset.collateralId} value={asset.collateralId}>
          {asset.collateralType || "Asset"} — #{asset.collateralId}{asset.description ? ` — ${asset.description}` : ""}
        </option>)}
      </select>
    </label>
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {checking && <p role="status" className="text-sm text-gray-600">Checking release eligibility...</p>}
    {eligibility && <div role="status" className={`rounded-xl p-4 text-sm ${eligibility.canSubmitRequest ? "bg-lime-50 text-lime-800" : "bg-orange-50 text-orange-800"}`}>
      <p className="font-semibold">{eligibility.canSubmitRequest ? "Eligible to request release" : "Release request unavailable"}</p>
      <p className="mt-1">{eligibility.message}</p>
      <p className="mt-2 text-xs">Existing request: {String(eligibility.requestStatus || "NONE").replaceAll("_", " ")}</p>
    </div>}
    {collateral && belongsToLoadedLoan && <button type="button" disabled={disabled || checking}
      onClick={() => { onCheck(null); setEligibility(null); setRevision((value) => value + 1); }}
      className="text-sm font-semibold underline disabled:opacity-50">Check eligibility again</button>}
  </section>;
}
