import { afterEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ShareResultCard } from "./ShareResultCard";

const TOP = [
    { rank: 1, name: "Ada", score: 100 },
    { rank: 2, name: "Grace", score: 80 },
];

const originalClipboard = Object.getOwnPropertyDescriptor(navigator, "clipboard");
const originalExecCommand = document.execCommand;
const originalCreateObjectURL = URL.createObjectURL;
const originalRevokeObjectURL = URL.revokeObjectURL;

function setClipboard(value: unknown) {
    Object.defineProperty(navigator, "clipboard", { configurable: true, value });
}

afterEach(() => {
    if (originalClipboard) Object.defineProperty(navigator, "clipboard", originalClipboard);
    else delete (navigator as { clipboard?: unknown }).clipboard;
    document.execCommand = originalExecCommand;
    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
    vi.restoreAllMocks();
    vi.useRealTimers();
});

describe("ShareResultCard", () => {
    test("copies the summary with the Clipboard API when available", async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        setClipboard({ writeText });
        render(<ShareResultCard title="Final" top={TOP} players={4} questions={10} accuracy={75} />);

        fireEvent.click(screen.getByRole("button", { name: "Copy summary" }));

        expect(writeText).toHaveBeenCalledWith(
            "Final\n1. Ada - 100 pts\n2. Grace - 80 pts\n4 players | 10 questions | 75% correct",
        );
        expect(await screen.findByRole("button", { name: "Copied" })).toBeInTheDocument();
    });

    test("falls back to execCommand when the Clipboard API is missing", () => {
        setClipboard(undefined);
        const execCommand = vi.fn().mockReturnValue(true);
        document.execCommand = execCommand;
        render(<ShareResultCard top={TOP} />);

        fireEvent.click(screen.getByRole("button", { name: "Copy summary" }));

        expect(execCommand).toHaveBeenCalledWith("copy");
        expect(screen.getByRole("button", { name: "Copied" })).toBeInTheDocument();
    });

    test("does not claim success when copying fails", async () => {
        setClipboard({ writeText: vi.fn().mockRejectedValue(new Error("denied")) });
        render(<ShareResultCard top={TOP} />);

        fireEvent.click(screen.getByRole("button", { name: "Copy summary" }));

        expect(await screen.findByRole("button", { name: "Copy summary" })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Copied" })).not.toBeInTheDocument();
    });

    test("keeps the download anchor in the DOM through click and revokes the URL afterwards", () => {
        vi.useFakeTimers();
        const revoke = vi.fn();
        URL.createObjectURL = vi.fn().mockReturnValue("blob:card");
        URL.revokeObjectURL = revoke;
        let attachedAtClick = false;
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            attachedAtClick = document.body.contains(this);
        });
        render(<ShareResultCard top={TOP} />);

        fireEvent.click(screen.getByRole("button", { name: "Download card" }));

        expect(attachedAtClick).toBe(true);
        expect(revoke).not.toHaveBeenCalled();
        vi.advanceTimersByTime(0);
        expect(revoke).toHaveBeenCalledWith("blob:card");
    });
});
