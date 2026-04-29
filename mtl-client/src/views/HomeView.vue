<template>
  <div class="home-view">
    <!-- Loading curtain — same background as login, hides map until ready -->
    <Transition name="curtain">
      <div
        v-if="showCurtain"
        class="curtain-wrapper"
        :class="{ 'has-captured-logo-position': capturedLogoTop !== null }"
        :style="curtainStyle"
      >
        <div class="photo-backdrop" :style="{ backgroundImage: `url(${bgImage})` }"></div>
        <div class="photo-vignette"></div>
        <span class="photo-credit">© Patrick Heusser</span>
        <div class="curtain-content">
          <img :src="logoSvg" class="photo-logo" alt="My Trail Log" />
          <div v-if="!loadFailed" class="photo-loader">
            <p class="photo-status">{{ currentSplashMessage }}<span class="photo-dots"></span></p>
            <div class="photo-progress-track"><div class="photo-progress-bar"></div></div>
          </div>
          <div v-else class="curtain-error">
            <p>Unable to load tracks — no server connection and no cached data available.</p>
            <Button label="Retry" icon="bi bi-arrow-clockwise" class="p-button-danger mt-3" @click="retryLoad" />
          </div>
        </div>
      </div>
    </Transition>

    <!-- Map renders behind the curtain, ready when tracks arrive -->
    <div class="map-wrapper">
      <div class="map-host">
        <Map :from-login="fromLogin" @tracks-loaded="onTracksLoaded" @load-failed="onLoadFailed" @syncing="onSyncing"></Map>
      </div>
    </div>

    <!-- Syncing indicator — thin bar at top after curtain is dismissed -->
    <transition name="bar-fade">
      <div v-if="syncing && !showCurtain" class="syncing-bar">
        <div class="syncing-bar-fill"></div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue';
import { computed, onMounted, onUnmounted, ref } from 'vue';
import Map from '@/components/map/Map.vue';
import Button from 'primevue/button';
import { getRandomBackground } from '@/utils/backgrounds';
import { startupLog, startupWarn } from '@/utils/startupDiagnostics';
import logoSvg from '@/assets/logo/logo3/mtl-logo-3_vector.svg';
import { clearSplashLogoTop, consumeSplashLogoTop } from '@/utils/splashLogoPosition';

const MINIMUM_SPLASH_MS = 1500;
const SPLASH_MESSAGE_INTERVAL_MS = 2000;
const FROM_LOGIN_KEY = 'mtl-from-login';
const SPLASH_MESSAGES = [
  'Loading your trails',
  'Preparing map layers',
  'Preparing activity stats',
  'Loading photos',
  'Almost there',
];

const showCurtain = ref(true);
const loadFailed = ref(false);
const syncing = ref(false);
const bgImage = getRandomBackground();
const fromLogin = ref(sessionStorage.getItem(FROM_LOGIN_KEY) === '1');
const capturedLogoTop = ref(fromLogin.value ? consumeSplashLogoTop() : null);
const currentSplashMessageIndex = ref(0);
const currentSplashMessage = computed(() => SPLASH_MESSAGES[currentSplashMessageIndex.value]);
const curtainStyle = computed<CSSProperties | undefined>(() => {
  if (capturedLogoTop.value === null) return undefined;
  return { '--splash-content-top': `${capturedLogoTop.value}px` } as CSSProperties;
});

let curtainShownAt = 0;
let splashMessageTimer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  curtainShownAt = performance.now();
  sessionStorage.removeItem(FROM_LOGIN_KEY);
  if (!fromLogin.value) clearSplashLogoTop();
  startSplashMessages();
  startupLog('curtain', 'Home view mounted; curtain visible', { fromLogin: fromLogin.value });
});

onUnmounted(() => {
  stopSplashMessages();
});

function startSplashMessages() {
  stopSplashMessages();
  currentSplashMessageIndex.value = 0;
  splashMessageTimer = setInterval(() => {
    if (currentSplashMessageIndex.value >= SPLASH_MESSAGES.length - 1) {
      stopSplashMessages();
      return;
    }
    currentSplashMessageIndex.value += 1;
  }, SPLASH_MESSAGE_INTERVAL_MS);
}

function stopSplashMessages() {
  if (!splashMessageTimer) return;
  clearInterval(splashMessageTimer);
  splashMessageTimer = null;
}

function hideCurtain() {
  showCurtain.value = false;
  stopSplashMessages();
}

function onTracksLoaded() {
  const elapsed = performance.now() - curtainShownAt;
  const remaining = Math.max(0, MINIMUM_SPLASH_MS - elapsed);
  startupLog('curtain', 'tracks-loaded received', { elapsedMs: Math.round(elapsed), delayMs: Math.round(remaining) });
  if (remaining <= 0) {
    hideCurtain();
  } else {
    setTimeout(() => {
      hideCurtain();
    }, remaining);
  }
}

function onLoadFailed() {
  startupWarn('curtain', 'load-failed received; showing startup error state');
  loadFailed.value = true;
  stopSplashMessages();
}

function onSyncing(isSyncing: boolean) {
  syncing.value = isSyncing;
}

function retryLoad() {
  startupLog('curtain', 'Retry requested; reloading page');
  location.reload();
}
</script>

<style scoped>
.home-view {
  display: contents;
}
.map-wrapper {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.map-host {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
}

/* ─── Loading curtain ─── */
.curtain-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  height: var(--splash-viewport-height);
  box-sizing: border-box;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
}

.curtain-wrapper.has-captured-logo-position {
  align-items: flex-start;
  padding: var(--splash-content-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
}

.curtain-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: var(--on-photo-text);
  padding: 0 1rem;
  width: min(420px, 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 1.25rem;
}

/* Curtain exit — fade into the map */
.curtain-leave-active {
  transition: opacity 0.6s ease;
}
.curtain-leave-to {
  opacity: 0;
}

/* Accessibility: respect reduced-motion */
@media (prefers-reduced-motion: reduce) {
  .curtain-leave-active {
    transition: none;
  }
}

.mt-3 {
  margin-top: 1rem;
}

.curtain-error {
  margin-top: 1rem;
  font-size: var(--text-base-size);
  color: var(--error);
  line-height: var(--text-base-lh);
}

/* ─── Syncing indicator bar ─── */
.syncing-bar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  z-index: 9000;
  background: var(--accent-subtle);
  overflow: hidden;
  pointer-events: none;
}

.syncing-bar-fill {
  width: 40%;
  height: 100%;
  background: var(--accent-muted);
  animation: syncSlide 1.8s ease-in-out infinite;
}

@keyframes syncSlide {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(350%);
  }
}

.bar-fade-enter-active,
.bar-fade-leave-active {
  transition: opacity 0.3s ease;
}
.bar-fade-enter-from,
.bar-fade-leave-to {
  opacity: 0;
}
</style>
