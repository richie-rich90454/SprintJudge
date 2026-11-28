import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";

const motionMock = vi.hoisted(() => ({ pulse: vi.fn() }));
vi.mock("../../services/MotionService", () => ({ motion: motionMock }));

import { CircularTimer } from "./CircularTimer";

describe("CircularTimer", () => {
    afterEach(() => vi.restoreAllMocks());

    test("fires onExpire once when the deadline has passed", () => {
        const onExpire = vi.fn();
        render(<CircularTimer endEpochMs={Date.now() - 1000} totalSec={30} onExpire={onExpire} />);
        expect(onExpire).toHaveBeenCalledOnce();
        expect(screen.getByRole("timer")).toHaveTextContent("0");
    });

    test("shows the hurry cue for a low, positive countdown", () => {
        render(<CircularTimer endEpochMs={Date.now() + 5000} totalSec={30} />);
        expect(screen.getByText("Hurry")).toBeInTheDocument();
        expect(motionMock.pulse).toHaveBeenCalled();
    });

    test("handles a zero total without dividing by zero", () => {
        render(<CircularTimer endEpochMs={Date.now() + 30_000} totalSec={0} />);
        expect(screen.getByRole("timer")).toBeInTheDocument();
    });
});
