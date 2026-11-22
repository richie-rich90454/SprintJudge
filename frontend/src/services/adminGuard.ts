import { redirect } from "@tanstack/react-router";
import { adminApi } from "./AdminApiService";

/**
 * SPA-side guard for the /admin routes. Spring Security only sees full page
 * loads, so client-side navigation (typed URL, bookmark) needs its own
 * bounce to the login page — otherwise anonymous users land on the
 * dashboard shell before the first 401 arrives.
 *
 * Fail-closed: ANY probe failure (401, 403, 500, network down) bounces to
 * login. Letting the dashboard render "its own error" left anonymous users
 * on a dead, unauthenticated /admin.
 */
export async function requireAdmin(): Promise<void> {
    try {
        await adminApi.getSettings();
    } catch (error) {
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status === 401 || status === 403) throw redirect({ to: "/admin/login" });
        // A backend outage must not silently log a valid admin out. Let the
        // error boundary show a retry prompt for transient failures.
        throw new Error("The admin service is unavailable. Reload to try again.");
    }
}
