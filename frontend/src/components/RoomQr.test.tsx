import { describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RoomQr } from "./RoomQr";

vi.mock("qrcode.react", () => ({
    QRCodeSVG: ({ value, size, level }: { value: string; size: number; level: string }) => (
        <div data-testid="qr" data-size={size} data-level={level}>
            {value}
        </div>
    ),
}));

describe("RoomQr", () => {
    test("renders a scannable join link at the default size", () => {
        render(<RoomQr pin="1234" />);
        expect(screen.getByRole("img", { name: /1234/ })).toBeInTheDocument();
        const qr = screen.getByTestId("qr");
        expect(qr).toHaveTextContent(`${window.location.origin}/j/1234`);
        expect(qr).toHaveAttribute("data-size", "120");
        expect(qr).toHaveAttribute("data-level", "M");
    });

    test("honors a custom size", () => {
        render(<RoomQr pin="abcd" size={200} />);
        expect(screen.getByTestId("qr")).toHaveAttribute("data-size", "200");
    });
});
