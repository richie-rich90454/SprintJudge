import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, renderHook, screen } from "@testing-library/react";
import { I18nProvider } from "../../i18n";
import { STAGE_THEMES, StageThemePicker, stageThemeStyle, useStageTheme } from "./StageTheme";

describe("StageTheme", () => {
    beforeEach(() => localStorage.clear());
    afterEach(() => localStorage.clear());

    test("exposes only the selected theme's token overrides", () => {
        expect(stageThemeStyle("classic")).toEqual({});
        expect(stageThemeStyle("neon")).toHaveProperty("--stage-surface");
        expect(stageThemeStyle("unknown" as never)).toEqual({});
    });

    test("falls back to classic for missing, invalid or unreadable storage", () => {
        const { result } = renderHook(() => useStageTheme());
        expect(result.current[0]).toBe("classic");

        localStorage.setItem("oq-stage-theme", "bogus");
        const invalid = renderHook(() => useStageTheme());
        expect(invalid.result.current[0]).toBe("classic");

        const spy = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        expect(renderHook(() => useStageTheme()).result.current[0]).toBe("classic");
        spy.mockRestore();
    });

    test("chooses a theme, persists it and follows storage events from other tabs", () => {
        const { result, unmount } = renderHook(() => useStageTheme());
        act(() => result.current[1]("arena"));
        expect(result.current[0]).toBe("arena");
        expect(localStorage.getItem("oq-stage-theme")).toBe("arena");

        act(() => {
            localStorage.setItem("oq-stage-theme", "light");
            window.dispatchEvent(new StorageEvent("storage", { key: "oq-stage-theme" }));
        });
        expect(result.current[0]).toBe("light");

        act(() => {
            window.dispatchEvent(new StorageEvent("storage", { key: "other-key" }));
        });
        expect(result.current[0]).toBe("light");
        unmount();
    });

    test("the picker marks the active theme and switches on click", () => {
        render(<I18nProvider><StageThemePicker className="host-picker" /></I18nProvider>);
        const buttons = screen.getAllByRole("button");
        expect(buttons).toHaveLength(STAGE_THEMES.length);
        expect(buttons[0]).toHaveAttribute("aria-pressed", "true");
        fireEvent.click(buttons[2]);
        expect(buttons[2]).toHaveAttribute("aria-pressed", "true");
        expect(buttons[0]).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByTitle("Stage theme: Neon").closest("div")).toHaveClass("host-picker");
    });
});
