// Belegfluss service worker – scope /app/.
// Caches only the app shell (app HTML, hashed files under /assets/, icons) so the installed app starts fast.
// Never touched: /api (invoices, PDFs, tokens, the live SSE stream), the landing page, anything but GET.
// scripts/stamp-sw.mjs fills in BUILD and ASSETS after `vite build`: every deploy is a new worker ("Neu laden"),
// and all JS/CSS – including lazy-loaded views – is cached on install.
const BUILD = '__BUILD__';
const ASSETS = [/*__ASSETS__*/];
const SHELL = 'belegfluss-shell-' + BUILD;
const APP = '/app/';
const ICONS = ['icon-192.png', 'icon-512.png', 'maskable-512.png'].map((f) => APP + 'icons/' + f);

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL).then((cache) => cache.addAll([APP, ...ASSETS.map((f) => '/assets/' + f), ...ICONS])));
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const name of await caches.keys()) {
      if (name.startsWith('belegfluss-shell-') && name !== SHELL) await caches.delete(name);
    }
    await self.clients.claim();
  })());
});

self.addEventListener('message', (event) => {
  if (event.data === 'skip-waiting') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (req.mode === 'navigate' && (url.pathname === APP || url.pathname === APP + 'index.html')) {
    event.respondWith(networkFirst(req));
  } else if (url.pathname.startsWith('/assets/') || ICONS.includes(url.pathname)) {
    event.respondWith(cacheFirst(req));
  }
  // everything else – above all /api – goes straight to the network, uncached
});

/** The app page: always the newest version when online; offline the cached shell (the app shows "Keine Verbindung"). */
async function networkFirst(req) {
  const cache = await caches.open(SHELL);
  try {
    const res = await fetch(req);
    if (res.ok && (res.headers.get('Content-Type') || '').includes('text/html')) await cache.put(APP, res.clone());
    return res;
  } catch (err) {
    const cached = await cache.match(APP);
    if (cached) return cached;
    throw err;
  }
}

/** Files under /assets/ carry a content hash and never change: cache on first use. */
async function cacheFirst(req) {
  const cache = await caches.open(SHELL);
  const cached = await cache.match(req);
  if (cached) return cached;
  const res = await fetch(req);
  if (res.ok) await cache.put(req, res.clone());
  return res;
}
