import { useEffect, useRef, useState } from "react";
import { createSettlementOrder, getSettlementPayment, verifySettlementPayment } from "../api";

let checkoutScript;
function loadCheckout() {
  if (window.Razorpay) return Promise.resolve();
  if (!checkoutScript) {
    checkoutScript = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.async = true;
      const timeout = window.setTimeout(() => fail(), 20000);
      function fail() {
        window.clearTimeout(timeout);
        script.remove();
        reject(new Error("Unable to load Razorpay Checkout. Please retry."));
      }
      script.onload = () => {
        window.clearTimeout(timeout);
        if (window.Razorpay) resolve();
        else fail();
      };
      script.onerror = fail;
      document.head.appendChild(script);
    }).catch((error) => {
      checkoutScript = null;
      throw error;
    });
  }
  return checkoutScript;
}

const money = (value) => new Intl.NumberFormat("en-IN", {
  style: "currency", currency: "INR",
}).format(value);

export default function SettlementPayment({ token, quoteId, approved, username, onRefresh }) {
  const [payment, setPayment] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const mounted = useRef(false);
  const processing = useRef(false);
  const checkout = useRef(null);

  useEffect(() => {
    mounted.current = true;
    let current = true;
    getSettlementPayment(token, quoteId)
      .then((data) => { if (current) setPayment(data); })
      .catch((err) => { if (current) setError(err.message); })
      .finally(() => { if (current) setLoading(false); });
    return () => {
      current = false;
      mounted.current = false;
      checkout.current?.close();
    };
  }, [token, quoteId]);

  async function refresh() {
    if (processing.current) return;
    processing.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const data = await getSettlementPayment(token, quoteId);
      if (mounted.current) setPayment(data);
      if (data.paymentStatus === "SUCCESS" && mounted.current) await onRefresh?.();
    } catch (err) {
      if (mounted.current) setError(err.message);
    } finally {
      processing.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  async function pay() {
    if (processing.current) return;
    processing.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const order = await createSettlementOrder(token, quoteId);
      if (!mounted.current) return;
      setPayment(order);
      if (order.paymentStatus === "SUCCESS" || !order.payable) return;
      if (!order.keyId?.startsWith("rzp_test_") || !order.razorpayOrderId
          || !Number.isSafeInteger(order.amountPaise) || order.amountPaise < 100) {
        throw new Error("Invalid test checkout information from the backend.");
      }
      await loadCheckout();
      if (!mounted.current) return;
      await new Promise((resolve, reject) => {
        let verifying = false;
        let finished = false;
        const finish = (err) => {
          if (finished) return;
          finished = true;
          if (err) reject(err);
          else resolve();
        };
        const modal = new window.Razorpay({
          key: order.keyId,
          order_id: order.razorpayOrderId,
          amount: order.amountPaise,
          currency: order.currency,
          name: "Clear Close",
          description: `Settlement quote #${quoteId} (Test Mode)`,
          prefill: { name: username },
          theme: { color: "#24351c" },
          method: { card: true, netbanking: true, upi: true, wallet: false, emi: false, paylater: false },
          retry: { enabled: false },
          modal: {
            ondismiss: () => {
              if (verifying || finished) return;
              if (mounted.current) setNotice("Checkout dismissed. No successful payment has been confirmed. Refresh status before retrying.");
              finish();
            },
          },
          handler: async (response) => {
            if (verifying || finished) return;
            verifying = true;
            try {
              const verified = await verifySettlementPayment(token, response);
              if (mounted.current) setPayment(verified);
              if (verified.paymentStatus === "SUCCESS" && mounted.current) await onRefresh?.();
              finish();
            } catch (err) {
              finish(new Error(`${err.message} Refresh payment status before attempting another payment.`));
            }
          },
        });
        checkout.current = modal;
        modal.on("payment.failed", () => {
          finish(new Error("Razorpay test payment failed. Refresh status before retrying."));
          modal.close();
        });
        modal.open();
      });
    } catch (err) {
      if (mounted.current) setError(err.message);
    } finally {
      checkout.current = null;
      processing.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  const success = payment?.paymentStatus === "SUCCESS";
  return (
    <section className="mt-4 rounded-xl border border-gray-200 bg-white p-4 text-gray-900" aria-label="Settlement payment">
      <h4 className="font-semibold">{success ? "Payment Successful" : "Settlement Payment — Test Mode"}</h4>
      {loading && <p className="mt-2 text-sm">Checking payment eligibility...</p>}
      {payment && (
        <>
          <p className="mt-2 text-sm">Payment Status: <strong>{payment.paymentStatus}</strong></p>
          <p className="mt-2 text-sm">{payment.message}</p>
          {success && (
            <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
              <div><dt>Payment ID</dt><dd>{payment.paymentId}</dd></div>
              <div><dt>Razorpay Payment ID</dt><dd className="break-all">{payment.razorpayPaymentId}</dd></div>
              <div><dt>Amount Paid</dt><dd>{money(payment.amount)}</dd></div>
              <div><dt>Payment Date</dt><dd>{payment.paymentDate?.replace("T", " ") || "N/A"}</dd></div>
              <div><dt>Method</dt><dd>{payment.method || "Not mapped to the existing database methods"}</dd></div>
            </dl>
          )}
        </>
      )}
      {error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}
      {notice && <p role="status" className="mt-3 text-sm text-amber-900">{notice}</p>}
      <div className="mt-4 flex flex-wrap gap-3">
        {approved && payment?.payable && !success && (
          <button type="button" onClick={pay} disabled={busy || loading}
            className="rounded-xl bg-black px-5 py-3 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50">
            {busy ? "Processing payment..." : "Pay Settlement Amount"}
          </button>
        )}
        <button type="button" onClick={refresh} disabled={busy || loading}
          className="rounded-xl border border-gray-300 px-4 py-3 text-sm font-semibold disabled:opacity-50">
          {busy ? "Please wait..." : "Refresh Payment Status"}
        </button>
        {success && onRefresh && (
          <button type="button" onClick={onRefresh} disabled={busy || loading}
            className="rounded-xl border border-green-700 px-4 py-3 text-sm font-semibold text-green-800 disabled:opacity-50">
            Refresh Exit Status
          </button>
        )}
      </div>
      <p className="mt-3 text-xs text-gray-500">Use Razorpay test payment details only. After payment verification, refresh exit status to see the backend-confirmed loan closure and exit completion.</p>
    </section>
  );
}
