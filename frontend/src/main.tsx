import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";
import { applyStoredTheme, watchSystemTheme } from "./stores/useUIStore";
import { audio } from "./services/AudioEngine";
import "./index.css";

// Sync the <html> class with the persisted theme (also handled inline in
// index.html to avoid a flash; this keeps it correct after HMR / re-mounts).
// HeroUI v3 (3.2.x) themes via @heroui/styles + the `.dark` class on <html>;
// dark is the default (set in index.html and useUIStore).
applyStoredTheme();
watchSystemTheme();

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: 1,
        },
    },
});

// Browsers block audio until a user gesture; resume the Tone context on the
// first interaction anywhere in the app so game SFX fire reliably.
const armAudio = () => {
    audio.resume();
    window.removeEventListener("pointerdown", armAudio);
    window.removeEventListener("keydown", armAudio);
};
window.addEventListener("pointerdown", armAudio);
window.addEventListener("keydown", armAudio);

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <QueryClientProvider client={queryClient}>
            <App />
        </QueryClientProvider>
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
