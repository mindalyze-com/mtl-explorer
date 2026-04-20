<template>
  <div class="home-view">
  <!-- Loading curtain — same background as login, hides map until ready -->
  <Transition name="curtain">
    <div v-if="showCurtain" class="curtain-wrapper">
      <div class="photo-backdrop" :style="{ backgroundImage: `url(${bgImage})` }"></div>
      <div class="photo-vignette"></div>
      <span class="photo-credit">© Patrick Heusser</span>
      <div class="curtain-content">
        <h1 class="curtain-title">My Trail Log</h1>
        <div v-if="!loadFailed">
          <p class="curtain-status">Loading your trails<span class="dots"></span></p>
          <div class="progress-track"><div class="progress-bar"></div></div>
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
      <Map @tracks-loaded="onTracksLoaded" @load-failed="onLoadFailed" @syncing="onSyncing"></Map>
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
import { onMounted, ref } from 'vue';
import Map from "@/components/map/Map.vue";
import Button from "primevue/button";
import { getRandomBackground } from '@/utils/backgrounds';
import { startupLog, startupWarn } from '@/utils/startupDiagnostics';

const MINIMUM_SPLASH_MS = 1500;

const showCurtain = ref(true);
const loadFailed = ref(false);
const syncing = ref(false);
const bgImage = getRandomBackground();

let curtainShownAt = 0;

onMounted(() => {
  curtainShownAt = performance.now();
  startupLog('curtain', 'Home view mounted; curtain visible');
});

function onTracksLoaded() {
  const elapsed = performance.now() - curtainShownAt;
  const remaining = Math.max(0, MINIMUM_SPLASH_MS - elapsed);
  startupLog('curtain', 'tracks-loaded received', { elapsedMs: Math.round(elapsed), delayMs: Math.round(remaining) });
  if (remaining <= 0) {
    showCurtain.value = false;
  } else {
    setTimeout(() => { showCurtain.value = false; }, remaining);
  }
}

function onLoadFailed() {
  startupWarn('curtain', 'load-failed received; showing startup error state');
  loadFailed.value = true;
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
.home-view { display: contents; }
.map-wrapper { display:flex; flex:1 1 auto; min-height:0; width:100%; height:100%; overflow:hidden; }
.map-host    { display:flex; flex:1 1 auto; min-height:0; height:100%; }

/* ─── Loading curtain ─── */
.curtain-wrapper {
  position: fixed;
  top: 0; left: 0;
  width: 100%; height: 100%;
  height: 100dvh;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
}

.curtain-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: var(--on-photo-text);
  padding: 0 1rem;
  animation: gentleFloat 4s ease-in-out infinite;
}

.curtain-title {
  font-size: clamp(2rem, 8vw, 4rem);
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--on-photo-text);
  text-shadow: var(--on-photo-text-shadow);
  margin: 0 0 1.5rem 0;
}

.curtain-status {
  font-size: 0.85rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--on-photo-text-muted);
  margin: 0;
  animation: breathe 3s ease-in-out infinite;
}

/* Subtle indeterminate progress bar */
.progress-track {
  width: min(220px, 60vw);
  height: 2px;
  margin: 1.2rem auto 0;
  border-radius: 1px;
  background: var(--on-photo-surface);
  overflow: hidden;
}

.progress-bar {
  width: 40%;
  height: 100%;
  border-radius: 1px;
  background: var(--on-photo-surface-lo);
  animation: progressSlide 1.8s ease-in-out infinite;
}

/* CSS-only animated ellipsis */
.dots::after {
  content: '';
  display: inline-block;
  width: 1.2em;
  text-align: left;
  animation: dots 1.5s steps(3, end) infinite;
}

/* ─── Keyframes ─── */

@keyframes gentleFloat {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-4px); }
}

@keyframes breathe {
  0%, 100% { opacity: 0.6; }
  50%      { opacity: 0.85; }
}

@keyframes dots {
  0%   { content: '.'; }
  33%  { content: '..'; }
  66%  { content: '...'; }
  100% { content: ''; }
}

@keyframes progressSlide {
  0%   { transform: translateX(-100%); }
  100% { transform: translateX(250%); }
}

/* Curtain exit — fade into the map */
.curtain-leave-active {
  transition: opacity 0.6s ease;
}
.curtain-leave-to {
  opacity: 0;
}

/* Landscape phones */
@media (max-height: 500px) and (orientation: landscape) {
  .curtain-title {
    font-size: clamp(1.5rem, 5vw, 2.5rem);
    margin-bottom: 1rem;
  }
}

/* Accessibility: respect reduced-motion */
@media (prefers-reduced-motion: reduce) {
  .curtain-content  { animation: none; }
  .curtain-status   { animation: none; }
  .dots::after      { animation: none; content: '...'; }
  .progress-bar     { animation: none; width: 100%; }
  .curtain-leave-active { transition: none; }
}

.mt-3 {
  margin-top: 1rem;
}

.curtain-error {
  margin-top: 1rem;
  font-size: 0.95rem;
  color: var(--error);
  line-height: 1.4;
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
  0%   { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
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
