import { afterEach, describe, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
        render(
            <ShareResultCard title="Final" top={TOP} players={4} questions={10} accuracy={75} />,
        );

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

    test("escapes XML characters and includes metadata in the downloaded card", async () => {
        let svg = "";
        URL.createObjectURL = vi.fn((blob: Blob) => {
            svg = "pending";
            void blob.text().then((value) => {
                svg = value;
            });
            return "blob:card";
        });
        render(
            <ShareResultCard
                title={'A & "B" <C>'}
                top={[{ rank: 1, name: "O'Brien & Co", score: 100 }]}
                players={4}
                questions={10}
                accuracy={75}
            />,
        );
        fireEvent.click(screen.getByRole("button", { name: "Download card" }));
        await waitFor(() => expect(svg).not.toBe(""));
        expect(svg).toContain("A &amp; &quot;B&quot; &lt;C&gt;");
        expect(svg).toContain("O&apos;Brien &amp; Co");
        expect(svg).toContain("4 players  |  10 questions  |  75% correct");
    });

    test("resets the copied badge after two seconds", async () => {
        vi.useFakeTimers();
        setClipboard({ writeText: vi.fn().mockResolvedValue(undefined) });
        render(<ShareResultCard top={TOP} />);
        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Copy summary" }));
            await Promise.resolve();
        });
        expect(screen.getByRole("button", { name: "Copied" })).toBeInTheDocument();
        act(() => vi.advanceTimersByTime(2000));
        expect(screen.getByRole("button", { name: "Copy summary" })).toBeInTheDocument();
    });

    test("renders nothing without podium rows", () => {
        const { container } = render(<ShareResultCard top={[]} />);
        expect(container).toBeEmptyDOMElement();
    });

    test("keeps the download anchor in the DOM through click and revokes the URL afterwards", () => {
        vi.useFakeTimers();
        const revoke = vi.fn();
        URL.createObjectURL = vi.fn().mockReturnValue("blob:card");
        URL.revokeObjectURL = revoke;
        let attachedAtClick = false;
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
            this: HTMLAnchorElement,
        ) {
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
