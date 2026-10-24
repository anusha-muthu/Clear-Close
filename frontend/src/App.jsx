import { useEffect, useState } from "react";

import Navbar from "./components/Navbar";
import Hero from "./components/Hero";
import Features from "./components/Features";
import HowItWorks from "./components/HowitWorks";
import Footer from "./components/Footer";

import BorrowerLogin from "./pages/BorrowerLogin";
import BorrowerDashboard from "./pages/BorrowerDashboard";
import OfficerLogin from "./pages/OfficerLogin";
import LoanClosureDashboard from "./pages/LoanClosureDashboard";

import GrievanceDashboard from "./pages/GrievanceDashboard";
import GrievanceOfficerDashboard from "./pages/GrievanceOfficerDashboard";
import CollateralReleaseDashboard from "./pages/CollateralReleaseDashboard";

import NocDashboard from "./pages/NocDashboard";

import { OFFICER_PORTALS } from "./officerApi";

const officerLoginRoutes = {
  closure: "#closure-officer-login",
  grievance: "#grievance-officer-login",
  collateral: "#collateral-officer-login",
};

function Redirect({ to }) {
  useEffect(() => {
    window.location.replace(to);
  }, [to]);

  return null;
}

function App() {
  const [route, setRoute] = useState(window.location.hash);

  const [session, setSession] = useState(() => {
    try {
      const saved = sessionStorage.getItem("clearclose_session");
      return saved ? JSON.parse(saved) : null;
    } catch {
      sessionStorage.removeItem("clearclose_session");
      return null;
    }
  });

  useEffect(() => {
    const updateRoute = () => setRoute(window.location.hash);

    window.addEventListener("hashchange", updateRoute);

    return () => {
      window.removeEventListener("hashchange", updateRoute);
    };
  }, []);

  const isBorrower = Boolean(
    session?.token &&
      session.accountType === "BORROWER" &&
      session.role === "BORROWER"
  );

  const canAccessPortal = (portal) =>
    Boolean(
      session?.token &&
        session.accountType === "OFFICER" &&
        OFFICER_PORTALS[portal]?.roles.includes(session.role)
    );

  function handleLogin(data) {
    const userSession = {
      token: data.token,
      username: data.username,
      userId: data.userId,
      accountType: data.accountType,
      role: data.role,
      portal: data.portal ?? null,
    };

    sessionStorage.setItem(
      "clearclose_session",
      JSON.stringify(userSession)
    );

    setSession(userSession);
  }

  function handleLogout() {
    sessionStorage.removeItem("clearclose_session");
    setSession(null);
    window.location.hash = "#home";
  }

  // Borrower login.
  if (route === "#borrower-login") {
    return isBorrower ? (
      <Redirect to="#borrower-dashboard" />
    ) : (
      <BorrowerLogin onLogin={handleLogin} />
    );
  }

  // Borrower dashboard.
  if (route === "#borrower-dashboard") {
    return isBorrower ? (
      <BorrowerDashboard
        session={session}
        onLogout={handleLogout}
      />
    ) : (
      <Redirect to="#borrower-login" />
    );
  }
     // NOC certificates: borrower access and authorized officer access.
  if (route === "#noc-dashboard") {
    if (!session?.token) {
      return <Redirect to="#borrower-login" />;
    }

    const authorizedOfficer =
      session.accountType === "OFFICER" &&
      ["LOAN_CLOSURE_OFFICER", "COLLATERAL_OFFICER", "ADMIN"].includes(
        session.role
      );

    if (!isBorrower && !authorizedOfficer) {
      return (
        <main className="borrower-login-page">
          <section className="borrower-login-card">
            <h1>Access denied</h1>
            <p>Your account cannot access the NOC service.</p>

            <a href="#officer-portals">Back to officer services</a>

            <button type="button" onClick={handleLogout}>
              Sign out
            </button>
          </section>
        </main>
      );
    }

    return (
      <NocDashboard
        session={session}
        onLogout={handleLogout}
      />
    );
  }
  // Officer service selection.
  if (
    route === "#officer-login" ||
    route === "#officer-portals"
  ) {
    return (
      <main className="borrower-login-page">
        <section className="borrower-login-card">
          <a href="#home" className="dashboard-brand">
            ClearClose
          </a>

          <p className="dashboard-eyebrow">
            Bank Employee Portal
          </p>

          <h1>Choose your service</h1>

          <div
            style={{
              display: "grid",
              gap: "16px",
              margin: "24px 0",
            }}
          >
            <a
              href={officerLoginRoutes.closure}
              className="dashboard-primary-button full-width"
            >
              Cooling-off and Loan Closure
            </a>

            <a
              href={officerLoginRoutes.grievance}
              className="dashboard-primary-button full-width"
            >
              Grievance Management
            </a>

            <a
              href={officerLoginRoutes.collateral}
              className="dashboard-primary-button full-width"
            >
              Collateral Release
            </a>
          </div>

          <a href="#home">← Back to Homepage</a>
        </section>
      </main>
    );
  }

  // Separate officer login pages.
  const loginEntry = Object.entries(officerLoginRoutes).find(
    ([, loginRoute]) => loginRoute === route
  );

  if (loginEntry) {
    const [portal] = loginEntry;

    if (canAccessPortal(portal)) {
      return (
        <Redirect to={OFFICER_PORTALS[portal].dashboard} />
      );
    }

    return (
      <OfficerLogin
        key={portal}
        portal={portal}
        onLogin={handleLogin}
      />
    );
  }

  // Preserve existing bookmarks while keeping closure and grievance dashboards separate.
  if (route === "#officer-dashboard" || route === "#closure-officer-dashboard") {
    return <Redirect to={OFFICER_PORTALS.closure.dashboard} />;
  }

  // Officer dashboard routes.
  const dashboardEntry = Object.entries(OFFICER_PORTALS).find(
    ([, config]) => config.dashboard === route
  );

  if (dashboardEntry) {
    const [portal] = dashboardEntry;

    if (!session?.token) {
      return <Redirect to={officerLoginRoutes[portal]} />;
    }

    if (!canAccessPortal(portal)) {
      return (
        <main className="borrower-login-page">
          <section className="borrower-login-card">
            <h1>Access denied</h1>
            <p>Your account cannot access this service.</p>

            <a href="#officer-portals">
              Choose another service
            </a>

            <button type="button" onClick={handleLogout}>
              Sign out
            </button>
          </section>
        </main>
      );
    }

    // The closure portal owns the existing cooling-off review workflow.
    if (portal === "closure") {
      return (
        <LoanClosureDashboard
          session={session}
          onLogout={handleLogout}
        />
      );
    }

    if (portal === "grievance") {
      return (
        <GrievanceOfficerDashboard
          session={session}
          onLogout={handleLogout}
        />
      );
    }

    return (
      <>
        <nav className="flex flex-wrap items-center gap-4 border-b p-4">
          <a href="#officer-portals">← Officer services</a>
          <a href="#noc-dashboard">NOC Certificates</a>

          <span>{session.username}</span>

          <button type="button" onClick={handleLogout}>
            Sign out
          </button>
        </nav>

        <CollateralReleaseDashboard session={session} />
      </>
    );
  }

  // Borrower grievance and collateral pages.
  if (
    route === "#grievance-dashboard" ||
    route === "#collateral-dashboard"
  ) {
    if (!isBorrower) {
      return <Redirect to="#borrower-login" />;
    }

    return (
      <>
        <nav className="border-b p-4">
          <a href="#borrower-dashboard">
            ← Borrower dashboard
          </a>
        </nav>

        {route === "#grievance-dashboard" ? (
          <GrievanceDashboard session={session} />
        ) : (
          <CollateralReleaseDashboard session={session} />
        )}
      </>
    );
  }

  // Homepage.
  return (
    <div className="min-h-screen bg-white text-[#102018]">
      <Navbar />

      <main>
        <Hero />
        <Features />
        <HowItWorks />
      </main>

      <Footer />
    </div>
  );
}

export default App;
