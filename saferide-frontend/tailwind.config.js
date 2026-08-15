/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        // SafeRide brand palette - see ide_selection / design doc.
        // Yellow is reserved for primary actions & brand identity only.
        primary: {
          DEFAULT: "#FFC107",
          foreground: "#0F172A",
        },
        dark: "#0F172A",
        background: "#F8FAFC",
        foreground: "#1E293B",
        muted: {
          DEFAULT: "#64748B",
          foreground: "#94A3B8",
        },
        success: "#22C55E",
        danger: "#EF4444",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
      borderRadius: {
        xl: "1rem",
        "2xl": "1.25rem",
      },
    },
  },
  plugins: [],
};
