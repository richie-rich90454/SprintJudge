import type { ReactNode } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

const mq = vi.hoisted(() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));

vi.stubGlobal("matchMedia", () => mq);

const chromeMock = vi.hoisted(() => ({
    onResolved: undefined as ((event: Record<string, unknown>) => void) | undefined,
    unsubscribe: vi.fn(),
}));
const i18nMock = vi.hoisted(() => ({
    setLocale: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({
    RouterProvider: () => null,
}));

vi.mock("./router", () => ({
    router: {
        subscribe: vi.fn((_event: string, cb: (event: Record<string, unknown>) => void) => {
            chromeMock.onResolved = cb;
            return chromeMock.unsubscribe;
        }),
    },
    RouterProvider: () => null,
}));

vi.mock("./i18n", () => ({
    I18nProvider: ({ children }: { children: ReactNode }) => children,
    LOCALES: [
        { code: "en", label: "English" },
        { code: "ar", label: "Arabic" },
    ],
    useI18n: () => ({
        t: (key: string) => key,
        locale: "en",
        setLocale: i18nMock.setLocale,
    }),
}));

const realLocation = globalThis.location;

const swMock = vi.hoisted(() => {
    const swListeners: Record<string, Array<() => void>> = {};
    return {
        addEventListener: vi.fn((type: string, cb: () => void) => {
            (swListeners[type] ??= []).push(cb);
        }),
        removeEventListener: vi.fn(),
        controller: {} as object | null,
        register: vi.fn(() => new Promise(() => {})),
        swListeners,
        resolve: undefined as ((value: unknown) => void) | undefined,
        fire: (type: string) => swListeners[type]?.forEach((cb) => cb()),
    };
});

function installServiceWorker() {
    Object.defineProperty(window.navigator, "serviceWorker", {
        configurable: true,
        value: swMock,
    });
}

function registrationMock(waiting: { postMessage: ReturnType<typeof vi.fn> } | null) {
    const listeners: Record<string, Array<() => void>> = {};
    return {
        waiting,
        installing: {
            state: "installing",
            addEventListener: vi.fn((type: string, cb: () => void) => {
                (listeners[type] ??= []).push(cb);
            }),
        },
        addEventListener: vi.fn((type: string, cb: () => void) => {
            (listeners[type] ??= []).push(cb);
        }),
        listeners,
        fire: (type: string) => listeners[type]?.forEach((cb) => cb()),
    };
}

function listenerOf(mock: ReturnType<typeof vi.fn>, type: string) {
    return mock.mock.calls.find((call) => call[0] === type)?.[1] as (() => void) | undefined;
}

import { App } from "./App";
import { useUIStore } from "./stores/useUIStore";

describe("App motion class", () => {
    beforeEach(() => {
        document.documentElement.classList.remove("motion-reduced");
        useUIStore.getState().setMotion("system");
        mq.matches = false;
        mq.addEventListener.mockClear();
    });

    test("updates the class when the OS reduced-motion preference flips", () => {
        render(<App />);
        const listener = mq.addEventListener.mock.calls.find(
            (call) => call[0] === "change",
        )?.[1] as (() => void) | undefined;
        expect(listener).toBeTypeOf("function");

        expect(document.documentElement).not.toHaveClass("motion-reduced");
        mq.matches = true;
        listener!();
        expect(document.documentElement).toHaveClass("motion-reduced");

        mq.matches = false;
        listener!();
        expect(document.documentElement).not.toHaveClass("motion-reduced");
    });

    test("syncs the class without a change listener when matchMedia has no addEventListener", () => {
        const original = mq.addEventListener;
        (mq as unknown as { addEventListener: unknown }).addEventListener = undefined;
        useUIStore.getState().setMotion("reduced");
        render(<App />);
        expect(document.documentElement).toHaveClass("motion-reduced");
        (mq as unknown as { addEventListener: unknown }).addEventListener = original;
    });
});

describe("AppChrome", () => {
    beforeEach(() => {
        document.body.innerHTML = "";
        chromeMock.onResolved = undefined;
        vi.clearAllMocks();
        swMock.register.mockImplementation(
            () =>
                new Promise((resolve) => {
                    swMock.resolve = resolve;
                }),
        );
        swMock.controller = {};
        for (const key of Object.keys(swMock.swListeners)) delete swMock.swListeners[key];
        localStorage.clear();
        document.documentElement.classList.remove("motion-reduced");
        useUIStore.getState().setMotion("system");
    });

    afterEach(() => {
        vi.unstubAllEnvs();
        vi.stubGlobal("location", realLocation);
        document.body.innerHTML = "";
    });

    test("prepares the skip target, announces later navigations and focuses it", () => {
        const main = document.createElement("main");
        document.body.appendChild(main);
        const scrollIntoView = vi.fn();
        Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
            configurable: true,
            value: scrollIntoView,
        });

        render(<App />);
        expect(main.id).toBe("main-content");
        expect(main).toHaveAttribute("tabindex", "-1");

        const onResolved = chromeMock.onResolved!;
        expect(onResolved).toBeTypeOf("function");
        expect(screen.getByRole("status")).toBeEmptyDOMElement();

        act(() => onResolved({ pathChanged: false, toLocation: { pathname: "/ignored" } }));
        expect(screen.getByRole("status")).toBeEmptyDOMElement();

        act(() => onResolved({ pathChanged: true, toLocation: { pathname: "/first" } }));
        expect(screen.getByRole("status")).toBeEmptyDOMElement();
        act(() => onResolved({ pathChanged: true, toLocation: { pathname: "/second" } }));
        expect(screen.getByRole("status")).toHaveTextContent("a11y.route: /second");

        fireEvent.click(screen.getByRole("link", { name: "a11y.skip" }));
        expect(scrollIntoView).toHaveBeenCalledOnce();
    });

    test("changing the locale calls setLocale", () => {
        render(<App />);
        fireEvent.change(screen.getByLabelText("a11y.language"), { target: { value: "ar" } });
        expect(i18nMock.setLocale).toHaveBeenCalledWith("ar");
    });

    test("skips PWA setup outside production or without serviceWorker support", () => {
        render(<App />);
        expect(swMock.register).not.toHaveBeenCalled();
    });

    test("registers the worker in production and drives the update banner", async () => {
        vi.stubEnv("PROD", true);
        installServiceWorker();
        const reload = vi.fn();
        vi.stubGlobal("location", { reload });
        const reg = registrationMock({ postMessage: vi.fn() });

        render(<App />);
        expect(swMock.register).toHaveBeenCalledWith("/sw.js");

        window.dispatchEvent(new Event("oq-update-accept"));
        expect(reg.waiting?.postMessage).not.toHaveBeenCalled();

        await act(async () => {
            swMock.resolve!(reg);
        });
        expect(screen.getByText("pwa.update")).toBeInTheDocument();
        expect(listenerOf(reg.addEventListener, "updatefound")).toBeTypeOf("function");

        swMock.fire("controllerchange");
        expect(reload).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", { name: "pwa.reload" }));
        expect(reg.waiting?.postMessage).toHaveBeenCalledWith({ type: "SKIP_WAITING" });

        reg.fire("updatefound");
        const stateChange = listenerOf(reg.installing.addEventListener, "statechange")!;
        Object.defineProperty(reg.installing, "state", { configurable: true, value: "installed" });
        stateChange();

        swMock.fire("controllerchange");
        expect(reload).toHaveBeenCalledOnce();
    });

    test("updatefound without an installing worker does not signal", async () => {
        vi.stubEnv("PROD", true);
        installServiceWorker();
        const reg = registrationMock(null);
        Object.defineProperty(reg, "installing", { configurable: true, value: null });

        render(<App />);
        await act(async () => {
            swMock.resolve!(reg);
        });
        expect(() => reg.fire("updatefound")).not.toThrow();
        expect(reg.addEventListener).toHaveBeenCalledWith("updatefound", expect.any(Function));
    });

    test("an installed worker without a controller does not signal", async () => {
        vi.stubEnv("PROD", true);
        installServiceWorker();
        swMock.controller = null;
        const reg = registrationMock(null);

        render(<App />);
        await act(async () => {
            swMock.resolve!(reg);
        });
        reg.fire("updatefound");
        const stateChange = listenerOf(reg.installing.addEventListener, "statechange")!;
        Object.defineProperty(reg.installing, "state", { configurable: true, value: "installed" });
        stateChange();
        expect(screen.queryByText("pwa.update")).not.toBeInTheDocument();
    });

    test("a failed worker registration is silent", async () => {
        vi.stubEnv("PROD", true);
        installServiceWorker();
        swMock.register.mockRejectedValueOnce(new Error("offline"));

        render(<App />);
        await act(async () => Promise.resolve());
        expect(screen.queryByText("pwa.update")).not.toBeInTheDocument();
    });
});
