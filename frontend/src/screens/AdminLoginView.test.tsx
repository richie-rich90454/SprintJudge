import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { AdminLoginView } from "./AdminLoginView";

vi.mock("../components/Shell", () => ({
    Shell: ({ children, minimal }: { children: ReactNode; minimal?: boolean }) => (
        <div data-minimal={minimal}>{children}</div>
    ),
}));
vi.mock("@tanstack/react-router", () => ({
    Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

describe("AdminLoginView", () => {
    const originalSearch = window.location.search;

    beforeEach(() => {
        window.history.replaceState({}, "", "/admin/login");
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({ ok: true, json: async () => [] }),
        );
    });
    afterEach(() => {
        window.history.replaceState({}, "", originalSearch || "/admin/login");
        vi.unstubAllGlobals();
    });

    test("renders the sign-in form and the player-view link", () => {
        render(<AdminLoginView />);
        expect(screen.getByLabelText("Username")).toHaveAttribute("name", "username");
        expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
        expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Back to player view" })).toHaveAttribute("href", "/");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    test("shows the failed-login alert when the URL has an error", () => {
        window.history.replaceState({}, "", "/admin/login?error=1");
        render(<AdminLoginView />);
        expect(screen.getByRole("alert")).toHaveTextContent("Wrong username or password");
        expect(screen.getByLabelText("Username")).toHaveAttribute("aria-invalid", "true");
    });

    test("lists OAuth providers returned by the public endpoint", async () => {
        (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
            ok: true,
            json: async () => [{ id: "g", name: "Google", authorizationUrl: "https://example.com/oauth" }],
        });
        render(<AdminLoginView />);
        expect(await screen.findByRole("link", { name: "Google" })).toHaveAttribute(
            "href",
            "https://example.com/oauth",
        );
        expect(screen.getByText("Or sign in with")).toBeInTheDocument();
    });

    test("handles a non-OK response and a rejected fetch", async () => {
        (fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ ok: false, json: async () => [] });
        const first = render(<AdminLoginView />);
        await waitFor(() => expect(fetch).toHaveBeenCalledWith("/api/public/auth/providers"));
        expect(first.container.querySelector('a[href="https://example.com/oauth"]')).toBeNull();

        (fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error("offline"));
        render(<AdminLoginView />);
        expect(screen.queryByText("Or sign in with")).not.toBeInTheDocument();
    });
});
