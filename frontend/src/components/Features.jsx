
const features = [
  {
    number: "01",
    title: "Cooling-Off Loan Exit",
    description:
      "Check eligibility and exit a recently disbursed loan within the allowed cooling-off period.",
    color: "bg-sky-200",
    href: "#borrower-dashboard?section=cooling-off",
  },
  {
    number: "02",
    title: "Loan Closure",
    description:
      "Request normal or early closure, review your settlement amount, and track approval.",
    color: "bg-purple-200",
    href: "#borrower-dashboard?section=preclosure",
  },
  {
  number: "03",
  title: "Grievance Management",
  description:
    "Raise complaints, track support tickets, and receive updates from bank officers.",
  color: "bg-lime-200",
  href: "#grievance-dashboard",
},
  {
    number: "04",
    title: "NOC Generation",
    description:
      "Download your No Objection Certificate after your loan closure is approved.",
    color: "bg-orange-100",
    href: "#noc-dashboard",
  },
  {
  number: "05",
  title: "Collateral Release",
  description:
    "Track the release of pledged assets and vehicle hypothecation after loan closure.",
  color: "bg-green-200",
  href: "#collateral-dashboard",
},
  
];

function Features() {
  return (
    <section
      id="features"
      className="mx-auto max-w-7xl px-6 py-24 lg:px-10"
    >
      <div className="mb-14 text-center">
        <p className="text-xs font-bold uppercase tracking-[0.25em] text-lime-700">
          Why Choose Clear Close?
        </p>

        <h2
          className="mt-5 text-4xl leading-tight sm:text-5xl"
          style={{ fontFamily: "'Playfair Display', serif" }}
        >
          Everything You Need
          <br />
          For a Smooth Loan Exit.
        </h2>

        <p className="mt-5 text-sm text-gray-500">
          One platform for your complete loan closure journey.
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
        {features.map((feature) => {
          const Card = feature.href ? "a" : "div";

          return (
            <Card
              key={feature.number}
              {...(feature.href ? { href: feature.href } : {})}
              className={`${feature.color} flex min-h-64 flex-col justify-between rounded-2xl border border-black/[0.08] p-8 text-inherit no-underline shadow-[0_10px_28px_rgba(16,32,24,0.08)] transition duration-300 hover:-translate-y-1 hover:shadow-[0_18px_36px_rgba(16,32,24,0.14)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-lime-700`}
            >
              <span className="text-sm font-bold">{feature.number}</span>

              <div>
                <h3 className="mb-3 text-2xl font-semibold tracking-tight">
                  {feature.title}
                </h3>
                <p className="text-sm leading-7 text-gray-700">
                  {feature.description}
                </p>
              </div>

              <span aria-hidden="true" className="self-end text-2xl">↗</span>
            </Card>
          );
        })}
      </div>
    </section>
  );
}

export default Features;
