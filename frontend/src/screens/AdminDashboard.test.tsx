import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AdminDashboard } from "./AdminDashboard";

const m = vi.hoisted(() => ({
    admin: {
        quizzes: [] as any[],
        questions: [] as any[],
        activeQuizId: null as string | null,
        wizardOpen: false,
        loadQuizzes: vi.fn(),
        loadQuestions: vi.fn(),
        openWizard: vi.fn(),
        createQuiz: vi.fn(),
    },
    ui: { setPin: vi.fn() },
    api: {
        createGame: vi.fn(),
        buildBankQuiz: vi.fn(),
        exportBank: vi.fn(),
        importBank: vi.fn(),
        getSettings: vi.fn(),
        updateSettings: vi.fn(),
    },
    navigate: vi.fn(),
}));

vi.mock("../stores/useAdminStore", () => ({
    useAdminStore: (selector?: (s: Record<string, unknown>) => unknown) =>
        selector ? selector(m.admin) : m.admin,
}));
vi.mock("../stores/useUIStore", () => ({
    useUIStore: (selector: (s: Record<string, unknown>) => unknown) => selector(m.ui),
}));
vi.mock("../services/AdminApiService", () => ({ adminApi: m.api }));
vi.mock("axios", () => ({ default: { isAxiosError: (e: any) => !!e?.isAxiosError } }));
vi.mock("@tanstack/react-router", () => ({ useNavigate: () => m.navigate }));
vi.mock("../hooks/useMotion", () => ({ useStaggerIn: () => vi.fn() }));
vi.mock("./QuestionWizard", () => ({ QuestionWizard: () => <div data-testid="wizard" /> }));
vi.mock("../components/SoundToggle", () => ({ SoundToggle: () => <div /> }));
vi.mock("../components/MotionToggle", () => ({ MotionToggle: () => <div /> }));
vi.mock("../components/AboutDialog", () => ({ AboutDialog: () => <div /> }));
vi.mock("../components/LogoMark", () => ({ LogoMark: () => <div /> }));

const clickTab = (name: string) => {
    const tab = screen.getByRole("tab", { name });
    fireEvent.mouseDown(tab, { button: 0, ctrlKey: false });
    fireEvent.click(tab);
};

const quiz = (id: string) => ({ id, title: `Quiz ${id}`, description: `Description ${id}` });
const question = (id: string) => ({
    id,
    title: `Question ${id}`,
    questionType: "MULTIPLE_SELECT",
    timeLimitSec: 30,
    pointsBase: 100,
});

describe("AdminDashboard", () => {
    const originalSearch = window.location.search;

    beforeEach(() => {
        vi.clearAllMocks();
        window.history.replaceState({}, "", "/admin");
        Object.assign(m.admin, {
            quizzes: [quiz("1")],
            questions: [question("1")],
            activeQuizId: "1",
            wizardOpen: false,
        });
        m.admin.loadQuizzes.mockResolvedValue(undefined);
        m.admin.loadQuestions.mockResolvedValue(undefined);
        m.admin.createQuiz.mockResolvedValue("2");
        m.api.createGame.mockResolvedValue({ pinCode: "123456" });
        m.api.buildBankQuiz.mockResolvedValue({ quizId: "csa-1" });
        m.api.getSettings.mockResolvedValue({ theme: "dark" });
        m.api.updateSettings.mockResolvedValue(undefined);
        m.api.exportBank.mockResolvedValue("{}");
        m.api.importBank.mockResolvedValue(undefined);
        URL.createObjectURL = vi.fn(() => "blob:mock");
        URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => window.history.replaceState({}, "", originalSearch || "/admin"));

    test("loads quizzes and shows the dashboard summary and CSA presets", () => {
        render(<AdminDashboard />);
        expect(m.admin.loadQuizzes).toHaveBeenCalledOnce();
        expect(screen.getByText("Quiz sets")).toBeInTheDocument();
        expect(screen.getByText(/Start a CSA class/)).toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: /CSA|Mixed/ })).toHaveLength(5);
    });

    test("gives the dashboard game-mode select an accessible name", () => {
        render(<AdminDashboard />);
        expect(screen.getByRole("combobox", { name: "Game mode for hosting" })).toBeInTheDocument();
    });

    test("offers a retry after a failed quiz load and shows a sign-in form on 401", async () => {
        m.admin.loadQuizzes.mockRejectedValueOnce(new Error("offline"));
        render(<AdminDashboard />);
        expect(await screen.findByText(/Failed to load quizzes/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(m.admin.loadQuizzes).toHaveBeenCalledTimes(2);

        m.admin.loadQuizzes.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 401 },
        });
        window.history.replaceState({}, "", "/admin?error=1");
        render(<AdminDashboard />);
        expect(await screen.findByLabelText("Username")).toHaveAttribute("name", "username");
        expect(screen.getByRole("alert")).toHaveTextContent("Wrong username or password");
    });

    test("builds and hosts a CSA set, distinguishing expired sessions", async () => {
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: /CSA 1/ }));
        await waitFor(() => expect(m.api.createGame).toHaveBeenCalledWith("csa-1", "STANDARD"));
        expect(m.api.buildBankQuiz).toHaveBeenCalledWith({
            csaUnit: 1,
            count: 16,
            includeCoding: true,
        });
        expect(m.ui.setPin).toHaveBeenCalledWith("123456");
        expect(m.navigate).toHaveBeenCalledWith({
            to: "/host",
            search: { pin: "123456", projector: false },
        });

        m.api.buildBankQuiz.mockRejectedValueOnce({ response: { status: 403 } });
        fireEvent.click(screen.getByRole("button", { name: /CSA 2/ }));
        expect(await screen.findByText(/session expired/i)).toBeInTheDocument();

        m.api.buildBankQuiz.mockRejectedValueOnce(new Error("offline"));
        fireEvent.click(screen.getByRole("button", { name: /CSA 3/ }));
        expect(await screen.findByText(/Could not build the CSA game/)).toBeInTheDocument();
    });

    test("hosts the first quiz with the selected mode and reports failures", async () => {
        render(<AdminDashboard />);
        fireEvent.change(screen.getAllByRole("combobox")[0], { target: { value: "TEAM" } });
        fireEvent.click(screen.getByRole("button", { name: /Host first quiz/ }));
        await waitFor(() => expect(m.api.createGame).toHaveBeenCalledWith("1", "TEAM"));
        expect(m.navigate).toHaveBeenCalled();

        m.api.createGame.mockRejectedValueOnce(new Error("offline"));
        fireEvent.click(screen.getByRole("button", { name: /Host first quiz/ }));
        expect(await screen.findByText(/Failed to create game/)).toBeInTheDocument();
    });

    test("opens the quiz creation form, validates and creates a set", async () => {
        m.admin.quizzes = [];
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: /Create quiz/ }));
        clickTab("Quizzes");
        expect(screen.getByText(/Create your first set/)).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "New set" }));
        fireEvent.click(screen.getByRole("button", { name: "Create" }));
        expect(m.admin.createQuiz).not.toHaveBeenCalled();

        fireEvent.change(screen.getByLabelText("Quiz title"), { target: { value: "New quiz" } });
        fireEvent.change(screen.getByLabelText("Quiz description"), {
            target: { value: "A description" },
        });
        fireEvent.click(screen.getByRole("button", { name: "Create" }));
        await waitFor(() =>
            expect(m.admin.createQuiz).toHaveBeenCalledWith("New quiz", "A description"),
        );

        m.admin.createQuiz.mockRejectedValueOnce(new Error("denied"));
        fireEvent.click(screen.getByRole("button", { name: "New set" }));
        fireEvent.change(screen.getByLabelText("Quiz title"), { target: { value: "Broken" } });
        fireEvent.click(screen.getByRole("button", { name: "Create" }));
        expect(await screen.findByText(/Could not create the quiz: denied/)).toBeInTheDocument();
    });

    test("filters quizzes, pages results and exposes per-quiz actions", async () => {
        m.admin.quizzes = Array.from({ length: 25 }, (_, i) => quiz(String(i + 1)));
        render(<AdminDashboard />);
        clickTab("Quizzes");
        expect(screen.getByRole("button", { name: "Show more (1 remaining)" })).toBeInTheDocument();
        fireEvent.change(screen.getByRole("textbox", { name: "Search quizzes" }), {
            target: { value: "Quiz 25" },
        });
        expect(screen.getByText("Quiz 25")).toBeInTheDocument();
        expect(screen.queryByText("Quiz 1")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "Questions" }));
        expect(m.admin.loadQuestions).toHaveBeenCalledWith("25");
        expect(screen.getByRole("tab", { name: "Questions" })).toBeInTheDocument();

        clickTab("Quizzes");
        fireEvent.click(screen.getByRole("button", { name: "Add" }));
        expect(m.admin.openWizard).toHaveBeenCalledWith("25");

        m.admin.loadQuestions.mockRejectedValueOnce(new Error("offline"));
        fireEvent.click(screen.getByRole("button", { name: "Questions" }));
        expect(await screen.findByText(/Could not load questions/)).toBeInTheDocument();
    });

    test("browses, filters and adds questions", () => {
        m.admin.quizzes = [quiz("1"), quiz("2")];
        render(<AdminDashboard />);
        clickTab("Questions");
        expect(screen.getByText("MULTIPLE SELECT")).toBeInTheDocument();
        fireEvent.change(screen.getByRole("textbox", { name: "Search questions" }), {
            target: { value: "no match" },
        });
        expect(screen.getByText(/No questions match your search/)).toBeInTheDocument();

        fireEvent.change(screen.getByRole("combobox", { name: "Select quiz set" }), {
            target: { value: "2" },
        });
        expect(m.admin.loadQuestions).toHaveBeenCalledWith("2");
        fireEvent.click(screen.getByRole("button", { name: "Add question" }));
        expect(m.admin.openWizard).toHaveBeenCalledWith("1");

        m.admin.activeQuizId = null;
        m.admin.questions = [];
        clickTab("Quizzes");
        clickTab("Questions");
        expect(screen.getByText(/Select a quiz set to view questions/)).toBeInTheDocument();
    });

    test("hosts games from the games tab and pages results", async () => {
        m.admin.quizzes = Array.from({ length: 25 }, (_, i) => quiz(String(i + 1)));
        render(<AdminDashboard />);
        clickTab("Games");
        expect(screen.getByRole("button", { name: "Show more (1 remaining)" })).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText("Mode"), { target: { value: "EXAM" } });
        fireEvent.click(screen.getAllByRole("button", { name: "Host game" })[0]);
        await waitFor(() => expect(m.api.createGame).toHaveBeenCalledWith("1", "EXAM"));
        fireEvent.click(screen.getByRole("button", { name: "Show more (1 remaining)" }));
        expect(screen.getByText("Quiz 25")).toBeInTheDocument();
    });

    test("shows the games empty state when there are no quiz sets", () => {
        m.admin.quizzes = [];
        render(<AdminDashboard />);
        clickTab("Games");
        expect(screen.getByText(/Create a quiz set first/)).toBeInTheDocument();
    });

    test("loads, edits and saves settings, handling failures", async () => {
        render(<AdminDashboard />);
        clickTab("Settings");
        expect(screen.getByText(/Loading settings/)).toBeInTheDocument();
        expect(await screen.findByDisplayValue("dark")).toBeInTheDocument();

        fireEvent.change(screen.getByDisplayValue("dark"), { target: { value: "light" } });
        fireEvent.click(screen.getByRole("button", { name: "Save" }));
        await waitFor(() => expect(m.api.updateSettings).toHaveBeenCalledWith({ theme: "light" }));
        expect(screen.getByText(/Saved/)).toBeInTheDocument();

        m.api.updateSettings.mockRejectedValueOnce(new Error("offline"));
        fireEvent.click(screen.getByRole("button", { name: "Save" }));
        expect(await screen.findByText(/Save failed/)).toBeInTheDocument();
    });

    test("shows settings load errors and the no-settings state", async () => {
        m.api.getSettings.mockRejectedValueOnce(new Error("offline"));
        const first = render(<AdminDashboard />);
        clickTab("Settings");
        expect(await screen.findByText(/Could not load settings/)).toBeInTheDocument();
        first.unmount();

        m.api.getSettings.mockResolvedValue({});
        const { rerender } = render(<AdminDashboard />);
        clickTab("Settings");
        expect(await screen.findByText(/No settings configured/)).toBeInTheDocument();
        rerender(<AdminDashboard />);
    });

    test("serializes object export responses before download", async () => {
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
        m.api.exportBank.mockResolvedValueOnce({ version: "1.0", quizzes: [] });
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: "Export" }));
        await waitFor(() => expect(m.api.exportBank).toHaveBeenCalledOnce());
        const blob = (
            URL.createObjectURL as unknown as { mock: { calls: unknown[][] } }
        ).mock.calls.at(-1)?.[0] as Blob;
        expect(await blob.text()).toContain('"version":"1.0"');
        click.mockRestore();
    });

    test("exports and imports the bank from the header", async () => {
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: "Export" }));
        await waitFor(() => expect(m.api.exportBank).toHaveBeenCalledOnce());
        expect(URL.createObjectURL).toHaveBeenCalled();
        expect(click).toHaveBeenCalled();

        const file = { text: async () => '{"questions":[]}' } as File;
        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        Object.defineProperty(input, "files", { configurable: true, value: [file] });
        fireEvent.change(input);
        await waitFor(() =>
            expect(m.api.importBank).toHaveBeenCalledWith('{"questions":[]}', true),
        );
        expect(m.admin.loadQuizzes).toHaveBeenCalledTimes(2);

        click.mockRestore();
    });

    test("renders the wizard when the store says it is open", () => {
        m.admin.wizardOpen = true;
        render(<AdminDashboard />);
        expect(screen.getByTestId("wizard")).toBeInTheDocument();
    });

    test("clears the saved timer on a second save and retries settings loading", async () => {
        const first = render(<AdminDashboard />);
        clickTab("Settings");
        await screen.findByDisplayValue("dark");
        fireEvent.click(screen.getByRole("button", { name: "Save" }));
        await waitFor(() => expect(screen.getByText("Saved!")).toBeInTheDocument());
        fireEvent.click(screen.getByRole("button", { name: "Save" }));
        await waitFor(() => expect(m.api.updateSettings).toHaveBeenCalledTimes(2));

        first.unmount();
        m.api.getSettings.mockRejectedValueOnce(new Error("offline"));
        const view = render(<AdminDashboard />);
        clickTab("Settings");
        await screen.findByText(/Could not load settings/);
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(screen.getByText(/Could not load settings/)).toBeInTheDocument();
        view.unmount();
    });

    test("reports export and import failures", async () => {
        m.api.exportBank.mockRejectedValueOnce(new Error("offline"));
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: "Export" }));
        expect(await screen.findByText(/Export failed/)).toBeInTheDocument();

        const file = { text: async () => "not json" } as File;
        m.api.importBank.mockRejectedValueOnce(new Error("bad file"));
        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        fireEvent.click(input);
        expect(input).toHaveValue("");
        Object.defineProperty(input, "files", { configurable: true, value: [file] });
        fireEvent.change(input);
        expect(await screen.findByText(/Import failed/)).toBeInTheDocument();
    });

    test("navigates to the player view and browses questions from the overview", () => {
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: "Player view" }));
        expect(m.navigate).toHaveBeenCalledWith({ to: "/" });
        fireEvent.click(screen.getByRole("button", { name: "Browse questions" }));
        expect(screen.getByRole("tab", { name: "Questions" })).toHaveAttribute(
            "aria-selected",
            "true",
        );
    });

    test("retry after a load error handles both 401 and generic failures", async () => {
        m.admin.loadQuizzes.mockRejectedValueOnce(new Error("offline"));
        const first = render(<AdminDashboard />);
        await screen.findByText(/Failed to load quizzes/);
        m.admin.loadQuizzes.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 401 },
        });
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        await screen.findByLabelText("Username");
        first.unmount();

        m.admin.loadQuizzes.mockRejectedValueOnce(new Error("offline"));
        const second = render(<AdminDashboard />);
        await screen.findByText(/Failed to load quizzes/);
        m.admin.loadQuizzes.mockRejectedValueOnce(new Error("still offline"));
        fireEvent.click(screen.getByRole("button", { name: "Retry" }));
        expect(await screen.findByText(/Failed to load quizzes/)).toBeInTheDocument();
        second.unmount();
    });

    test("hosts from the quiz list and loads more results", async () => {
        m.admin.quizzes = Array.from({ length: 25 }, (_, i) => quiz(String(i + 1)));
        render(<AdminDashboard />);
        clickTab("Quizzes");
        fireEvent.change(screen.getByLabelText("Game mode for hosting"), {
            target: { value: "TEAM" },
        });
        fireEvent.click(screen.getAllByRole("button", { name: "Host" })[0]);
        await waitFor(() => expect(m.api.createGame).toHaveBeenCalledWith("1", "TEAM"));
        fireEvent.click(screen.getByRole("button", { name: "Show more (1 remaining)" }));
        expect(screen.getByText("Quiz 25")).toBeInTheDocument();
    });

    test("saved badge expires and non-Error quiz failures use a fallback message", async () => {
        const view = render(<AdminDashboard />);
        clickTab("Settings");
        await screen.findByDisplayValue("dark");
        fireEvent.click(screen.getByRole("button", { name: "Save" }));
        await waitFor(() => expect(screen.getByText("Saved!")).toBeInTheDocument());
        await waitFor(() => expect(screen.queryByText("Saved!")).not.toBeInTheDocument(), {
            timeout: 3000,
        });
        view.unmount();

        m.admin.createQuiz.mockRejectedValueOnce("bad");
        render(<AdminDashboard />);
        fireEvent.click(screen.getByRole("button", { name: /Create quiz/ }));
        clickTab("Quizzes");
        fireEvent.click(screen.getByRole("button", { name: "New set" }));
        fireEvent.change(screen.getByLabelText("Quiz title"), { target: { value: "Broken" } });
        fireEvent.click(screen.getByRole("button", { name: "Create" }));
        expect(
            await screen.findByText(/Could not create the quiz: unknown error/),
        ).toBeInTheDocument();
    });

    test("empty quiz guards in the overview and games search", async () => {
        m.admin.quizzes = [];
        render(<AdminDashboard />);
        const host = screen.getByRole("button", { name: /Host first quiz/ });
        const propsKey = Object.keys(host).find((key) => key.startsWith("__reactProps"));
        (host as any)[propsKey!].onClick();
        expect(m.api.createGame).not.toHaveBeenCalled();

        m.admin.quizzes = [quiz("1")];
        clickTab("Quizzes");
        fireEvent.change(screen.getByRole("textbox", { name: "Search quizzes" }), {
            target: { value: "nothing" },
        });
        clickTab("Games");
        expect(await screen.findByText(/No quiz sets match your search/)).toBeInTheDocument();
    });

    test("question-set selection errors and empty selection are handled", async () => {
        render(<AdminDashboard />);
        clickTab("Questions");
        const select = screen.getByLabelText("Select quiz set");
        fireEvent.change(select, { target: { value: "" } });
        expect(m.admin.loadQuestions).not.toHaveBeenCalled();
        m.admin.loadQuestions.mockRejectedValueOnce(new Error("offline"));
        fireEvent.change(select, { target: { value: "1" } });
        expect(await screen.findByText(/Could not load questions. Try again/)).toBeInTheDocument();
    });
});
