import { useEffect } from "react";
import { RouterProvider } from "@tanstack/react-router";
import { router } from "./router";
import { motionReduced, useUIStore } from "./stores/useUIStore";

export function App() {
    // One class drives every CSS animation, so the in-app toggle and the OS
    // preference agree with the GSAP hooks (which read motionReduced()).
    const motion = useUIStore((s) => s.motion);
    useEffect(() => {
        document.documentElement.classList.toggle("motion-reduced", motionReduced());
    }, [motion]);
    return <RouterProvider router={router} />;
}
