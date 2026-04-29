import { fileURLToPath, URL } from 'node:url';
import { readFileSync } from 'node:fs';

import { defineConfig, loadEnv } from 'vite';

const { version: APP_PKG_VERSION } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'));
import vue from '@vitejs/plugin-vue';
import { VitePWA } from 'vite-plugin-pwa';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // Dev proxy target: override via VITE_DEV_PROXY_TARGET (e.g. http://localhost:8080)
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080';
  return {
  define: {
    __APP_VERSION__: JSON.stringify(new Date().toISOString()),
    __APP_PKG_VERSION__: JSON.stringify(APP_PKG_VERSION),
  },
  plugins: [
    vue(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.ico', 'favicon-256.png', 'apple-touch-icon.png', 'pwa-192x192.png', 'pwa-512x512.png', 'pwa-512x512-maskable.png'],
      manifest: {
        name: 'My Trail Log',
        short_name: 'MTL',
        description: 'Track your mountain trails and hikes',
        theme_color: '#ffffff',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512-maskable.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable'
          }
        ]
      },
      workbox: {
        // clientsClaim removed — with registerType:'prompt', clients.claim() causes a race
        // where the old page briefly runs under the new SW, leading to missing-chunk errors.
        // Without it the new SW only takes control after the reload (navigation), which is safe.
        globPatterns: ['**/*.{js,css,html,ico,png,svg,jpg,jpeg,gif,webp,woff,woff2,ttf,eot}'],
        globIgnores: ['backgrounds/**/*'],
        runtimeCaching: [
          {
            urlPattern: /\/backgrounds\//,
            handler: 'CacheFirst',
            options: {
              cacheName: 'mtl-backgrounds',
              expiration: {
                maxEntries: 30,
                maxAgeSeconds: 60 * 60 * 24 * 365,
              },
            },
          },
        ],
        navigateFallbackDenylist: [/^\/mtl\/api/, /^\/mtl\/v3\//],
        maximumFileSizeToCacheInBytes: 10485760,
        ignoreURLParametersMatching: [/^.*$/]
      },
      devOptions: {
        enabled: true
      }
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    fs: {
      allow: ['.', '../mtl-api/mtl-api-typescript-fetch/target/generated-sources/mtl-typescript'],
    },
    proxy: {
      '/mtl/api': {
        target: devProxyTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
  base: '/mtl/',
  build: {
    // Lowered from 4000 → 1500 to stop masking real bundle bloat.
    // Address by code-splitting (dynamic import of heavy chart/map deps) when warnings appear.
    chunkSizeWarningLimit: 1500,
    sourcemap: true,
  },
  };
});
