import {
    Outlet,
    createRootRoute,
    createRoute,
    createRouter,
    lazyRouteComponent,
    useRouterState,
    type ErrorComponentProps,
} from "@tanstack/react-router";
import { LandingView } from "./screens/LandingView";
import { JoinView } from "./screens/JoinView";
import { SoloView } from "./screens/SoloView";
import { ExploreView } from "./screens/ExploreView";
import { AdminLoginView } from "./screens/AdminLoginView";
import { requireAdmin } from "./services/adminGuard";

function Root() {
    const pathname = useRouterState({ select: (s) => s.location.pathname });
    return (
        <div key={pathname} className="route-enter">
            <Outlet />
        </div>
    );
}

/** Renders guard failures (e.g. a backend outage) instead of an unhandled beforeLoad error. */
export function AdminRouteError({ error, reset }: ErrorComponentProps) {
    return (
        <div className="page-shell py-10" role="alert">
            <h1 className="text-2xl font-extrabold">Admin service unavailable</h1>
            <p className="mt-3 text-[var(--oq-ink-soft)]">
                {error instanceof Error
                    ? error.message
                    : "The admin service is unavailable. Reload to try again."}
            </p>
            <button type="button" className="btn btn-primary btn-sm mt-4" onClick={reset}>
                Retry
            </button>
        </div>
    );
}

const rootRoute = createRootRoute({ component: Root });

const indexRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/",
    component: LandingView,
});

const joinPinRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/j/$pin",
    component: JoinView,
});

const joinRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/join",
    component: JoinView,
});

const soloRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/solo",
    component: SoloView,
});

const practiceRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/practice",
    component: lazyRouteComponent(() => import("./screens/PracticeView"), "PracticeView"),
});

const exploreRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/explore",
    component: ExploreView,
});

const playRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/play",
    component: lazyRouteComponent(() => import("./screens/QuestionView"), "QuestionView"),
});

const hostRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/host",
    validateSearch: (s: Record<string, unknown>) => ({
        pin:
            typeof s.pin === "string"
                ? s.pin
                : typeof s.pin === "number"
                  ? String(s.pin)
                  : undefined,
        projector: s.projector === true || s.projector === "1" || s.projector === 1,
    }),
    component: lazyRouteComponent(() => import("./screens/HostView"), "HostView"),
});

const resultsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/results",
    component: lazyRouteComponent(() => import("./screens/ResultView"), "ResultView"),
});

const watchRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/watch/$pin",
    component: lazyRouteComponent(() => import("./screens/SpectatorView"), "SpectatorView"),
});

const adminRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin",
    beforeLoad: requireAdmin,
    errorComponent: AdminRouteError,
    component: lazyRouteComponent(() => import("./screens/AdminDashboard"), "AdminDashboard"),
});

// Spring Security's form-login success URL; alias of the dashboard.
const adminDashboardAliasRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/dashboard",
    beforeLoad: requireAdmin,
    errorComponent: AdminRouteError,
    component: lazyRouteComponent(() => import("./screens/AdminDashboard"), "AdminDashboard"),
});

const adminLoginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/login",
    component: AdminLoginView,
});

const bankRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/bank",
    beforeLoad: requireAdmin,
    errorComponent: AdminRouteError,
    component: lazyRouteComponent(() => import("./screens/BankBrowser"), "BankBrowser"),
});

const volumeRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/volume",
    beforeLoad: requireAdmin,
    errorComponent: AdminRouteError,
    component: lazyRouteComponent(() => import("./screens/VolumeReport"), "VolumeReport"),
});

const assignmentsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/assignments",
    beforeLoad: requireAdmin,
    errorComponent: AdminRouteError,
    component: lazyRouteComponent(() => import("./screens/AssignmentView"), "AssignmentView"),
});

const analyticsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/analytics",
    beforeLoad: requireAdmin,
    errorComponent: AdminRouteError,
    component: lazyRouteComponent(() => import("./screens/AnalyticsView"), "AnalyticsView"),
});

const profileRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/profile",
    component: lazyRouteComponent(() => import("./screens/ProfileView"), "ProfileView"),
});

const routeTree = rootRoute.addChildren([
    indexRoute,
    joinPinRoute,
    joinRoute,
    soloRoute,
    practiceRoute,
    exploreRoute,
    playRoute,
    hostRoute,
    resultsRoute,
    watchRoute,
    adminRoute,
    adminDashboardAliasRoute,
    adminLoginRoute,
    bankRoute,
    volumeRoute,
    assignmentsRoute,
    analyticsRoute,
    profileRoute,
]);

export const router = createRouter({
    routeTree,
    defaultNotFoundComponent: LandingView,
    parseSearch: (search) => Object.fromEntries(new URLSearchParams(search)),
    stringifySearch: (search) => {
        const params = new URLSearchParams();
        for (const [key, value] of Object.entries(search)) {
            if (value === undefined || value === null) continue;
            params.set(key, typeof value === "boolean" ? (value ? "1" : "0") : String(value));
        }
        const query = params.toString();
        return query ? `?${query}` : "";
    },
});

declare module "@tanstack/react-router" {
    interface Register {
        router: typeof router;
    }
}
