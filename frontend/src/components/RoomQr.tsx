import { useI18n } from "../i18n";
import { QRCodeSVG } from "qrcode.react";

/** Scannable join link for the lobby projector / host header. */
export function RoomQr({ pin, size = 120 }: { pin: string; size?: number }) {
    const { t } = useI18n();
    const value = `${window.location.origin}/j/${pin}`;
    return (
        <span className="qr-frame" role="img" aria-label={t("ui.qrLabel", { pin })}>
            <QRCodeSVG value={value} size={size} level="M" />
        </span>
    );
}
