<template>
  <AppNotifications />
  <ReleaseUpdateNotifications
    :browser-update-available="needRefresh && !browserUpdateDeferred"
    :browser-just-updated="browserJustUpdated"
    @reload="reloadBrowser"
    @later="browserUpdateDeferred = true"
  />

  <router-view v-slot="{ Component }">
    <Transition name="route-fade">
      <component :is="Component" />
    </Transition>
  </router-view>
</template>

<script setup lang="ts">
/// <reference types="vite-plugin-pwa/client" />
import { logSanitizedError, warnSanitized, warnRequestFailure } from '@/utils/safeLogging';
import AppNotifications from '@/components/ui/AppNotifications.vue';
import { useToast } from 'primevue/usetoast';
import { onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import ReleaseUpdateNotifications from '@/components/info/ReleaseUpdateNotifications.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import { useRoute } from 'vue-router';
import { useRegisterSW } from 'virtual:pwa-register/vue';
import { getServerBuildInfo } from '@/utils/ServiceHelper';
import { applyServerDefaultLocale } from '@/composables/useLocale';
import { applyServerDefaultMeasurementSystem } from '@/composables/useMeasurementSystem';
import { runConnectivityProbe } from '@/composables/useConnectivityProbe';
import { isAuthenticated } from '@/utils/auth';
import { startServiceWorkerUpdateChecks } from '@/utils/serviceWorkerUpdateChecks';

const PWA_UPDATED_KEY = 'mtl.pwa.just-updated';
const DEV_SERVICE_WORKER_RELOAD_KEY = 'mtl.dev-sw-cleanup-reloaded';

const toast = useToast();
const route = useRoute();
const browserUpdateDeferred = ref(false);
const browserJustUpdated = Boolean(sessionStorage.getItem(PWA_UPDATED_KEY));
const releaseUpdates = useReleaseUpdatesStore();
provide('toast', toast);

onMounted(() => {
  if (import.meta.env.DEV) {
    void cleanupDevServiceWorkers();
  }

  sessionStorage.removeItem(PWA_UPDATED_KEY);

  void applyAuthenticatedServerDefaults();

  // ── Connectivity probe: detect blocked CDNs / network filters ──
  runConnectivityProbe();
});

watch(
  () => route.fullPath,
  () => void applyAuthenticatedServerDefaults()
);

let serverDefaultsApplied = false;

async function applyAuthenticatedServerDefaults() {
  if (serverDefaultsApplied || !isAuthenticated()) return;
  const buildInfo = await getServerBuildInfo().catch((error) => {
    warnRequestFailure('Application build information could not be loaded', error, {
      topic1: 'application',
      topic2: 'App',
      topic3: 'build-info',
    });
    return null;
  });
  if (!buildInfo) return;
  applyServerDefaultLocale(buildInfo.defaultLocale);
  applyServerDefaultMeasurementSystem(buildInfo.defaultMeasurementSystem);
  serverDefaultsApplied = true;
}

async function cleanupDevServiceWorkers() {
  if (!('serviceWorker' in navigator)) {
    sessionStorage.removeItem(DEV_SERVICE_WORKER_RELOAD_KEY);
    return;
  }

  try {
    const registrations = await navigator.serviceWorker.getRegistrations();
    if (registrations.length === 0) {
      sessionStorage.removeItem(DEV_SERVICE_WORKER_RELOAD_KEY);
      return;
    }

    await Promise.all(registrations.map((registration) => registration.unregister()));
    if ('caches' in window) {
      const cacheKeys = await caches.keys();
      await Promise.all(cacheKeys.map((cacheKey) => caches.delete(cacheKey)));
    }

    if (navigator.serviceWorker.controller && !sessionStorage.getItem(DEV_SERVICE_WORKER_RELOAD_KEY)) {
      sessionStorage.setItem(DEV_SERVICE_WORKER_RELOAD_KEY, '1');
      window.location.reload();
      return;
    }

    sessionStorage.removeItem(DEV_SERVICE_WORKER_RELOAD_KEY);
  } catch (error) {
    warnSanitized('[PWA] Dev service worker cleanup failed', error, {
      topic1: 'application',
      topic2: 'App',
      topic3: 'cleanupDevServiceWorkers',
    });
  }
}

let stopServiceWorkerUpdateChecks: (() => void) | undefined;

const { needRefresh, updateServiceWorker } = useRegisterSW({
  onRegistered(r: ServiceWorkerRegistration | undefined) {
    console.log('✅ [PWA] Service Worker successfully registered with scope:', r?.scope);
    stopServiceWorkerUpdateChecks?.();
    stopServiceWorkerUpdateChecks = undefined;
    if (r) {
      stopServiceWorkerUpdateChecks = startServiceWorkerUpdateChecks(r);
    }
  },
  onRegisterError(error: unknown) {
    logSanitizedError('🚨 [PWA Error] Service Worker failed to register:', error, {
      topic1: 'application',
      topic2: 'App',
      topic3: 'onRegisterError',
    });
  },
});

onBeforeUnmount(() => stopServiceWorkerUpdateChecks?.());

watch(needRefresh, (available) => {
  browserUpdateDeferred.value = false;
  releaseUpdates.setBrowserUpdate(available, reloadBrowser);
});
function reloadBrowser() {
  sessionStorage.setItem(PWA_UPDATED_KEY, '1');

  // Listen for the new SW taking control, then reload.
  let reloading = false;
  const doReload = () => {
    if (reloading) return;
    reloading = true;
    window.location.reload();
  };
  navigator.serviceWorker?.addEventListener('controllerchange', doReload);
  // Reload only after the user's explicit action.
  setTimeout(doReload, 2000);
  updateServiceWorker(true);
}
</script>

<style>
/* Route crossfade — both views overlap so the shared background stays visible */
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.5s ease;
}
.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
}
.route-fade-enter-active,
.route-fade-leave-active {
  position: absolute;
  inset: 0;
}

.route-fade-leave-active {
  pointer-events: none;
}

@media (prefers-reduced-motion: reduce) {
  .route-fade-enter-active,
  .route-fade-leave-active {
    transition: none;
  }
}
</style>
