import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { motion } from "./services/MotionService";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// Tactile press micro-interaction: every button responds to the finger.
motion.installGlobalPressFeedback();

// Instant repeat loads (production only; dev server must stay untouched).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {
      /* offline shell is an enhancement, never a hard dependency */
    });
  });
}
