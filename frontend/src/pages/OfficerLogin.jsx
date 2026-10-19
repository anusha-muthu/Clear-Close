
import { useState } from "react";
import { loginOfficer } from "../officerApi";

function OfficerLogin({ onLogin }) {

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();

    setLoading(true);
    setError("");

    try {
      // Call Auth Service through API Gateway
      const data = await loginOfficer(username, password);

      // Store authenticated session
      onLogin(data);

      // Redirect to officer dashboard
      window.location.hash = "#officer-dashboard";

    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="borrower-login-page">
      <section className="borrower-login-card officer-login-card">

        {/* Logo */}
        <a href="#home" className="dashboard-brand">
          <span className="dashboard-brand-mark" />
          Clear<span>Close</span>
        </a>

        <div className="officer-login-intro">
          <p className="dashboard-eyebrow">
            Bank Employee Portal
          </p>

          <h1>
            Officer Login
          </h1>

          <p className="dashboard-muted">
            Sign in to review and manage cooling-off
            exit requests.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="borrower-login-form">

          {/* Username */}
          <div>
            <label
              htmlFor="officer-username"
            >
              Username
            </label>

            <input
              id="officer-username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              placeholder="Enter officer username"
              required
            />
          </div>

          {/* Password */}
          <div>
            <label
              htmlFor="officer-password"
            >
              Password
            </label>

            <input
              id="officer-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              placeholder="Enter password"
              required
            />
          </div>

          {/* Error */}
          {error && (
            <p
              role="alert"
              className="login-error"
            >
              {error}
            </p>
          )}

          {/* Login Button */}
          <button
            type="submit"
            disabled={loading}
            className="dashboard-primary-button full-width"
          >
            {loading ? "Logging in..." : "Login as Officer →"}
          </button>

        </form>

        <div className="officer-login-links">
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
