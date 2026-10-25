import { useEffect, useState } from "react";

const API_URL = (import.meta.env.VITE_API_URL || "http://localhost:8091").replace(/\/$/, "");

export default function SeniorOverdueNotice({ session }) {
  const [items, setItems] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const response = await fetch(
          "http://localhost:8091/api/grievances/senior/overdue-notifications",
          {
            headers: {
              Authorization: `Bearer ${session.token}`,
            },
          }
        );

        if (!response.ok) {
          throw new Error(`Unable to load notices (${response.status})`);
        }

        setItems(await response.json());
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }

    if (session?.token) load();
  }, [session?.token]);

  return (
    <section className="mx-auto mb-6 max-w-7xl rounded-xl border border-amber-300 bg-amber-50 p-4">
      <h2 className="font-bold text-amber-900">
        Senior grievance notifications
      </h2>

      {loading && (
        <p className="mt-2 text-sm text-gray-600">
          Loading notifications...
        </p>
      )}

      {error && (
        <p className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {!loading && !error && items.length === 0 && (
        <p className="mt-2 text-sm text-gray-600">
          No overdue grievances.
        </p>
      )}

      {items.map((item) => (
        <p key={item.grievanceId} className="mt-2 text-sm text-amber-900">
          Grievance #{item.grievanceId} has not been resolved after seven
          working days.
        </p>
      ))}
    </section>
  );
}