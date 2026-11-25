import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

const realLocation = globalThis.location;

let shouldThrow = true;

function Fragile() {
    if (shouldThrow) throw new Error("boom");
    return <p>Recovered content</p>;
}

describe("ErrorBoundary", () => {
    beforeEach(() => {
        shouldThrow = true;
        vi.spyOn(console, "error").mockImplementation(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.stubGlobal("location", realLocation);
        localStorage.removeItem("oq-locale");
    });

    test("retry recovers in place without a reload", () => {
        localStorage.setItem("oq-locale", "en");
        render(
            <ErrorBoundary>
                <Fragile />
            </ErrorBoundary>,
        );

        expect(screen.getByText("Something went wrong")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Reload" })).toBeInTheDocument();

        shouldThrow = false;
        fireEvent.click(screen.getByRole("button", { name: "Try again" }));

        expect(screen.getByText("Recovered content")).toBeInTheDocument();
    });

    test("fallback strings follow the stored locale", () => {
        localStorage.setItem("oq-locale", "ar");
        render(
            <ErrorBoundary>
                <Fragile />
            </ErrorBoundary>,
        );

        expect(screen.getByText("حدث خطأ ما")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "حاول مرة أخرى" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "إعادة التحميل" })).toBeInTheDocument();
    });

    test("the reload button reloads the document", () => {
        localStorage.setItem("oq-locale", "en");
        const reload = vi.fn();
        vi.stubGlobal("location", { reload });
        render(
            <ErrorBoundary>
                <Fragile />
            </ErrorBoundary>,
        );
        fireEvent.click(screen.getByRole("button", { name: "Reload" }));
        expect(reload).toHaveBeenCalledOnce();
    });

    test("caller-supplied fallback still wins", () => {
        render(
            <ErrorBoundary fallback={<p>Custom fallback</p>}>
                <Fragile />
            </ErrorBoundary>,
        );
        expect(screen.getByText("Custom fallback")).toBeInTheDocument();
    });
});
