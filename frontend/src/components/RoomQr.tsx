import { QRCodeSVG } from "qrcode.react";

/** Scannable join link for the lobby projector / host header. */
export function RoomQr({ pin, size = 120 }: { pin: string; size?: number }) {
    const value = `${window.location.origin}/j/${pin}`;
    return (
        <span className="qr-frame" role="img" aria-label={`Join link QR code for game ${pin}`}>
            <QRCodeSVG value={value} size={size} level="M" />
        </span>
    );
}
