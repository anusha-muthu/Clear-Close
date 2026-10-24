const symbols = {
  noc: <>
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h6M14 2l6 6M14 2v6h6v4M8 12h4M8 16h2" />
    <circle cx="17" cy="17" r="4" />
    <path d="m15.5 17 1 1 2-2M15 21v2l2-1 2 1v-2" />
  </>,
  collateral: <>
    <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" />
    <circle cx="10" cy="11" r="2" />
    <path d="m11.5 12.5 4 4M14 15l1.5-1.5" />
  </>,
  "cooling-off": <>
    <path d="M3 10a9 9 0 1 1 1 7M3 4v6h6" />
    <path d="M12 7v5l3 2" />
  </>,
  preclosure: <>
    <path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-6-6Z" />
    <path d="M14 3v6h6m-12 6 3 3 5-5" />
  </>,
  overview: <>
    <rect x="3" y="3" width="7" height="7" rx="1.5" />
    <rect x="14" y="3" width="7" height="7" rx="1.5" />
    <rect x="3" y="14" width="7" height="7" rx="1.5" />
    <rect x="14" y="14" width="7" height="7" rx="1.5" />
  </>,
  "my-loans": <>
    <path d="m3 8 9-5 9 5v2H3V8Zm0 13h18M5 10v7m5-7v7m4-7v7m5-7v7M3 18h18v3H3v-3Z" />
  </>,
};

export default function BorrowerNavIcon({ name }) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"
    aria-hidden="true" focusable="false" className="shrink-0">
    {symbols[name]}
  </svg>;
}
