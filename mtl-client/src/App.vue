<template>
  <!-- placeholder for standard toasts -->
  <Toast position="top-center"></Toast>

  <!-- PWA update toast (post-reload notification only) -->
  <Toast position="top-center" group="pwa"></Toast>

  <router-view v-slot="{ Component }">
    <Transition name="route-fade">
      <component :is="Component" />
    </Transition>
  </router-view>
</template>

<script setup lang="ts">
/// <reference types="vite-plugin-pwa/client" />
import Toast from 'primevue/toast';
import { useToast } from 'primevue/usetoast';
import { onBeforeUnmount, onMounted, provide, watch } from 'vue';
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
provide('toast', toast);

onMounted(() => {
  if (import.meta.env.DEV) {
    void cleanupDevServiceWorkers();
  }

  // Show post-reload "updated" toast if we just auto-updated
  if (sessionStorage.getItem(PWA_UPDATED_KEY)) {
    sessionStorage.removeItem(PWA_UPDATED_KEY);
    toast.add({
      severity: 'success',
      summary: 'App Updated',
      detail: 'MTL Explorer was updated to the latest version.',
      life: 5000,
      group: 'pwa',
    });
  }

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
  const buildInfo = await getServerBuildInfo().catch(() => null);
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
    console.warn('[PWA] Dev service worker cleanup failed', error);
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
    console.error('🚨 [PWA Error] Service Worker failed to register:', error);
  },
});

onBeforeUnmount(() => stopServiceWorkerUpdateChecks?.());

watch(needRefresh, (isNeeded) => {
  if (isNeeded) {
    console.log('[PWA] New version detected — auto-updating…');
    sessionStorage.setItem(PWA_UPDATED_KEY, '1');

    // Listen for the new SW taking control, then reload.
    let reloading = false;
    const doReload = () => {
      if (reloading) return;
      reloading = true;
      window.location.reload();
    };
    navigator.serviceWorker?.addEventListener('controllerchange', doReload);
    // Safety-net: reload after 2 s even if controllerchange doesn't fire
    setTimeout(doReload, 2000);
    updateServiceWorker(true);
  }
});
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
