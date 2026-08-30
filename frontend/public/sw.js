/* SprintJudge service worker — instant repeat loads.
 *
 * Strategy:
 *   - /fonts/*            cache-first (immutable content, versioned by URL)
 *   - /assets/*           stale-while-revalidate (hashed build output)
 *   - navigation requests network-first with cached shell fallback (offline)
 * Everything else passes through untouched.
 */
const VERSION = "oq-sw-v3";
const SHELL_CACHE = `${VERSION}-shell`;
const ASSET_CACHE = `${VERSION}-assets`;
const FONT_CACHE = `${VERSION}-fonts`;

self.addEventListener("install", (event) => {
    event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        (async () => {
            const names = await caches.keys();
            await Promise.all(
                names.filter((n) => !n.startsWith(VERSION)).map((n) => caches.delete(n)),
            );
            await self.clients.claim();
        })(),
    );
});

self.addEventListener("fetch", (event) => {
    const req = event.request;
    if (req.method !== "GET") return;
    const url = new URL(req.url);
    if (url.origin !== location.origin) return;

    if (url.pathname.startsWith("/fonts/")) {
        event.respondWith(cacheFirst(req, FONT_CACHE));
        return;
    }
    if (url.pathname.startsWith("/assets/")) {
        event.respondWith(staleWhileRevalidate(req, ASSET_CACHE));
        return;
    }
    if (req.mode === "navigate") {
        event.respondWith(networkFirstShell(req));
        return;
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
    } catch (e) {
        const cache = await caches.open(SHELL_CACHE);
        const cached = await cache.match(req);
        return cached || Response.error();
    }
}
