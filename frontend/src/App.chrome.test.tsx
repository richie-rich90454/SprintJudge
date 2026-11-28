import { beforeEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";

const m = vi.hoisted(() => ({
    handler: undefined as ((event: any) => void) | undefined,
    unsubscribe: vi.fn(),
}));

vi.mock("@tanstack/react-router", () => ({ RouterProvider: () => <main>Route content</main> }));
vi.mock("./router", () => ({
    router: {
        subscribe: (_event: string, handler: (event: any) => void) => {
            m.handler = handler;
            return m.unsubscribe;
        },
    },
}));

const mq = vi.hoisted(() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
vi.stubGlobal("matchMedia", () => mq);

vi.mock("./stores/useUIStore", async (importOriginal) => {
    const actual = await importOriginal<typeof import("./stores/useUIStore")>();
    return { ...actual };
});

import { App } from "./App";

describe("App chrome", () => {
    beforeEach(() => {
        m.handler = undefined;
        m.unsubscribe.mockClear();
        document.documentElement.classList.remove("motion-reduced");
        localStorage.removeItem("oq-locale");
    });

    test("gives the main landmark an id and tab index, and focuses it on skip", () => {
        HTMLElement.prototype.scrollIntoView = vi.fn();
        render(<App />);
        const main = document.querySelector("main")!;
        expect(main).toHaveAttribute("id", "main-content");
        expect(main).toHaveAttribute("tabindex", "-1");
        const skip = screen.getByRole("link", { name: "Skip to main content" });
        fireEvent.click(skip);
        expect(document.activeElement).toBe(main);
    });

    test("announces route changes after the first navigation", () => {
        const { unmount } = render(<App />);
        const status = screen.getByRole("status");
        act(() => m.handler?.({ pathChanged: false, toLocation: { pathname: "/ignored" } }));
        expect(status).toBeEmptyDOMElement();
        act(() => m.handler?.({ pathChanged: true, toLocation: { pathname: "/play" } }));
        act(() => m.handler?.({ pathChanged: true, toLocation: { pathname: "/join" } }));
        expect(status).not.toBeEmptyDOMElement();
        expect(status.textContent).not.toContain("ignored");

        const subscriptionCleanup = m.unsubscribe;
        unmount();
        expect(subscriptionCleanup).toHaveBeenCalledOnce();
    });

    test("changes the locale through the accessibility selector", () => {
        render(<App />);
        const select = screen.getByRole("combobox", { name: "Language" });
        fireEvent.change(select, { target: { value: "ar" } });
        expect(localStorage.getItem("oq-locale")).toBe("ar");
        expect(document.documentElement).toHaveAttribute("dir", "rtl");
    });

    test("offers a reload when an update is announced", () => {
        render(<App />);
        act(() => window.dispatchEvent(new Event("oq-update-available")));
        expect(screen.getByText("A new version is available.")).toBeInTheDocument();
        const reload = vi.fn();
        window.addEventListener("oq-update-accept", reload);
        fireEvent.click(screen.getByRole("button", { name: "Reload" }));
        expect(reload).toHaveBeenCalledOnce();
        window.removeEventListener("oq-update-accept", reload);
    });

    test("honors OS reduced-motion changes while mounted", () => {
        render(<App />);
        const listener = mq.addEventListener.mock.calls.find(
            (call) => call[0] === "change",
        )?.[1] as (() => void) | undefined;
        expect(listener).toBeTypeOf("function");
        mq.matches = true;
        listener!();
        expect(document.documentElement).toHaveClass("motion-reduced");
        mq.matches = false;
        listener!();
        expect(document.documentElement).not.toHaveClass("motion-reduced");
    });
});
