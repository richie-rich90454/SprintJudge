import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, test, vi } from "vitest";

vi.mock("./screens/LandingView", () => ({ LandingView: () => null }));
vi.mock("./screens/JoinView", () => ({ JoinView: () => null }));
vi.mock("./screens/SoloView", () => ({ SoloView: () => null }));
vi.mock("./screens/ExploreView", () => ({ ExploreView: () => null }));
vi.mock("./screens/AdminLoginView", () => ({ AdminLoginView: () => null }));
// Intercept every lazy screen so preload() resolves locally instead of pulling
// real modules (and their editor/worker dependencies) into jsdom.
vi.mock("./screens/AdminDashboard", () => ({ AdminDashboard: () => null }));
vi.mock("./screens/AnalyticsView", () => ({ AnalyticsView: () => null }));
vi.mock("./screens/AssignmentView", () => ({ AssignmentView: () => null }));
vi.mock("./screens/BankBrowser", () => ({ BankBrowser: () => null }));
vi.mock("./screens/HostView", () => ({ HostView: () => null }));
vi.mock("./screens/PracticeView", () => ({ PracticeView: () => null }));
vi.mock("./screens/ProfileView", () => ({ ProfileView: () => null }));
vi.mock("./screens/QuestionView", () => ({ QuestionView: () => null }));
vi.mock("./screens/QuestionWizard", () => ({ QuestionWizard: () => null }));
vi.mock("./screens/ResultView", () => ({ ResultView: () => null }));
vi.mock("./screens/SpectatorView", () => ({ SpectatorView: () => null }));
vi.mock("./screens/VolumeReport", () => ({ VolumeReport: () => null }));

const routeState = vi.hoisted(() => ({ pathname: "/" }));
vi.mock("@tanstack/react-router", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@tanstack/react-router")>();
    return {
        ...actual,
        Outlet: () => null,
        useRouterState: vi.fn((opts: { select: (s: unknown) => unknown }) =>
            opts.select({ location: { pathname: routeState.pathname } }),
        ),
    };
});

import { AdminRouteError, router } from "./router";

describe("AdminRouteError", () => {
    test("shows the guard error and offers a retry", () => {
        const reset = vi.fn();
        render(
            <AdminRouteError
                error={new Error("The admin service is unavailable. Reload to try again.")}
                reset={reset}
            />,
        );

        expect(screen.getByRole("heading", { name: "Admin service unavailable" })).toBeInTheDocument();
        expect(screen.getByText(/admin service is unavailable/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(reset).toHaveBeenCalledOnce();
    });

    test("uses the fallback message for a non-Error value", () => {
        render(<AdminRouteError error={"bad"} reset={vi.fn()} />);
        expect(screen.getByText("The admin service is unavailable. Reload to try again.")).toBeInTheDocument();
    });
});

describe("router configuration", () => {
    test("parses and stringifies search strings", () => {
        expect(router.options.parseSearch?.("?a=1&b=two")).toEqual({ a: "1", b: "two" });
        expect(
            router.options.stringifySearch?.({
                a: 1,
                b: true,
                c: false,
                d: undefined,
                e: null,
                f: "x",
            }),
        ).toBe("?a=1&b=1&c=0&f=x");
        expect(router.options.stringifySearch?.({})).toBe("");
    });

    test("validates host search params", () => {
        const host = router.routeTree.children?.find((child) => (child as any).options.path === "/host");
        const validate = host?.options.validateSearch as (s: Record<string, unknown>) => Record<string, unknown>;
        expect(validate({ pin: "1234", projector: "1" })).toEqual({ pin: "1234", projector: true });
        expect(validate({ pin: 1234, projector: 1 })).toEqual({ pin: "1234", projector: true });
        expect(validate({ pin: true, projector: false })).toEqual({ pin: undefined, projector: false });
    });

    test("root renders a path-keyed route container", () => {
        const Root = router.routeTree.options.component as () => ReactElement;
        routeState.pathname = "/solo";
        const { container } = render(<Root />);
        expect(container.firstChild).toHaveClass("route-enter");
    });

    test("preloads every lazy route component", async () => {
        const children = router.routeTree.children ?? [];
        const lazy = children.filter(
            (child) => typeof (child.options.component as { preload?: unknown })?.preload === "function",
        );
        expect(lazy.length).toBeGreaterThanOrEqual(10);
        await Promise.all(
            lazy.map((child) => (child.options.component as { preload: () => Promise<unknown> }).preload()),
        );
    }, 30000);
});
