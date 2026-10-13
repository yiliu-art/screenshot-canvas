// Offline cache for the app shell + Android "Share to Screenshots" inbox.
const CACHE = 'screenshot-canvas-v1';
const SHELL = ['./', './index.html', './manifest.webmanifest',
  './icons/icon-192.png', './icons/icon-512.png', './icons/apple-touch-icon.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

function inboxPut(files) {
  return new Promise((res, rej) => {
    const r = indexedDB.open('sc-inbox', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('files', { autoIncrement: true });
    r.onerror = () => rej(r.error);
    r.onsuccess = () => {
      const t = r.result.transaction('files', 'readwrite');
      files.forEach(f => t.objectStore('files').add(f));
      t.oncomplete = () => res();
      t.onerror = () => rej(t.error);
    };
  });
}

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  // Images shared from the phone's gallery / share sheet (Android)
  if (e.request.method === 'POST' && url.pathname.endsWith('/share')) {
    e.respondWith((async () => {
      const form = await e.request.formData();
      const files = form.getAll('images').filter(f => f && f.size);
      if (files.length) await inboxPut(files);
      return Response.redirect('./?shared=1', 303);
    })());
    return;
  }

  if (e.request.method !== 'GET' || url.origin !== location.origin) return;

  // Network first for the page itself (so updates arrive), cache first for the rest
  if (e.request.mode === 'navigate') {
    e.respondWith(fetch(e.request)
      .then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put('./index.html', copy)); return r; })
      .catch(() => caches.match('./index.html')));
    return;
  }
  e.respondWith(caches.match(e.request).then(hit => hit || fetch(e.request)));
});
