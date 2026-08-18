import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "leaflet/dist/leaflet.css";
import "./index.css";
import App from "./App.tsx";

// Applied synchronously, before the first paint - if this waited for ThemeProvider's own effect
// (which only runs after React's first commit), a dark-mode user would see a flash of the light
// theme on every load. Kept in sync with ThemeContext's own storage key/fallback logic.
const storedTheme = localStorage.getItem("saferide.theme");
const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)").matches;
if (storedTheme === "dark" || (storedTheme !== "light" && prefersDark)) {
  document.documentElement.classList.add("dark");
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
