import { useEffect, useLayoutEffect, useRef, useState, type MouseEvent } from "react";
import { RouterProvider } from "@tanstack/react-router";
import { router } from "./router";
import { motionReduced, useUIStore } from "./stores/useUIStore";
import { I18nProvider, LOCALES, useI18n, type Locale } from "./i18n";

const UPDATE_EVENT = "oq-update-available";
const RELOAD_EVENT = "oq-update-accept";

function ensureMainTarget(): HTMLElement | null {
    const main = document.querySelector<HTMLElement>("main");
    if (!main) return null;
    if (!main.id) main.id = "main-content";
    if (!main.hasAttribute("tabindex")) main.setAttribute("tabindex", "-1");
    return main;
}

function focusMain(): HTMLElement | null {
    const main = ensureMainTarget();
    main?.focus({ preventScroll: true });
    return main;
}

/** Cross-cutting a11y: skip link, route focus + announcement, locale, PWA. */
function AppChrome() {
    const { t, locale, setLocale } = useI18n();
    const [announcement, setAnnouncement] = useState("");
    const [updateAvailable, setUpdateAvailable] = useState(false);
    const firstRoute = useRef(true);

    useLayoutEffect(() => {
        // The skip link is live before the first route event; give it a target now.
        ensureMainTarget();
    }, []);

    useEffect(() => {
        const unsubscribe = router.subscribe("onResolved", (event) => {
            if (!event.pathChanged) return;
            const main = focusMain();
            if (!firstRoute.current && main) {
                setAnnouncement(`${t("a11y.route")}: ${event.toLocation.pathname}`);
            }
            firstRoute.current = false;
        });
        return unsubscribe;
    }, [t]);

    useEffect(() => {
        const show = () => setUpdateAvailable(true);
        window.addEventListener(UPDATE_EVENT, show);
        return () => window.removeEventListener(UPDATE_EVENT, show);
    }, []);

    useEffect(() => {
        if (!import.meta.env.PROD || !("serviceWorker" in navigator)) return;
        let refreshing = false;
        let updateAccepted = false;
        const onControllerChange = () => {
            // clients.claim() on first install fires this too: only a user-
            // accepted update may interrupt in-progress input.
            if (!updateAccepted || refreshing) return;
            refreshing = true;
            location.reload();
        };
        navigator.serviceWorker.addEventListener("controllerchange", onControllerChange);

        const accept = () => {
            if (!reg?.waiting) return;
            updateAccepted = true;
            reg.waiting.postMessage({ type: "SKIP_WAITING" });
        };
        let reg: ServiceWorkerRegistration | undefined;
        window.addEventListener(RELOAD_EVENT, accept);

        navigator.serviceWorker
            .register("/sw.js")
            .then((registration) => {
                reg = registration;
                const signal = () => {
                    if (registration.waiting) window.dispatchEvent(new Event(UPDATE_EVENT));
                };
                signal();
                registration.addEventListener("updatefound", () => {
                    const installing = registration.installing;
                    if (!installing) return;
                    installing.addEventListener("statechange", () => {
                        if (
                            installing.state === "installed" &&
                            navigator.serviceWorker.controller
                        ) {
                            signal();
                        }
                    });
                });
            })
            .catch(() => {
                /* offline shell is an enhancement, never a hard dependency */
            });

        return () => {
            navigator.serviceWorker.removeEventListener("controllerchange", onControllerChange);
            window.removeEventListener(RELOAD_EVENT, accept);
        };
    }, []);

    const onSkip = (event: MouseEvent<HTMLAnchorElement>) => {
        event.preventDefault();
        const main = focusMain();
        main?.scrollIntoView();
    };

    return (
        <>
            <a href="#main-content" className="skip-link" onClick={onSkip}>
                {t("a11y.skip")}
            </a>
            <div className="sr-only" role="status" aria-live="polite" aria-atomic="true">
                {announcement}
            </div>
            <RouterProvider router={router} />

            <div className="oq-a11y-tools">
                <label className="sr-only" htmlFor="oq-locale">
                    {t("a11y.language")}
                </label>
                <select
                    id="oq-locale"
                    className="oq-locale-select"
                    value={locale}
                    aria-label={t("a11y.language")}
                    onChange={(event) => setLocale(event.target.value as Locale)}
                >
                    {LOCALES.map((item) => (
                        <option key={item.code} value={item.code}>
                            {item.label}
                        </option>
                    ))}
                </select>
            </div>

            {updateAvailable && (
                <div className="oq-update" role="status" aria-live="polite">
                    <span>{t("pwa.update")}</span>
                    <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={() => window.dispatchEvent(new Event(RELOAD_EVENT))}
                    >
                        {t("pwa.reload")}
                    </button>
                </div>
            )}
        </>
    );
}

export function App() {
    // One class drives every CSS animation, so the in-app toggle and the OS
    // preference agree with the GSAP hooks (which read motionReduced()).
    const motion = useUIStore((s) => s.motion);
    useEffect(() => {
        const sync = () =>
            document.documentElement.classList.toggle("motion-reduced", motionReduced());
        sync();
        const query = window.matchMedia("(prefers-reduced-motion: reduce)");
        query.addEventListener?.("change", sync);
        return () => query.removeEventListener?.("change", sync);
    }, [motion]);
    return (
        <I18nProvider>
            <AppChrome />
        </I18nProvider>
    );
}
