import { redirect } from "@tanstack/react-router";
import { adminApi } from "./AdminApiService";

/**
 * SPA-side guard for the /admin routes. Spring Security only sees full page
 * loads, so client-side navigation (typed URL, bookmark) needs its own
 * bounce to the login page — otherwise anonymous users land on the
 * dashboard shell before the first 401 arrives.
 *
 * Fail-closed on auth failures: 401/403 prove the admin session is gone, so
 * bounce to login rather than rendering an anonymous dashboard shell.
 * Transient failures (500, network down) keep a valid admin signed in and
 * surface the error through the admin route's error component.
 */
export async function requireAdmin(): Promise<void> {
    try {
        await adminApi.getSettings();
    } catch (error) {
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status === 401 || status === 403) throw redirect({ to: "/admin/login" });
        // A backend outage must not silently log a valid admin out. Let the
        // admin route's error component show a retry prompt.
        throw new Error("The admin service is unavailable. Reload to try again.");
    }
}
