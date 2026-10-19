const navLinks = [
  { label: "Home", href: "#home" },
  { label: "Our Services", href: "#features" },
  { label: "How It Works", href: "#how-it-works" },
  { label: "About Us", href: "#about" },
];

function Navbar() {
  return (
    <header
      style={{
        background: "#fff",
        position: "sticky",
        top: 0,
        zIndex: 10,
      }}
    >
      <nav
        style={{
          alignItems: "center",
          display: "flex",
          height: "86px",
          margin: "0 auto",
          maxWidth: "1440px",
          padding: "0 40px",
          width: "100%",
        }}
      >
        <a
          href="#home"
          aria-label="ClearClose home"
          style={{
            alignItems: "center",
            color: "#102018",
            display: "flex",
            fontSize: "27px",
            fontWeight: 800,
            gap: "12px",
            letterSpacing: "-0.07em",
            textDecoration: "none",
            whiteSpace: "nowrap",
          }}
        >
          <span
            aria-hidden="true"
            style={{
              border: "3px solid #65a30d",
              display: "inline-block",
              height: "14px",
              transform: "rotate(45deg)",
              width: "14px",
            }}
          />
          <span>
            Clear<span style={{ color: "#65a30d" }}>Close</span>
          </span>
        </a>

        <div
          style={{
            alignItems: "center",
            display: "flex",
            gap: "42px",
            justifyContent: "center",
            margin: "0 auto",
          }}
        >
          {navLinks.map((link) => (
            <a
              key={link.label}
              href={link.href}
              style={{
                color: "#1b251e",
                fontSize: "14px",
                fontWeight: 600,
                textDecoration: "none",
                whiteSpace: "nowrap",
              }}
            >
              {link.label}
            </a>
          ))}
        </div>

        <div style={{ alignItems: "center", display: "flex", gap: "22px" }}>
          <a
            href="#login"
            style={{
              color: "#1b251e",
              fontSize: "14px",
              fontWeight: 600,
              textDecoration: "none",
            }}
          >
            Login
          </a>
          <a
            href="#features"
            style={{
              background: "#000",
              borderRadius: "999px",
              color: "#fff",
              fontSize: "14px",
              fontWeight: 700,
              padding: "14px 24px",
              textDecoration: "none",
              whiteSpace: "nowrap",
            }}
          >
            Get Started
          </a>
        </div>
      </nav>
    </header>
  );
}

export default Navbar;
