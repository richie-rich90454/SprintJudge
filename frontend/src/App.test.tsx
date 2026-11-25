import type { ReactNode } from "react";
import { render } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";

const mq = vi.hoisted(() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));

vi.stubGlobal("matchMedia", () => mq);

vi.mock("./router", () => ({
    router: { subscribe: vi.fn(() => vi.fn()) },
    RouterProvider: () => null,
}));

vi.mock("./i18n", () => ({
    I18nProvider: ({ children }: { children: ReactNode }) => children,
    LOCALES: [],
    useI18n: () => ({ t: (key: string) => key, locale: "en", setLocale: vi.fn() }),
}));

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
});
