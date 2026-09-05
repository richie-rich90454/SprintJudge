import { MotionConfig } from "framer-motion";
import { RouterProvider } from "@tanstack/react-router";
import { router } from "./router";
import { motionReduced, useUIStore } from "./stores/useUIStore";

/**
 * Gates every declarative framer-motion animation on the motion preference.
 * The imperative MotionService already honors it; without this wrapper the
 * motion toggle only silenced half the app.
 */
function MotionGate({ children }: { children: React.ReactNode }) {
    useUIStore((s) => s.motion);
    return (
        <MotionConfig reducedMotion={motionReduced() ? "always" : "user"}>
            {children}
        </MotionConfig>
    );
}

export function App() {
    return (
        <MotionGate>
            <RouterProvider router={router} />
        </MotionGate>
    );
}
