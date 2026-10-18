
function Footer() {
  return (
    <footer
      id="about"
      className="bg-[#101a13] px-6 py-16 text-white lg:px-16"
    >
      <div className="mx-auto flex max-w-7xl flex-col justify-between gap-10 md:flex-row">
        <div>
          <h2 className="text-3xl font-bold">
            Clear<span className="text-lime-400">Close.</span>
          </h2>

          <p className="mt-4 max-w-xs text-sm leading-7 text-gray-400">
            Making loan exits simpler, clearer,
            and more transparent.
          </p>
        </div>

        <div className="flex flex-wrap gap-8 text-sm">
          <a href="#home" className="hover:text-lime-400">
            Home
          </a>
          <a href="#features" className="hover:text-lime-400">
            Services
          </a>
          <a href="#how-it-works" className="hover:text-lime-400">
            How It Works
          </a>
        </div>
      </div>

      <div className="mx-auto mt-14 max-w-7xl border-t border-gray-700 pt-7 text-xs text-gray-400">
        © 2026 Clear Close • Project Demonstration
      </div>
    </footer>
  );
}

export default Footer;
