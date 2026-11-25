import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";

vi.mock("./screens/LandingView", () => ({ LandingView: () => null }));
vi.mock("./screens/JoinView", () => ({ JoinView: () => null }));
vi.mock("./screens/SoloView", () => ({ SoloView: () => null }));
vi.mock("./screens/ExploreView", () => ({ ExploreView: () => null }));
vi.mock("./screens/AdminLoginView", () => ({ AdminLoginView: () => null }));

import { AdminRouteError } from "./router";

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
});
