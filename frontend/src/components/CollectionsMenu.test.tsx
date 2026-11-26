import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { CollectionsMenu } from "./CollectionsMenu";

describe("CollectionsMenu", () => {
    beforeEach(() => {
        localStorage.clear();
        vi.spyOn(Date, "now").mockReturnValue(42);
    });
    afterEach(() => {
        localStorage.clear();
        vi.restoreAllMocks();
    });

    test("starts empty and creates a folder with the current quiz", () => {
        render(<CollectionsMenu quizId="q1" title="Quiz One" />);
        expect(screen.getByRole("button", { name: "Collections" })).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Collections" }));
        expect(screen.getByText("No folders yet.")).toBeInTheDocument();
        expect(
            screen.getByText("Quiz One - folders are kept in this browser."),
        ).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Create" }));
        expect(JSON.parse(localStorage.getItem("oq_collections") ?? "[]")).toEqual([]);

        const input = screen.getByRole("textbox", { name: "New folder name" });
        fireEvent.change(input, { target: { value: "  Favorites  " } });
        fireEvent.keyDown(input, { key: "Enter" });
        expect(JSON.parse(localStorage.getItem("oq_collections") ?? "[]")).toEqual([
            { id: "42", name: "Favorites", quizIds: ["q1"] },
        ]);
        expect(input).toHaveValue("");
        fireEvent.click(screen.getByRole("button", { name: "Close dialog" }));
        expect(screen.getByRole("button", { name: /Collections \(1\)/ })).toBeInTheDocument();
    });

    test("loads persisted folders, toggles membership and ignores malformed storage", () => {
        localStorage.setItem(
            "oq_collections",
            JSON.stringify([
                { id: "a", name: "Saved folder", quizIds: ["q1"] },
                { id: "b", name: "Other folder", quizIds: ["q2", "q3"] },
            ]),
        );
        const { unmount } = render(<CollectionsMenu quizId="q1" title="Quiz One" />);
        fireEvent.click(screen.getByRole("button", { name: /Collections \(1\)/ }));
        expect(screen.getByRole("button", { name: /Saved folder/ })).toHaveAttribute(
            "aria-pressed",
            "true",
        );
        expect(screen.getByRole("button", { name: /Other folder/ })).toHaveTextContent("2 quizzes");

        fireEvent.click(screen.getByRole("button", { name: /Saved folder/ }));
        expect(JSON.parse(localStorage.getItem("oq_collections") ?? "[]")[0].quizIds).toEqual([]);
        fireEvent.click(screen.getByRole("button", { name: /Other folder/ }));
        expect(JSON.parse(localStorage.getItem("oq_collections") ?? "[]")[1].quizIds).toEqual([
            "q2",
            "q3",
            "q1",
        ]);
        unmount();

        localStorage.setItem("oq_collections", "{bad json");
        render(<CollectionsMenu quizId="q1" title="Quiz One" />);
        fireEvent.click(screen.getByRole("button", { name: "Collections" }));
        expect(screen.getByText("No folders yet.")).toBeInTheDocument();
    });

    test("ignores storage that is not an array and an empty create", () => {
        localStorage.setItem("oq_collections", JSON.stringify({ id: "a", name: "Not a list" }));
        render(<CollectionsMenu quizId="q1" title="Quiz One" />);
        fireEvent.click(screen.getByRole("button", { name: "Collections" }));
        expect(screen.getByText("No folders yet.")).toBeInTheDocument();

        const input = screen.getByRole("textbox", { name: "New folder name" });
        fireEvent.change(input, { target: { value: "   " } });
        fireEvent.keyDown(input, { key: "Enter" });
        expect(localStorage.getItem("oq_collections")).toBe(
            JSON.stringify({ id: "a", name: "Not a list" }),
        );
        expect(screen.getByText("No folders yet.")).toBeInTheDocument();
    });

    test("survives blocked localStorage reads and writes", () => {
        const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        render(<CollectionsMenu quizId="q1" title="Quiz One" />);
        getItem.mockRestore();

        fireEvent.click(screen.getByRole("button", { name: "Collections" }));
        const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        fireEvent.change(screen.getByRole("textbox", { name: "New folder name" }), {
            target: { value: "Local" },
        });
        fireEvent.click(screen.getByRole("button", { name: "Create" }));
        expect(screen.getByRole("button", { name: /Local/ })).toBeInTheDocument();
        setItem.mockRestore();
    });
});
