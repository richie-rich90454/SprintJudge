import { useEffect, useState } from "react";
import axios from "axios";

function isLoopback(host: string): boolean {
    return host === "localhost" || host === "127.0.0.1" || host === "::1" || host === "[::1]";
}

/**
 * Join URL that works on student phones even when the host opened the app on
 * localhost. The server advertises its LAN IPv4 addresses through /api/public/net.
 */
export function useJoinUrl(pin: string): string {
    const [lanBase, setLanBase] = useState<string | null>(null);

    useEffect(() => {
        let alive = true;
        axios
            .get<{ urls?: string[] }>("/api/public/net")
            .then((response) => {
                if (!alive) return;
                const urls = response.data?.urls ?? [];
                if (urls.length > 0) setLanBase(urls[0].replace(/\/$/, ""));
            })
            .catch(() => {
                /* The current origin is still a valid fallback. */
            });
        return () => {
            alive = false;
        };
    }, []);

    const origin = window.location.origin;
    let host = origin;
    try {
        host = new URL(origin).hostname;
    } catch {
        /* keep origin */
    }
    const base = isLoopback(host) ? (lanBase ?? origin) : origin;
    return pin ? `${base}/j/${pin}` : base;
}
