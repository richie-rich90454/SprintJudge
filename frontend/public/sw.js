/* Offline shell + update flow for the quiz app.
 *
 *   - /fonts/*, /icons/*  cache-first (immutable)
 *   - /assets/*           stale-while-revalidate (hashed build output)
 *   - navigations         network-first, cached shell fallback (offline)
 * A new worker waits for the in-app prompt before taking over.
 */
const VERSION = "oq-sw-v4";
const SHELL_CACHE = `${VERSION}-shell`;
const ASSET_CACHE = `${VERSION}-assets`;
const STATIC_CACHE = `${VERSION}-static`;
const SHELL_URLS = [
    "/",
    "/index.html",
    "/practice",
    "/manifest.webmanifest",
    "/favicon.svg",
    "/fonts/noto-sans-latin-var.woff2",
];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(SHELL_CACHE).then((cache) =>
            Promise.all(SHELL_URLS.map((url) => cache.add(url).catch(() => undefined))),
        ),
    );
});

self.addEventListener("message", (event) => {
    if (event.data && event.data.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches
            .keys()
            .then((names) =>
                Promise.all(names.filter((n) => !n.startsWith(VERSION)).map((n) => caches.delete(n))),
            )
            .then(() => self.clients.claim()),
    );
});

self.addEventListener("fetch", (event) => {
    const req = event.request;
    if (req.method !== "GET") return;
    const url = new URL(req.url);
    if (url.origin !== location.origin) return;

    if (url.pathname.startsWith("/fonts/") || url.pathname.startsWith("/icons/")) {
        event.respondWith(cacheFirst(req, STATIC_CACHE));
    } else if (url.pathname.startsWith("/assets/")) {
        event.respondWith(staleWhileRevalidate(req, ASSET_CACHE));
    } else if (req.mode === "navigate") {
        event.respondWith(networkFirstShell(req));
    }
});

async function cacheFirst(req, cacheName) {
    const cache = await caches.open(cacheName);
    const hit = await cache.match(req);
    if (hit) return hit;
    const res = await fetch(req);
    if (res.ok) cache.put(req, res.clone());
    return res;
}

async function staleWhileRevalidate(req, cacheName) {
    const cache = await caches.open(cacheName);
    const cached = await cache.match(req);
    const network = fetch(req)
        .then((res) => {
            if (res.ok) cache.put(req, res.clone());
            return res;
        })
        .catch(() => undefined);
    return cached || (await network) || Response.error();
}

async function networkFirstShell(req) {
    try {
        const res = await fetch(req);
        if (res.ok) {
            const cache = await caches.open(SHELL_CACHE);
            cache.put(req, res.clone());
        }
        return res;
    } catch {
        const cache = await caches.open(SHELL_CACHE);
        return (await cache.match(req)) || (await cache.match("/index.html")) || (await cache.match("/")) || Response.error();
    }
}
