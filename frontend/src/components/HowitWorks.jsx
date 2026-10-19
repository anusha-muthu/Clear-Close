
const steps = [
  {
    number: "STEP 01",
    title: "Login & Select Loan",
    description:
      "Sign in and select the existing loan you want to manage.",
  },
  {
    number: "STEP 02",
    title: "Choose Your Service",
    description:
      "Choose cooling-off exit, loan closure, or another available service.",
  },
  {
    number: "STEP 03",
    title: "Complete Required Actions",
    description:
      "Review repayment details, make a test payment, or submit required information.",
  },
  {
    number: "STEP 04",
    title: "Track Your Progress",
    description:
      "Track officer approvals, closure status, NOC, and collateral release.",
  },
];

function HowItWorks() {
  return (
    <section
      id="how-it-works"
      className="mx-auto max-w-7xl px-6 py-24 lg:px-10"
    >
      <div className="mb-14 text-center">
        <p className="text-xs font-bold uppercase tracking-widest text-lime-700">
          Simple & Transparent
        </p>

        <h2
          className="mt-5 text-5xl"
          style={{ fontFamily: "'Playfair Display', serif" }}
        >
          How It Works?
        </h2>

        <p className="mt-5 text-sm text-gray-500">
          Your loan exit journey in four simple steps.
        </p>
      </div>

      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {steps.map((step) => (
          <div
            key={step.number}
            className="min-h-60 rounded-2xl border border-[#dfe5df] bg-white p-7 shadow-[0_8px_24px_rgba(16,32,24,0.05)] transition duration-300 hover:-translate-y-1 hover:border-lime-500 hover:shadow-[0_16px_32px_rgba(16,32,24,0.1)]"
          >
            <span className="text-xs font-bold tracking-widest text-lime-700">
              {step.number}
            </span>

            <h3 className="mt-10 text-xl font-semibold">
              {step.title}
            </h3>

            <p className="mt-3 text-sm leading-7 text-gray-500">
              {step.description}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

export default HowItWorks;
