import { useState } from "react";
import {
  loginOfficer,
  getOfficerDashboard,
  OFFICER_PORTALS,
} from "../officerApi";

function OfficerLogin({ onLogin, portal = "closure" }) {
  const config = OFFICER_PORTALS[portal];

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();

    if (loading) return;

    setLoading(true);
    setError("");

    try {
      const data = await loginOfficer(
        username,
        password,
        portal
      );

      const dashboard = getOfficerDashboard(
        portal,
        data.role
      );

      await onLogin({ ...data, portal });

      window.location.hash = dashboard;
    } catch (err) {
      setError(err.message || "Unable to sign in.");
    } finally {
      setLoading(false);
    }
  }

  if (!config) {
    return (
      <main className="borrower-login-page">
        <section className="borrower-login-card">
          <h1>Unknown officer portal</h1>
          <a href="#officer-login">Choose a service</a>
        </section>
      </main>
    );
  }

  return (
    <main className="borrower-login-page">
      <section className="borrower-login-card officer-login-card">
        <a href="#home" className="dashboard-brand">
          <span className="dashboard-brand-mark" />
          Clear<span>Close</span>
        </a>

        <div className="officer-login-intro">
          <p className="dashboard-eyebrow">
            Bank Employee Portal
          </p>

          <h1>{config.title}</h1>

          <p className="dashboard-muted">
            {config.description}
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="borrower-login-form"
        >
          <div>
            <label htmlFor="officer-username">
              Username
            </label>

            <input
              id="officer-username"
              type="text"
              value={username}
              onChange={(event) =>
                setUsername(event.target.value)
              }
              autoComplete="username"
              placeholder="Enter officer username"
              disabled={loading}
              required
            />
          </div>

          <div>
            <label htmlFor="officer-password">
              Password
            </label>

            <input
              id="officer-password"
              type="password"
              value={password}
              onChange={(event) =>
                setPassword(event.target.value)
              }
              autoComplete="current-password"
              placeholder="Enter password"
              disabled={loading}
              required
            />
          </div>

          {error && (
            <p role="alert" className="login-error">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="dashboard-primary-button full-width"
          >
            {loading ? "Logging in..." : "Sign in →"}
          </button>
        </form>

        <div className="officer-login-links">
          <a
            href="#officer-login"
            className="officer-secondary-link"
          >
            ← Choose another officer service
          </a>

          <a
            href="#borrower-login"
            className="officer-secondary-link"
          >
            Borrower Login
          </a>

          <a
            href="#home"
            className="officer-back-link"
          >
            ← Back to Homepage
          </a>
        </div>
      </section>
    </main>
  );
}

export default OfficerLogin;