import axios from "axios";
import { redirect } from "@tanstack/react-router";
import { adminApi } from "./AdminApiService";

/**
 * SPA-side guard for the /admin routes. Spring Security only sees full page
 * loads, so client-side navigation (Solo page link, typed URL, bookmark)
 * needs its own bounce to the login page — otherwise anonymous users land
 * on the dashboard shell before the first 401 arrives.
 */
export async function requireAdmin(): Promise<void> {
    try {
        await adminApi.getSettings();
    } catch (e: unknown) {
        if (axios.isAxiosError(e) && (e.response?.status === 401 || e.response?.status === 403)) {
            throw redirect({ to: "/admin/login" });
        }
    }
}
