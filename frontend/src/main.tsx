import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { applyStoredTheme, motionReduced, useUIStore, watchSystemTheme } from "./stores/useUIStore";
import { applyStoredLocale } from "./i18n";
import { audio } from "./services/AudioEngine";
import { motion } from "./services/MotionService";
import "@fontsource/noto-sans-mono/400.css";
import "@fontsource/noto-sans-mono/500.css";
import "@fontsource/noto-sans-mono/600.css";
import "./index.css";

// Sync the <html> class with the persisted theme (also handled inline in
// index.html to avoid a flash; this keeps it correct after HMR / re-mounts).
// Light-only lockdown (see useUIStore).
applyStoredTheme();
watchSystemTheme();
applyStoredLocale();

// PWA install metadata is injected here so index.html stays untouched.
const manifestLink = document.createElement("link");
manifestLink.rel = "manifest";
manifestLink.href = "/manifest.webmanifest";
document.head.appendChild(manifestLink);

// Keep the imperative motion engine in sync with the persisted motion
// preference (the system default honors the OS reduced-motion setting).
motion.setReduced(motionReduced());
useUIStore.subscribe((s, prev) => {
    if (s.motion !== prev.motion) motion.setReduced(motionReduced());
});

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
            <ErrorBoundary>
                <App />
            </ErrorBoundary>
        </QueryClientProvider>
    </React.StrictMode>,
);
