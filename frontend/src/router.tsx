import {
    Outlet,
    createRootRoute,
    createRoute,
    createRouter,
    lazyRouteComponent,
} from "@tanstack/react-router";
import { LandingView } from "./views/LandingView";
import { JoinView } from "./views/JoinView";
import { SoloView } from "./views/SoloView";
import { ExploreView } from "./views/ExploreView";
import { AdminLoginView } from "./views/AdminLoginView";

function Root() {
    return <Outlet />;
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

const exploreRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/explore",
    component: ExploreView,
});

const playRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/play",
    component: lazyRouteComponent(() => import("./views/QuestionView"), "QuestionView"),
});

const hostRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/host",
    validateSearch: (s: Record<string, unknown>) => ({
        pin: typeof s.pin === "string" ? s.pin : undefined,
        projector: s.projector === true || s.projector === "1" || s.projector === 1,
    }),
    component: lazyRouteComponent(() => import("./views/HostView"), "HostView"),
});

const resultsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/results",
    component: lazyRouteComponent(() => import("./views/ResultView"), "ResultView"),
});

const adminRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin",
    component: lazyRouteComponent(() => import("./views/AdminDashboard"), "AdminDashboard"),
});

// Spring Security's form-login success URL; alias of the dashboard.
const adminDashboardAliasRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/dashboard",
    component: lazyRouteComponent(() => import("./views/AdminDashboard"), "AdminDashboard"),
});

const adminLoginRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/admin/login",
    component: AdminLoginView,
});

const routeTree = rootRoute.addChildren([
    indexRoute,
    joinPinRoute,
    joinRoute,
    soloRoute,
    exploreRoute,
    playRoute,
    hostRoute,
    resultsRoute,
    adminRoute,
    adminDashboardAliasRoute,
    adminLoginRoute,
]);

export const router = createRouter({
    routeTree,
    defaultNotFoundComponent: LandingView,
});

declare module "@tanstack/react-router" {
    interface Register {
        router: typeof router;
    }
}
