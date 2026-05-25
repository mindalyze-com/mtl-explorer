import { fileURLToPath, URL } from 'node:url';
import { readFileSync } from 'node:fs';

import { defineConfig, loadEnv } from 'vite';

const { version: APP_PKG_VERSION } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'));
import vue from '@vitejs/plugin-vue';
import { VitePWA } from 'vite-plugin-pwa';

const CHUNK_SIZE_WARNING_LIMIT_KB = 1300;
const NODE_MODULES_MARKER = '/node_modules/';
const GENERATED_API_CLIENT_MARKER = '/mtl-api/mtl-api-typescript-fetch/';
const APP_DISPLAY_NAME = 'MTL Explorer';
const PWA_INSTALL_BACKGROUND_COLOR = '#ffffff';

const VENDOR_CHUNK_RULES: Array<[string, string[]]> = [
  ['vendor-vue', ['@vue/', 'pinia', 'vue', 'vue-router']],
  ['vendor-map', ['@protomaps/', 'd3-array', 'd3-geo', 'd3-selection', 'maplibre-gl', 'pmtiles']],
  ['vendor-primevue', ['@primeuix/', '@primevue/', 'primeicons', 'primevue']],
  ['vendor-charts', ['highcharts', 'highcharts-vue']],
  ['vendor-highlight', ['@highlightjs/', 'highlight.js']],
  ['vendor-data', ['axios', 'colormap', 'date-fns', 'dexie', 'fflate', 'lerp', 'lodash', 'p-limit', 'yocto-queue']],
];

function matchesPackage(id: string, packageName: string): boolean {
  const marker = `${NODE_MODULES_MARKER}${packageName}`;
  if (packageName.endsWith('/')) {
    return id.includes(marker);
  }

  return id.includes(`${marker}/`) || id.includes(`${marker}.`) || id.endsWith(marker);
}

function vendorChunkFor(id: string): string | undefined {
  const normalizedId = id.replaceAll('\\', '/');

  if (normalizedId.includes(GENERATED_API_CLIENT_MARKER)) {
    return 'vendor-api';
  }

  if (!normalizedId.includes(NODE_MODULES_MARKER)) {
    return undefined;
  }

  for (const [chunkName, packageNames] of VENDOR_CHUNK_RULES) {
    if (packageNames.some((packageName) => matchesPackage(normalizedId, packageName))) {
      return chunkName;
    }
  }

  return 'vendor-misc';
}

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
        includeAssets: [
          'favicon.ico',
          'favicon-256.png',
          'apple-touch-icon.png',
          'pwa-192x192.png',
          'pwa-512x512.png',
          'pwa-512x512-maskable.png',
        ],
        manifest: {
          name: APP_DISPLAY_NAME,
          short_name: 'MTL',
          description: 'Track your mountain trails and hikes',
          theme_color: PWA_INSTALL_BACKGROUND_COLOR,
          background_color: PWA_INSTALL_BACKGROUND_COLOR,
          icons: [
            {
              src: 'pwa-192x192.png',
              sizes: '192x192',
              type: 'image/png',
            },
            {
              src: 'pwa-512x512.png',
              sizes: '512x512',
              type: 'image/png',
            },
            {
              src: 'pwa-512x512-maskable.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable',
            },
          ],
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
          ignoreURLParametersMatching: [/^.*$/],
        },
        devOptions: {
          enabled: true,
        },
      }),
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
      chunkSizeWarningLimit: CHUNK_SIZE_WARNING_LIMIT_KB,
      sourcemap: mode !== 'production',
      rollupOptions: {
        output: {
          manualChunks: vendorChunkFor,
        },
      },
    },
  };
});
