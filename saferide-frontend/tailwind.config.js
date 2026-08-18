/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        // SafeRide brand palette - see ide_selection / design doc.
        // Yellow is reserved for primary actions & brand identity only.
        // Every one of these resolves through a CSS custom property (see index.css's :root/.dark)
        // rather than a literal hex value, so toggling the "dark" class on <html> re-themes every
        // existing bg-primary/text-dark/bg-background/... usage across the app automatically -
        // see contexts/ThemeContext.tsx.
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        dark: "hsl(var(--dark))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        success: "hsl(var(--success))",
        danger: "hsl(var(--danger))",
        // New tokens - the shared surface every card/panel/dialog uses (bg-white/border-slate-200
        // classes were additionally given dark:bg-card/dark:border-card-border variants).
        card: {
          DEFAULT: "hsl(var(--card))",
          border: "hsl(var(--card-border))",
        },
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
