import { useState } from "react";

const journeyOptions = [
  {
    id: "cooling-off",
    label: "Cooling-off exit",
    title: "Exit a new loan early",
    description:
      "Check whether your recently disbursed loan is eligible for a cooling-off exit.",
    action: "Check eligibility",
    href: "#borrower-dashboard?section=cooling-off",
  },
  {
    id: "closure",
    label: "Close my loan",
    title: "Plan your loan closure",
    description:
      "Review your settlement steps and start a normal or early loan closure request.",
    action: "Start closure",
    href: "#borrower-dashboard?section=preclosure",
  },
  {
    id: "noc",
    label: "Track my NOC",
    title: "Find your closure documents",
    description:
      "See your closure status and download your NOC when it is ready.",
    action: "Track NOC",
    href: "#noc-dashboard",
  },
];

function Hero() {
  const [selectedJourney, setSelectedJourney] = useState("cooling-off");
  const activeJourney = journeyOptions.find(
    (journey) => journey.id === selectedJourney,
  );

  return (
    <section
      id="home"
      className="relative overflow-hidden bg-white px-6 py-14 lg:px-16 lg:py-16"
    >
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-[28rem] bg-gradient-to-t from-lime-500/75 via-lime-300/50 to-transparent" />

      <div className="relative mx-auto grid max-w-7xl items-center gap-16 lg:grid-cols-2">
        <div>
          <p className="mb-6 text-xs font-bold uppercase tracking-[0.25em] text-lime-700">
            Smarter Loan Closure
          </p>

          <h1
            className="text-5xl leading-[1.12] tracking-tight text-[#102018] sm:text-6xl lg:text-7xl"
            style={{ fontFamily: "'Playfair Display', serif" }}
          >
            A Completely
            <br />
            Hassle-Free
            <br />
            Loan Exit.
          </h1>

          <p className="mt-7 max-w-lg text-base leading-8 text-[#102018]">
            Manage your complete loan closure journey in one place. Check
            cooling-off eligibility, review settlement amounts, track approvals,
            and access your NOC without unnecessary follow-ups.
          </p>

          <div className="mt-9 flex flex-wrap gap-4">
            <a
              href="#features"
              className="rounded-full bg-black px-7 py-3.5 text-sm font-semibold text-white transition hover:bg-gray-800"
            >
              Get Started &rarr;
            </a>
            <a
              href="#how-it-works"
              className="rounded-full border border-black px-7 py-3.5 text-sm font-semibold transition hover:bg-black hover:text-white"
            >
              How It Works
            </a>
          </div>

          <div className="mt-9 flex flex-wrap gap-6 text-xs font-medium">
            <span>Simple Process</span>
            <span>Transparent Tracking</span>
            <span>Secure Experience</span>
          </div>
        </div>

        <div className="hero-summary-card mx-auto w-full rounded-3xl border border-[#dfe5df] bg-white p-7 shadow-[0_24px_60px_rgba(16,32,24,0.12)] ring-1 ring-black/[0.02] sm:p-8">
          <p className="text-center text-xs font-bold uppercase tracking-widest">
            Find the right next step
          </p>

          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "10px",
              marginTop: "24px",
            }}
          >
            {journeyOptions.map((journey) => {
              const isSelected = journey.id === selectedJourney;

              return (
                <button
                  key={journey.id}
                  type="button"
                  onClick={() => setSelectedJourney(journey.id)}
                  aria-pressed={isSelected}
                  style={{
                    alignItems: "center",
                    background: isSelected ? "#f3fce7" : "#ffffff",
                    border: isSelected
                      ? "1.5px solid #65a30d"
                      : "1px solid #dfe5df",
                    borderRadius: "12px",
                    color: isSelected ? "#102018" : "#536057",
                    cursor: "pointer",
                    display: "flex",
                    fontFamily: "inherit",
                    fontSize: "14px",
                    fontWeight: isSelected ? 700 : 600,
                    justifyContent: "space-between",
                    minHeight: "54px",
                    padding: "0 16px",
                    textAlign: "left",
                    transition: "border-color 180ms ease, background-color 180ms ease",
                    width: "100%",
                  }}
                >
                  {journey.label}
                  <span
                    style={{
                      alignItems: "center",
                      background: isSelected ? "#65a30d" : "#ffffff",
                      border: isSelected
                        ? "1px solid #65a30d"
                        : "1px solid #cbd5cb",
                      borderRadius: "999px",
                      display: "flex",
                      flex: "0 0 auto",
                      height: "18px",
                      justifyContent: "center",
                      width: "18px",
                    }}
                  >
                    {isSelected && (
                      <span
                        style={{
                          background: "#ffffff",
                          borderRadius: "999px",
                          height: "6px",
                          width: "6px",
                        }}
                      />
                    )}
                  </span>
                </button>
              );
            })}
          </div>

          <div
            style={{
              borderTop: "1px dashed #d1d9d1",
              margin: "22px 0",
            }}
          />

          <h2
            className="text-center text-2xl font-semibold tracking-tight text-[#102018]"
            style={{ fontFamily: "'Playfair Display', serif" }}
          >
            {activeJourney.title}
          </h2>

          <p className="mt-3 text-center text-sm leading-6 text-gray-600">
            {activeJourney.description}
          </p>

          <a
            href={activeJourney.href}
            className="mx-auto mt-5 flex w-fit rounded-full bg-black px-8 py-3 text-sm font-semibold text-white hover:bg-gray-800"
          >
            {activeJourney.action} &rarr;
          </a>

          <p className="mt-4 text-center text-xs text-gray-400">
            Select an option to see your next step
          </p>
        </div>
      </div>
    </section>
  );
}

export default Hero;
