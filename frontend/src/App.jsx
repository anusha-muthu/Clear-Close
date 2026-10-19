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

function App() {
  const [route, setRoute] = useState(window.location.hash);
  const [session, setSession] = useState(() => {
    try {
      const savedSession = sessionStorage.getItem("clearclose_session");
      return savedSession ? JSON.parse(savedSession) : null;
    } catch {
      sessionStorage.removeItem("clearclose_session");
      return null;
    }
  });

  useEffect(() => {
    const updateRoute = () => setRoute(window.location.hash);

    window.addEventListener("hashchange", updateRoute);
    return () => window.removeEventListener("hashchange", updateRoute);
  }, []);

  const isBorrower =
    session?.token &&
    session.accountType === "BORROWER" &&
    session.role === "BORROWER";

  const isOfficer =
    session?.token &&
    session.accountType === "OFFICER" &&
    session.role === "LOAN_CLOSURE_OFFICER";

  const handleLogin = (data) => {
    const userSession = {
      token: data.token,
      username: data.username,
      userId: data.userId,
      accountType: data.accountType,
      role: data.role,
    };

    sessionStorage.setItem(
      "clearclose_session",
      JSON.stringify(userSession),
    );
    setSession(userSession);
  };

  const handleLogout = () => {
    sessionStorage.removeItem("clearclose_session");
    setSession(null);
    window.location.hash = "#home";
  };

  if (route === "#borrower-login") {
    if (isBorrower) {
      window.location.hash = "#borrower-dashboard";
      return null;
    }

    return <BorrowerLogin onLogin={handleLogin} />;
  }

  if (route === "#borrower-dashboard") {
    if (!isBorrower) {
      window.location.hash = "#borrower-login";
      return null;
    }

    return <BorrowerDashboard session={session} onLogout={handleLogout} />;
  }

  if (route === "#officer-login") {
    if (isOfficer) {
      window.location.hash = "#officer-dashboard";
      return null;
    }

    return <OfficerLogin onLogin={handleLogin} />;
  }

  if (route === "#officer-dashboard") {
    if (!isOfficer) {
      window.location.hash = "#officer-login";
      return null;
    }

    return <OfficerDashboard session={session} onLogout={handleLogout} />;
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
