// termOS service worker: app shell offline. API (DeepSeek, GitHub, dll) selalu lewat jaringan.
const CACHE = 'termos-shell-v5';
const LIBS = 'termos-libs-v1'; // library dokumen dari jsDelivr (versi dikunci + diverifikasi SHA-384 di halaman)
const SHELL = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './version.json'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith('termos-shell-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method === 'GET' && url.hostname === 'cdn.jsdelivr.net' && url.pathname.startsWith('/npm/')) {
    // cache-first: library dokumen tetap bisa dipakai offline setelah unduhan pertama
    e.respondWith(caches.open(LIBS).then(async c => (await c.match(e.request)) || fetch(e.request).then(r => { if (r.ok) c.put(e.request, r.clone()); return r; })));
    return;
  }
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // network-first supaya update langsung terpakai, fallback ke cache saat offline
  e.respondWith(
    fetch(e.request)
      .then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); return r; })
      .catch(() => caches.match(e.request).then(r => r || caches.match('./index.html')))
  );
});
