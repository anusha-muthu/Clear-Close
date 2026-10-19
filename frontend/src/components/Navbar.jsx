import { useState } from "react";

const navLinks = [
  { label: "Home", href: "#home" },
  { label: "Our Services", href: "#features" },
  { label: "How It Works", href: "#how-it-works" },
  { label: "About Us", href: "#about" },
];

function Navbar() {
  const [loginMenuOpen, setLoginMenuOpen] = useState(false);

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

        <div
          onMouseLeave={() => setLoginMenuOpen(false)}
          style={{ marginLeft: "auto", position: "relative", transform: "translateX(-48px)" }}
        >
          <button
            type="button"
            aria-haspopup="menu"
            aria-expanded={loginMenuOpen}
            onClick={() => setLoginMenuOpen((open) => !open)}
            style={{
              background: "#000",
              border: "none",
              borderRadius: "999px",
              color: "#fff",
              cursor: "pointer",
              fontSize: "14px",
              fontWeight: 700,
              padding: "14px 30px",
              whiteSpace: "nowrap",
            }}
          >
            Login 
          </button>

          {loginMenuOpen && (
            <div
              role="menu"
              aria-label="Choose login type"
              style={{
                background: "#fff",
                border: "1px solid #e3e9e2",
                borderRadius: "14px",
                boxShadow: "0 12px 32px rgba(16, 32, 24, 0.14)",
                minWidth: "190px",
                padding: "8px",
                position: "absolute",
                left: "50%",
                top: "calc(100% + 10px)",
                transform: "translateX(-50%)",
              }}
            >
              <a
                href="#borrower-login"
                role="menuitem"
                onClick={() => setLoginMenuOpen(false)}
                style={menuItemStyle}
              >
                Borrower
              </a>
              <a
                href="#officer-login"
                role="menuitem"
                onClick={() => setLoginMenuOpen(false)}
                style={menuItemStyle}
              >
                Officer
              </a>
            </div>
          )}
        </div>
      </nav>
    </header>
  );
}

const menuItemStyle = {
  borderRadius: "9px",
  color: "#1b251e",
  display: "block",
  fontSize: "14px",
  fontWeight: 600,
  padding: "12px 14px",
  textDecoration: "none",
};

export default Navbar;
