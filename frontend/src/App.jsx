import { useEffect, useState } from "react";

import Navbar from "./components/Navbar";
import Hero from "./components/Hero";
import Features from "./components/Features";
import HowItWorks from "./components/HowitWorks";
import Footer from "./components/Footer";

import BorrowerLogin from "./pages/BorrowerLogin";
import BorrowerDashboard from "./pages/BorrowerDashboard";
import OfficerLogin from "./pages/OfficerLogin";
import OfficerDashboard from "./pages/OfficerDashboard";
import GrievanceDashboard from "./pages/GrievanceDashboard";
import CollateralReleaseDashboard from "./pages/CollateralReleaseDashboard";

const PORTALS = {
  closure: {
    label: "Cooling-off and Loan Closure",
    login: "#closure-officer-login",
    dashboard: "#officer-dashboard",
    roles: ["LOAN_CLOSURE_OFFICER", "ADMIN"],
  },
  grievance: {
    label: "Grievance Management",
    login: "#grievance-officer-login",
    dashboard: "#grievance-officer-dashboard",
    roles: [
      "LOAN_CLOSURE_OFFICER",
      "GRIEVANCE_OFFICER",
      "COLLATERAL_OFFICER",
      "ADMIN",
    ],
  },
  collateral: {
    label: "Collateral Release",
    login: "#collateral-officer-login",
    dashboard: "#collateral-officer-dashboard",
    roles: ["COLLATERAL_OFFICER", "ADMIN"],
  },
};

function Redirect({ to }) {
  useEffect(() => {
    window.location.replace(to);
  }, [to]);

  return null;
}

function OfficerPortals() {
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
          {Object.entries(PORTALS).map(([key, portal]) => (
            <a
              key={key}
              href={portal.login}
              className="dashboard-primary-button full-width"
            >
              {portal.label}
            </a>
          ))}
        </div>

        <a href="#home">← Back to Homepage</a>
      </section>
    </main>
  );
}

function OfficerAccessDenied({ onLogout }) {
  return (
    <main className="borrower-login-page">
      <section className="borrower-login-card">
        <h1>Access denied</h1>
        <p>Your account cannot access this officer dashboard.</p>

        <a href="#officer-login">Choose another service</a>

        <button
          type="button"
          onClick={onLogout}
          className="dashboard-primary-button full-width"
        >
          Sign out
        </button>
      </section>
    </main>
  );
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
        PORTALS[portal].roles.includes(session.role)
    );

  const handleLogin = (data) => {
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
  };

  const handleLogout = () => {
    sessionStorage.removeItem("clearclose_session");
    setSession(null);
    window.location.hash = "#home";
  };

  // Borrower login and main dashboard.
  if (route === "#borrower-login") {
    return isBorrower ? (
      <Redirect to="#borrower-dashboard" />
    ) : (
      <BorrowerLogin onLogin={handleLogin} />
    );
  }

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

  // The existing Officer button now opens the service selector.
  if (
    route === "#officer-login" ||
    route === "#officer-portals"
  ) {
    return <OfficerPortals />;
  }

  // Separate officer login pages.
  const loginEntry = Object.entries(PORTALS).find(
    ([, portal]) => portal.login === route
  );

  if (loginEntry) {
    const [portalKey, portal] = loginEntry;

    if (canAccessPortal(portalKey)) {
      return <Redirect to={portal.dashboard} />;
    }

    return (
      <OfficerLogin
        key={portalKey}
        portal={portalKey}
        onLogin={handleLogin}
      />
    );
  }

  // Officer dashboards.
  const dashboardEntry = Object.entries(PORTALS).find(
    ([, portal]) => portal.dashboard === route
  );

  if (dashboardEntry) {
    const [portalKey, portal] = dashboardEntry;

    if (!session?.token) {
      return <Redirect to={portal.login} />;
    }

    if (!canAccessPortal(portalKey)) {
      return <OfficerAccessDenied onLogout={handleLogout} />;
    }

    if (portalKey === "closure") {
      return (
        <OfficerDashboard
          session={session}
          onLogout={handleLogout}
        />
      );
    }

    return (
      <>
        <nav className="border-b p-4">
          <a href="#officer-login">← Officer services</a>

          <span style={{ margin: "0 16px" }}>
            {session.username}
          </span>

          <button type="button" onClick={handleLogout}>
            Sign out
          </button>
        </nav>

        {portalKey === "grievance" ? (
          <GrievanceDashboard session={session} />
        ) : (
          <CollateralReleaseDashboard session={session} />
        )}
      </>
    );
  }

  // Existing borrower grievance and collateral pages.
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
          <a href="#borrower-dashboard">← Borrower dashboard</a>
        </nav>

        {route === "#grievance-dashboard" ? (
          <GrievanceDashboard session={session} />
        ) : (
          <CollateralReleaseDashboard session={session} />
        )}
      </>
    );
  }

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