import { describe, test, expect, beforeEach, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { I18nProvider, useI18n } from "../../i18n";
import { RichStemEditor, RichStemPreview } from "./RichStemFields";

function LocaleHarness({ children }: { children: ReactNode }) {
    const { setLocale } = useI18n();
    return (
        <>
            <button type="button" onClick={() => setLocale("ar")}>
                Switch to Arabic
            </button>
            {children}
        </>
    );
}

function renderInProvider(ui: ReactNode) {
    return render(<I18nProvider><LocaleHarness>{ui}</LocaleHarness></I18nProvider>);
}

describe("RichStemFields locale switching", () => {
    beforeEach(() => {
        localStorage.clear();
    });

    test("editor labels switch to Arabic", () => {
        renderInProvider(<RichStemEditor value={{}} onChange={() => {}} />);
        expect(screen.getByText("Rich stem media")).toBeInTheDocument();
        expect(screen.getByText("Image URL")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Switch to Arabic" }));

        expect(screen.getByText("وسائط نص السؤال")).toBeInTheDocument();
        expect(screen.getByText("رابط الصورة")).toBeInTheDocument();
        expect(document.documentElement.dir).toBe("rtl");
    });

    test("preview aria label and blocked-media message switch to Arabic", () => {
        renderInProvider(<RichStemPreview value={{ imageUrl: "javascript:alert(1)" }} />);
        expect(screen.getByLabelText("Rich stem preview")).toBeInTheDocument();
        expect(screen.getByText("Image URL blocked by the preview allowlist.")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Switch to Arabic" }));

        expect(screen.getByLabelText("معاينة نص السؤال")).toBeInTheDocument();
        expect(screen.getByText("حُظر رابط الصورة من قبل قائمة السماح للمعاينة.")).toBeInTheDocument();
    });
});

describe("RichStemFields editor", () => {
    beforeEach(() => localStorage.clear());

    test("every field emits a patch with the edited value", () => {
        const onChange = vi.fn();
        render(<RichStemEditor value={{}} onChange={onChange} />);

        const fields: [string, string, string][] = [
            ["Image URL", "https://x/a.png", "imageUrl"],
            ["Image alt text", "alt", "imageAlt"],
            ["Image caption / source", "caption", "imageCaption"],
            ["Audio URL", "https://x/a.mp3", "audioUrl"],
            ["Audio transcript", "hello", "audioTranscript"],
            ["Media source / license", "CC-BY", "mediaLicense"],
            ["LaTeX (use $...$ delimiters)", "$x^2$", "latex"],
            ["Code language", "py", "codeLanguage"],
            ["Code snippet", "print(1)", "code"],
        ];
        for (const [label, value, key] of fields) {
            onChange.mockClear();
            fireEvent.change(screen.getByLabelText(label), { target: { value } });
            expect(onChange).toHaveBeenCalledWith({ [key]: value });
        }
    });

    test("editing one field preserves the others", () => {
        const onChange = vi.fn();
        render(<RichStemEditor value={{ imageUrl: "https://x/a.png" }} onChange={onChange} />);
        fireEvent.change(screen.getByLabelText("Image alt text"), { target: { value: "alt" } });
        expect(onChange).toHaveBeenCalledWith({ imageUrl: "https://x/a.png", imageAlt: "alt" });
    });
});

describe("RichStemFields preview", () => {
    beforeEach(() => localStorage.clear());

    test("renders description, image, audio, latex and code", () => {
        render(
            <RichStemPreview
                description="A description"
                value={{
                    imageUrl: "https://x/a.png",
                    imageAlt: "Diagram",
                    imageCaption: "Fig 1",
                    audioUrl: "https://x/a.mp3",
                    audioTranscript: "Transcript text",
                    latex: "$\\alpha \\times \\beta$",
                    code: "print(1)",
                    codeLanguage: "py",
                }}
            />,
        );
        expect(screen.getByText("A description")).toBeInTheDocument();
        expect(screen.getByRole("img", { name: "Diagram" })).toBeInTheDocument();
        expect(screen.getByText("Fig 1")).toBeInTheDocument();
        expect(document.querySelector("audio")).toBeInTheDocument();
        expect(screen.getByText("Transcript")).toBeInTheDocument();
        expect(screen.getByText("α × β")).toBeInTheDocument();
        expect(screen.getByText("$\\alpha \\times \\beta$")).toBeInTheDocument();
        expect(screen.getByText("py: print(1)")).toBeInTheDocument();
    });

    test("an image without alt text uses the translated fallback", () => {
        render(<RichStemPreview value={{ imageUrl: "https://x/a.png" }} />);
        expect(screen.getByRole("img", { name: "Stem image" })).toBeInTheDocument();
    });

    test("audio without a transcript omits the details block", () => {
        render(<RichStemPreview value={{ audioUrl: "/local.mp3" }} />);
        expect(document.querySelector("audio")).toBeInTheDocument();
        expect(screen.queryByText("Transcript")).not.toBeInTheDocument();
    });

    test("code without a language omits the prefix", () => {
        render(<RichStemPreview value={{ code: "x = 1" }} />);
        expect(screen.getByText("x = 1")).toBeInTheDocument();
    });

    test("blocked audio shows the allowlist warning", () => {
        render(<RichStemPreview value={{ audioUrl: "javascript:alert(1)" }} />);
        expect(screen.getByText("Audio URL blocked by the preview allowlist.")).toBeInTheDocument();
    });

    test("empty media shows the add-media hint", () => {
        render(<RichStemPreview value={{}} />);
        expect(screen.getByText("Add media or markup to see a preview.")).toBeInTheDocument();
    });
});
