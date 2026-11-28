import { beforeEach, describe, expect, test, vi } from "vitest";

const m = vi.hoisted(() => ({
    render: vi.fn(),
    applyStoredTheme: vi.fn(),
    watchSystemTheme: vi.fn(),
    applyStoredLocale: vi.fn(),
    motionReduced: vi.fn(() => false),
    subscribe: vi.fn(),
    resume: vi.fn(),
    setReduced: vi.fn(),
}));

vi.mock("react-dom/client", () => ({
    default: { createRoot: () => ({ render: m.render }) },
}));
vi.mock("./App", () => ({ App: () => null }));
vi.mock("./components/ErrorBoundary", () => ({ ErrorBoundary: ({ children }: any) => children }));
vi.mock("./stores/useUIStore", () => ({
    applyStoredTheme: m.applyStoredTheme,
    watchSystemTheme: m.watchSystemTheme,
    motionReduced: m.motionReduced,
    useUIStore: { subscribe: m.subscribe },
}));
vi.mock("./i18n", () => ({ applyStoredLocale: m.applyStoredLocale }));
vi.mock("./services/AudioEngine", () => ({ audio: { resume: m.resume } }));
vi.mock("./services/MotionService", () => ({ motion: { setReduced: m.setReduced } }));
vi.mock("@tanstack/react-query", () => ({
    QueryClient: class {},
    QueryClientProvider: ({ children }: any) => children,
}));

describe("main entry point", () => {
    beforeEach(() => {
        vi.resetModules();
        vi.clearAllMocks();
        document.getElementById("root")?.remove();
        document.querySelector('link[rel="manifest"]')?.remove();
        const root = document.createElement("div");
        root.id = "root";
        document.body.appendChild(root);
        window.dispatchEvent(new Event("pointerdown"));
        window.dispatchEvent(new Event("keydown"));
        vi.clearAllMocks();
    });

    test("initializes theme, locale, PWA metadata and the React tree", async () => {
        await import("./main");
        expect(m.applyStoredTheme).toHaveBeenCalledOnce();
        expect(m.watchSystemTheme).toHaveBeenCalledOnce();
        expect(m.applyStoredLocale).toHaveBeenCalledOnce();
        expect(document.querySelector('link[rel="manifest"]')).toHaveAttribute(
            "href",
            "/manifest.webmanifest",
        );
        expect(m.setReduced).toHaveBeenCalledWith(false);
        expect(m.subscribe).toHaveBeenCalledOnce();
        expect(m.render).toHaveBeenCalledOnce();
    });

    test("resumes audio on the first gesture and stops listening afterwards", async () => {
        await import("./main");
        window.dispatchEvent(new Event("pointerdown"));
        expect(m.resume).toHaveBeenCalledOnce();
        window.dispatchEvent(new Event("keydown"));
        expect(m.resume).toHaveBeenCalledOnce();
    });

    test("syncs motion preference when the store changes", async () => {
        await import("./main");
        const listener = m.subscribe.mock.calls[0][0] as (next: any, prev: any) => void;
        m.motionReduced.mockReturnValue(true);
        listener({ motion: "reduced" }, { motion: "full" });
        expect(m.setReduced).toHaveBeenCalledWith(true);
        listener({ motion: "reduced" }, { motion: "reduced" });
        expect(m.setReduced).toHaveBeenCalledTimes(2);
    });
});
