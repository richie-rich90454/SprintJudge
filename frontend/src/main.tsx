import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { applyStoredTheme } from "./stores/useUIStore";
import "./index.css";

// Sync the <html> class with the persisted theme (also handled inline in
// index.html to avoid a flash; this keeps it correct after HMR / re-mounts).
// HeroUI v3 (3.2.x) themes via @heroui/styles + the `.dark` class on <html>;
// dark is the default (set in index.html and useUIStore).
applyStoredTheme();

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
);

// Instant repeat loads (production only; dev server must stay untouched).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
    window.addEventListener("load", () => {
        navigator.serviceWorker.register("/sw.js").catch(() => {
            /* offline shell is an enhancement, never a hard dependency */
        });
    });
}
