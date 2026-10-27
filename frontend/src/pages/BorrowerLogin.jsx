import { useState } from "react";
import { loginBorrower } from "../api";

function BorrowerLogin({ onLogin, redirectTo = "#borrower-dashboard" }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setLoading(true);

    try {
      const data = await loginBorrower(username, password);

      if (
        !data.token ||
        data.accountType !== "BORROWER" ||
        data.role !== "BORROWER"
      ) {
        throw new Error("This account is not a borrower account.");
      }

      onLogin(data);
      window.location.hash = redirectTo;
    } catch (loginError) {
      setError(loginError.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="borrower-login-page">
      <section className="borrower-login-card">
        <a href="#home" className="dashboard-brand">
          <span className="dashboard-brand-mark" />
          Clear<span>Close</span>
        </a>

        <p className="dashboard-eyebrow">Borrower portal</p>
        <h1>Welcome back.</h1>
        <p className="dashboard-muted">
          Sign in to manage your existing loans and closure requests.
        </p>

        <form onSubmit={handleSubmit} className="borrower-login-form">
          <label htmlFor="borrower-username">
            Username
            <input
              id="borrower-username"
              type="text"
              placeholder="Enter your username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              required
            />
          </label>

          <label htmlFor="borrower-password">
            Password
            <input
              id="borrower-password"
              type="password"
              placeholder="Enter your password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </label>

          {error && <p className="login-error" role="alert">{error}</p>}

          <button
            type="submit"
            className="dashboard-primary-button"
            disabled={loading}
          >
            {loading ? "Signing in..." : "Sign in"}
          </button>
        </form>

        <p className="dashboard-small-text">
          Use a seeded borrower username and password.
        </p>
      </section>
    </main>
  );
}

export default BorrowerLogin;
