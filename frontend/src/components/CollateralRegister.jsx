import { useEffect, useRef, useState } from "react";
import {
  getCollateralRegister, getCollateralTypes, registerCollateral, updateCollateral,
} from "../collateralOfficerApi.js";

const empty = { loanAccountId: "", collateralType: "", description: "", custodian: "", fileName: "", storagePath: "" };
const field = "mt-2 w-full rounded-xl border border-gray-300 bg-white px-4 py-3 text-sm disabled:bg-gray-100";

export default function CollateralRegister({ session, onBusyChange }) {
  const permitted = Boolean(session?.token && session.accountType === "OFFICER"
    && ["COLLATERAL_OFFICER", "ADMIN"].includes(session.role));
  const [rows, setRows] = useState([]);
  const [types, setTypes] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ ...empty });
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [typeError, setTypeError] = useState("");
  const [formError, setFormError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const mutation = useRef(null);
  const busy = useRef(false);
  const token = session?.token;
  const role = session?.role;
  const userId = session?.userId;
  const selected = rows.find((row) => String(row.collateralId) === String(selectedId));

  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setLoading(true); setRows([]); setTypes([]); setSelectedId(null);
    setEditing(false); setForm({ ...empty }); setLoadError(""); setTypeError("");
    setFormError(""); setNotice("");
    if (!permitted) { setLoading(false); return () => controller.abort(); }
    const auth = { token, accountType: "OFFICER", role };
    Promise.allSettled([
      getCollateralRegister(auth, controller.signal),
      getCollateralTypes(auth, controller.signal),
    ]).then(([list, choices]) => {
      if (!active) return;
      if (list.status === "fulfilled") setRows([...list.value].sort((a, b) => b.collateralId - a.collateralId));
      else if (list.reason.name !== "AbortError") setLoadError(list.reason.message);
      if (choices.status === "fulfilled") setTypes(choices.value);
      else if (choices.reason.name !== "AbortError") setTypeError(choices.reason.message);
      setLoading(false);
    });
    return () => { active = false; controller.abort(); };
  }, [token, role, userId, permitted, revision]);

  useEffect(() => {
    setSaving(false); onBusyChange?.(false);
    return () => { mutation.current?.abort(); busy.current = false; onBusyChange?.(false); };
  }, [token, role, userId, onBusyChange]);

  function startNew() {
    if (busy.current) return;
    setSelectedId(null); setEditing(false); setForm({ ...empty });
    setFormError(""); setNotice("");
  }

  function startEdit(row) {
    if (busy.current) return;
    setSelectedId(row.collateralId); setEditing(true);
    setForm({ loanAccountId: String(row.loanAccountId), collateralType: row.collateralType || "",
      description: row.description || "", custodian: row.custodian || "",
      fileName: row.fileName || "", storagePath: row.storagePath || "" });
    setFormError(""); setNotice("");
  }

  async function save(event) {
    event.preventDefault();
    if (!permitted || busy.current || loading || loadError || typeError) return;
    if (!types.includes(form.collateralType) && !(editing && selected?.collateralType === form.collateralType)) {
      setFormError("Select one of the collateral types returned by the service."); return;
    }
    if (editing && !selected) { setFormError("Select a collateral record again."); return; }
    const controller = new AbortController(); mutation.current = controller; busy.current = true;
    setSaving(true); onBusyChange?.(true); setFormError(""); setNotice("");
    try {
      const saved = editing
        ? await updateCollateral(session, selected.collateralId, form, controller.signal)
        : await registerCollateral(session, form, controller.signal);
      if (controller.signal.aborted) return;
      setRows((previous) => [saved, ...previous.filter((row) => String(row.collateralId) !== String(saved.collateralId))]
        .sort((a, b) => b.collateralId - a.collateralId));
      setSelectedId(saved.collateralId); setEditing(true);
      setForm({ loanAccountId: String(saved.loanAccountId), collateralType: saved.collateralType || "",
        description: saved.description || "", custodian: saved.custodian || "",
        fileName: saved.fileName || "", storagePath: saved.storagePath || "" });
      setNotice(`Collateral #${saved.collateralId} ${editing ? "updated" : "registered"}.`);
    } catch (err) {
      if (!controller.signal.aborted) setFormError(err.message);
    } finally {
      if (mutation.current === controller) {
        busy.current = false;
        if (!controller.signal.aborted) { setSaving(false); onBusyChange?.(false); }
      }
    }
  }

  const query = search.trim().toLowerCase();
  const visible = rows.filter((row) => [row.collateralId, row.loanAccountId, row.collateralType, row.description, row.custodian]
    .some((value) => String(value ?? "").toLowerCase().includes(query)));
  const change = (key, value) => setForm((previous) => ({ ...previous, [key]: value }));
  if (!permitted) return <p role="alert">Sign in as a collateral officer.</p>;

  return <section aria-label="Staff collateral register" className="mx-auto max-w-7xl space-y-5">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h2 className="text-2xl font-bold">Collateral register</h2><p className="mt-1 text-sm text-gray-500">Register assets and maintain their details.</p></div>
      <div className="flex gap-3"><button type="button" disabled={saving || loading} onClick={startNew} className="rounded-full bg-black px-5 py-3 text-sm font-semibold text-white disabled:opacity-50">Register collateral</button><button type="button" disabled={saving || loading} onClick={() => setRevision((value) => value + 1)} className="rounded-full border px-5 py-3 text-sm disabled:opacity-50">Refresh</button></div>
    </div>
    {loadError && <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-800">{loadError}</p>}
    {notice && <p role="status" className="rounded-xl bg-lime-50 p-4 text-sm text-lime-800">{notice}</p>}
    <div className="grid items-start gap-6 lg:grid-cols-3">
      <div className="overflow-hidden rounded-2xl border bg-white lg:col-span-2">
        <div className="border-b p-4"><input type="search" aria-label="Search collateral register" placeholder="Search asset, loan, type or custodian" value={search} onChange={(e) => setSearch(e.target.value)} className={field} /></div>
        <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="p-4">Collateral</th><th className="p-4">Loan</th><th className="p-4">Custodian</th><th className="p-4">Release status</th></tr></thead><tbody>
          {visible.map((row) => <tr key={row.collateralId} className={`border-t ${String(selectedId) === String(row.collateralId) ? "bg-lime-50" : ""}`}>
            <td className="p-4"><button type="button" disabled={saving} onClick={() => startEdit(row)} className="font-bold underline">#{row.collateralId}</button><p className="mt-1">{row.collateralType}</p><p className="mt-1 max-w-64 truncate text-xs text-gray-500">{row.description}</p></td><td className="p-4">{row.loanAccountId}</td><td className="p-4">{row.custodian || "—"}</td><td className="p-4">{String(row.releaseStatus || "Unknown").replaceAll("_", " ")}</td>
          </tr>)}
        </tbody></table>{!visible.length && <p className="p-10 text-center text-sm text-gray-500">{loading ? "Loading collateral..." : loadError ? "Unable to load collateral." : "No collateral matches this view."}</p>}</div>
      </div>
      <form onSubmit={save} className="space-y-4 rounded-2xl border bg-white p-6">
        <h3 className="text-xl font-bold">{editing ? `Edit collateral #${selectedId}` : "Register collateral"}</h3>
        {typeError && <p role="alert" className="text-sm text-red-700">Unable to load collateral types: {typeError}</p>}
        {formError && <p role="alert" className="text-sm text-red-700">{formError}</p>}
        <label className="block text-sm font-semibold">Loan Account ID *<input type="number" min="1" step="1" required disabled={saving || editing} value={form.loanAccountId} onChange={(e) => change("loanAccountId", e.target.value)} className={field} /></label>
        <label className="block text-sm font-semibold">Collateral type *<select required disabled={saving || loading || Boolean(typeError)} value={form.collateralType} onChange={(e) => change("collateralType", e.target.value)} className={field}><option value="">Select type</option>{editing && form.collateralType && !types.includes(form.collateralType) && <option value={form.collateralType}>{form.collateralType} (existing)</option>}{types.map((type) => <option key={type} value={type}>{type.replaceAll("_", " ")}</option>)}</select></label>
        <label className="block text-sm font-semibold">Description<textarea maxLength={500} rows={3} disabled={saving} value={form.description} onChange={(e) => change("description", e.target.value)} className={field} /></label>
        <label className="block text-sm font-semibold">Custodian<input maxLength={100} disabled={saving} value={form.custodian} onChange={(e) => change("custodian", e.target.value)} className={field} /></label>
        {!editing ? <>
          <label className="block text-sm font-semibold">Document filename<input maxLength={255} disabled={saving} value={form.fileName} onChange={(e) => change("fileName", e.target.value)} className={field} /></label>
          <label className="block text-sm font-semibold">Document storage path<input maxLength={500} disabled={saving} value={form.storagePath} onChange={(e) => change("storagePath", e.target.value)} className={field} /></label>
          <p className="text-xs text-gray-500">Use the filename and storage path of an already stored document.</p>
        </> : <div className="space-y-2 rounded-xl bg-gray-50 p-3 text-xs text-gray-600"><p className="break-words">Document: {form.fileName || "Not recorded"}</p><p className="break-words">Storage path: {form.storagePath || "Not recorded"}</p></div>}
        <button type="submit" disabled={saving || loading || Boolean(loadError) || Boolean(typeError)} className="w-full rounded-full bg-black px-5 py-3 text-sm font-semibold text-white disabled:opacity-50">{saving ? "Saving..." : editing ? "Save changes" : "Register collateral"}</button>
      </form>
    </div>
  </section>;
}
