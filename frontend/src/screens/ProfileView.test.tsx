import type { ReactNode } from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProfileView } from "./ProfileView";

const mock = vi.hoisted(() => ({
    identity: null as any,
    mutation: { isPending: false, isError: false, error: null as any, mutate: vi.fn() },
    game: { playerName: null, playerUuid: null, rejoinToken: null },
    queryClient: { setQueryData: vi.fn() },
}));

vi.mock("@tanstack/react-query", () => ({
    useQueryClient: () => mock.queryClient,
    useQuery: () => ({ data: undefined, isPending: false, isError: false, refetch: vi.fn() }),
    useMutation: () => mock.mutation,
}));

vi.mock("../components/Shell", () => ({
    Shell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("../stores/useGameStore", () => ({
    useGameStore: (selector: (s: any) => unknown) => selector(mock.game),
}));

vi.mock("../services/ProfileApiService", () => ({
    createProfile: vi.fn(),
    fetchProfile: vi.fn(),
    forgetIdentity: vi.fn(),
    storedIdentity: () => mock.identity,
    storeIdentity: vi.fn(),
    updateProfile: vi.fn(),
}));

describe("ProfileView create error", () => {
    beforeEach(() => {
        mock.identity = null;
        mock.mutation = { isPending: false, isError: false, error: null, mutate: vi.fn() };
    });

    test("shows a real Error message", () => {
        mock.mutation = {
            isPending: false,
            isError: true,
            error: new Error("nickname taken"),
            mutate: vi.fn(),
        };
        render(<ProfileView />);

        expect(screen.getByRole("alert")).toHaveTextContent("Could not create a profile: nickname taken");
    });

    test("does not render 'undefined' for non-Error rejections", () => {
        mock.mutation = {
            isPending: false,
            isError: true,
            error: { code: 400 },
            mutate: vi.fn(),
        };
        render(<ProfileView />);

        expect(screen.getByRole("alert")).toHaveTextContent("Could not create a profile: unknown error");
        expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
    });
});
