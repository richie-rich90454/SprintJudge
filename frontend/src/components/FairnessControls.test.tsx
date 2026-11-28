import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { FairnessControls, readFairness } from "./FairnessControls";

const api = vi.hoisted(() => ({ updateSettings: vi.fn() }));
vi.mock("../services/AdminApiService", () => ({ adminApi: api }));
const updateSettings = api.updateSettings;

describe("FairnessControls", () => {
    beforeEach(() => {
        localStorage.clear();
        updateSettings.mockClear();
        updateSettings.mockResolvedValue({});
    });
    afterEach(() => localStorage.clear());

    test("defaults to both options shuffled", () => {
        expect(readFairness()).toEqual({ shuffleOptions: true, shuffleQuestions: true });
        render(<FairnessControls />);
        expect(
            screen.getAllByRole("switch").every((el) => el.getAttribute("aria-checked") === "true"),
        ).toBe(true);
        expect(screen.getAllByText("ON")).toHaveLength(2);
    });

    test("reads persisted choices, including explicit false values", () => {
        localStorage.setItem(
            "oq-fairness",
            JSON.stringify({ shuffleOptions: false, shuffleQuestions: false }),
        );
        expect(readFairness()).toEqual({ shuffleOptions: false, shuffleQuestions: false });
        render(<FairnessControls />);
        expect(
            screen
                .getAllByRole("switch")
                .every((el) => el.getAttribute("aria-checked") === "false"),
        ).toBe(true);
        expect(screen.getAllByText("OFF")).toHaveLength(2);
    });

    test("falls back to defaults for malformed or unavailable storage", () => {
        localStorage.setItem("oq-fairness", "{not json");
        expect(readFairness()).toEqual({ shuffleOptions: true, shuffleQuestions: true });
        const spy = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        expect(readFairness()).toEqual({ shuffleOptions: true, shuffleQuestions: true });
        spy.mockRestore();
    });

    test("toggles a switch, persists it and mirrors the setting to the API", async () => {
        render(<FairnessControls />);
        fireEvent.click(screen.getAllByRole("switch")[0]);
        expect(JSON.parse(localStorage.getItem("oq-fairness") ?? "{}")).toEqual({
            shuffleOptions: false,
            shuffleQuestions: true,
        });
        expect(updateSettings).toHaveBeenCalledWith({
            shuffleOptions: "false",
            shuffleQuestions: "true",
        });
        expect(screen.getAllByRole("switch")[0]).toHaveAttribute("aria-checked", "false");
    });

    test("keeps the local toggle when the API rejects the update", async () => {
        updateSettings.mockRejectedValueOnce(new Error("offline"));
        render(<FairnessControls />);
        fireEvent.click(screen.getAllByRole("switch")[1]);
        expect(screen.getAllByRole("switch")[1]).toHaveAttribute("aria-checked", "false");
    });

    test("keeps working when localStorage writes are blocked", () => {
        const spy = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        render(<FairnessControls />);
        fireEvent.click(screen.getAllByRole("switch")[0]);
        expect(screen.getAllByRole("switch")[0]).toHaveAttribute("aria-checked", "false");
        spy.mockRestore();
    });
});
