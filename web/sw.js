// Service Worker básico para caché (Stale-while-revalidate)
const CACHE_NAME = 'anitail-v4';
const ASSETS_TO_CACHE = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './locales/es.json',
  './locales/en.json',
  './locales/it.json',
  './locales/fr.json',
  './assets/icon.png',
  './assets/icon.png',
  './assets/now-playing.png',
  './assets/quick-picks.png',
  './assets/search.png',
  './assets/library.png',
  './assets/player-audio.png',
  './assets/content-settings.png',
  './assets/account.png',
  './assets/contributors.png',
  './assets/pc/1.jpeg',
  './assets/pc/2.jpeg',
  './assets/pc/3.jpeg',
  './assets/pc/4.jpeg',
  './assets/pc/5.jpeg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    })
  );
});

self.addEventListener('fetch', (event) => {
  // Estrategia: stale-while-revalidate para recursos estáticos
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request).catch(() => caches.match('./index.html'))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      const fetchPromise = fetch(event.request).then((networkResponse) => {
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, networkResponse.clone());
        });
        return networkResponse;
      });
      return cachedResponse || fetchPromise;
    })
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheName !== CACHE_NAME) {
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
});
