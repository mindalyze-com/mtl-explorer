<template>
  <div class="login-wrapper" :class="{ 'is-departing': isDeparting }">
    <div class="photo-backdrop" :style="{ backgroundImage: `url(${bgImage})` }"></div>
    <div class="photo-vignette"></div>
    <Transition name="notice-fade">
      <div v-if="noticeMessage" class="login-notice" :class="`login-notice-${noticeSeverity}`">
        <Message :severity="noticeSeverity" :closable="false">{{ noticeMessage }}</Message>
      </div>
    </Transition>
    <span class="photo-credit">© Patrick Heusser</span>
    <button class="legal-trigger" aria-label="About & license" @click="showLegal = !showLegal">AGPL-3.0</button>
    <Transition name="legal-fade">
      <div v-if="showLegal" class="legal-backdrop" @click="showLegal = false">
        <div class="legal-panel" @click.stop>
          <button class="legal-close" aria-label="Close" @click="showLegal = false">✕</button>
          <p class="legal-line">Photo &amp; app © <strong>Patrick Heusser</strong></p>
          <button
            class="legal-about-link"
            @click="
              showLegal = false;
              showAbout = true;
            "
          >
            AGPL-3.0 · About &amp; Source
          </button>
        </div>
      </div>
    </Transition>
    <Transition name="about-fade">
      <div v-if="showAbout" class="about-overlay-backdrop" @click="showAbout = false">
        <div class="about-overlay-panel" @click.stop>
          <button class="about-overlay-close" aria-label="Close" @click="showAbout = false">✕</button>
          <div class="about-overlay-header">
            <p class="about-overlay-kicker">About &amp; Source</p>
            <h2 class="about-overlay-title">MyTrailLog</h2>
            <p class="about-overlay-version">Version {{ version }}</p>
          </div>
          <div class="about-overlay-chips" aria-label="License summary">
            <span class="about-overlay-chip">AGPL-3.0-or-later</span>
            <span class="about-overlay-chip">Commercial license available</span>
          </div>
          <div class="about-overlay-divider"></div>
          <div class="about-overlay-body">
            <p class="about-overlay-text">
              MyTrailLog is dual-licensed under
              <a href="https://www.gnu.org/licenses/agpl-3.0.html" target="_blank" rel="noopener">AGPL-3.0-or-later</a>
              and a separate commercial license.
            </p>
            <p class="about-overlay-text">
              If you modify the software and make it available over a network, you must offer the corresponding source
              code of that running version.
            </p>
            <div class="about-overlay-section">
              <p class="about-overlay-label">Source code</p>
              <p class="about-overlay-text">
                <a :href="sourceUrl" target="_blank" rel="noopener">{{ sourceUrl }}</a>
              </p>
            </div>
            <div class="about-overlay-section">
              <p class="about-overlay-label">Commercial inquiries</p>
              <p class="about-overlay-text">
                <a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
              </p>
            </div>
            <p class="about-overlay-text about-overlay-copyright">© 2020-2026 Patrick Heusser &amp; contributors</p>
          </div>
        </div>
      </div>
    </Transition>
    <div class="login-container" :class="{ 'is-departing': isDeparting }">
      <div class="mac-login-panel">
        <img ref="logoEl" :src="logoSvg" class="photo-logo" alt="My Trail Log" />

        <div class="login-stage" :class="{ 'is-authenticating': isAuthenticating }">
          <div class="login-controls" :aria-hidden="isAuthenticating">
            <div v-if="demoMode" class="demo-hint">
              <span class="demo-badge">DEMO</span>
              <span
                >User: <strong>{{ demoUsername }}</strong> &nbsp;/&nbsp; Password:
                <strong>{{ demoPassword }}</strong></span
              >
            </div>

            <form class="form-container" @submit.prevent="handleLogin">
              <InputText
                id="username"
                v-model="username"
                type="text"
                placeholder="Username"
                class="mac-input"
                autocomplete="username"
              />

              <InputText
                id="password"
                v-model="password"
                type="password"
                placeholder="Password"
                class="mac-input"
                autocomplete="current-password"
              />

              <Button type="submit" label="Sign In" class="mac-button" :loading="loading" />
            </form>
          </div>

          <Transition name="login-loader-fade">
            <div v-if="isAuthenticating" class="login-departure-loader photo-loader" aria-live="polite">
              <p class="photo-status">{{ loginStageMessage }}<span class="photo-dots"></span></p>
              <div class="photo-progress-track"><div class="photo-progress-bar"></div></div>
            </div>
          </Transition>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import logoSvg from '@/assets/logo/logo3/mtl-logo-3_vector.svg';
import { useRouter, useRoute } from 'vue-router';
import axios from 'axios';
import { setToken } from '@/utils/auth';
import { submitClientEnvironmentOnce } from '@/utils/clientEnvironmentAnalytics';
import { getRandomBackground } from '@/utils/backgrounds';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { getDemoStatus } from '@/utils/ServiceHelper';
import { describeError, startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import { clearSplashLogoTop, saveSplashLogoTop } from '@/utils/splashLogoPosition';

const router = useRouter();
const route = useRoute();
const username = ref('');
const password = ref('');
const logoEl = ref<HTMLImageElement | null>(null);
const noticeMessage = ref('');
const noticeSeverity = ref<'warn' | 'error'>('warn');
const loading = ref(false);
const isDeparting = ref(false);
const bgImage = getRandomBackground();
const demoMode = ref(false);
const demoUsername = ref('');
const demoPassword = ref('');
const showLegal = ref(false);
const showAbout = ref(false);
const version = computed<string>(() => (import.meta.env.VITE_APP_VERSION as string) || 'dev');
const isAuthenticating = computed(() => loading.value || isDeparting.value);
const loginStageMessage = computed(() => (isDeparting.value ? 'Loading your trails' : 'Signing in'));
const sourceUrl = 'https://github.com/mindalyze-com/mtl-explorer';
const contactEmail = 'hey.lueg@gmail.com';
let noticeTimeout: ReturnType<typeof setTimeout> | null = null;

function clearNotice() {
  if (noticeTimeout) {
    clearTimeout(noticeTimeout);
    noticeTimeout = null;
  }
  noticeMessage.value = '';
}

function showNotice(message: string, severity: 'warn' | 'error' = 'error') {
  clearNotice();
  noticeMessage.value = message;
  noticeSeverity.value = severity;
  noticeTimeout = setTimeout(() => {
    noticeMessage.value = '';
    noticeTimeout = null;
  }, 3000);
}

function saveCurrentSplashLogoTop() {
  const logo = logoEl.value;
  if (!logo) return;
  saveSplashLogoTop(logo.getBoundingClientRect().top);
}

onMounted(async () => {
  startupLog('login', 'Login view mounted', { reason: route.query.reason ?? null });
  if (route.query.reason === 'expired') {
    showNotice('Session expired. Sign in again.', 'warn');
  }
  const demo = await getDemoStatus();
  if (demo.demoMode) {
    demoMode.value = true;
    demoUsername.value = demo.username ?? '';
    demoPassword.value = demo.password ?? '';
    username.value = demo.username ?? '';
    password.value = demo.password ?? '';
  }
});

onUnmounted(() => {
  clearNotice();
});

const backendUrl = import.meta.env.VITE_BACKEND_URL;

async function handleLogin() {
  if (!username.value || !password.value) {
    showNotice('Enter username and password.');
    return;
  }

  clearSplashLogoTop();

  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur();
  }

  loading.value = true;
  isDeparting.value = false;
  clearNotice();
  const loginTimer = startStartupTimer('login', 'Submitting login request');

  try {
    const response = await axios.post(`${backendUrl}api/auth/login`, {
      username: username.value,
      password: password.value,
    });

    if (response.data && response.data.token) {
      loginTimer.success('Login accepted by server');
      setToken(response.data.token);
      submitClientEnvironmentOnce();
      sessionStorage.setItem('mtl-from-login', '1');
      // Warm the map-config request before navigating; track data is loaded
      // by Map.vue in incremental batches so the first page can render early.
      fetchMapConfig();
      startupLog('prefetch', 'Track loading deferred to map view for incremental batches');
      startupLog('login', 'Navigating to home after login');
      saveCurrentSplashLogoTop();
      isDeparting.value = true;
      await new Promise((resolve) => setTimeout(resolve, 360));
      router.push('/');
    } else {
      loginTimer.warn('Login response did not contain a token');
      showNotice('Login failed. Invalid response.');
    }
  } catch (error) {
    loginTimer.error('Login request failed', describeError(error));
    if (axios.isAxiosError(error)) {
      if (!error.response) {
        showNotice('Network error. Unable to reach the server.');
      } else if (error.response.status === 401 || error.response.status === 403) {
        showNotice('Invalid username or password.');
      } else {
        showNotice(`Login failed (${error.response.status}).`);
      }
    } else {
      showNotice('An unexpected error occurred.');
    }
    console.error('Login error', error);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrapper {
  position: relative;
  min-height: 100vh;
  min-height: var(--splash-viewport-height);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
}

.login-notice {
  position: fixed;
  top: calc(env(safe-area-inset-top) + 1rem);
  left: 50%;
  transform: translateX(-50%);
  width: min(420px, calc(100vw - 2rem));
  z-index: 30;
}

.login-notice :deep(.p-message) {
  margin: 0;
  border-radius: 14px;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.18);
}

.login-notice-warn :deep(.p-message) {
  background: rgba(255, 196, 91, 0.14);
  border: 1px solid rgba(255, 213, 128, 0.28);
  color: rgba(255, 248, 225, 0.96);
}

.login-notice-warn :deep(.p-message .p-message-text) {
  color: rgba(255, 248, 225, 0.96);
  font-weight: 500;
  font-size: var(--text-sm-size);
  line-height: 1.3;
}

.login-notice-warn :deep(.p-message .p-message-icon) {
  color: rgba(255, 221, 140, 0.92);
}

.login-notice-error :deep(.p-message) {
  background-color: rgba(207, 47, 47, 0.84);
  border: 1px solid rgba(255, 255, 255, 0.16);
  color: #fff4f4;
}

.login-notice-error :deep(.p-message .p-message-text),
.login-notice-error :deep(.p-message .p-message-icon) {
  color: #fff4f4;
}

.login-container {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 420px;
  padding: 1rem;
}

.mac-login-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 1.25rem;
}

.login-stage {
  position: relative;
  width: 100%;
  max-width: 320px;
  min-height: 8.75rem;
}

.login-controls {
  width: 100%;
  transition:
    opacity 0.24s ease,
    transform 0.24s ease,
    filter 0.24s ease;
}

.login-container.is-departing {
  pointer-events: none;
}

.login-stage.is-authenticating .login-controls {
  opacity: 0;
  transform: translateY(8px);
  filter: blur(3px);
  pointer-events: none;
}

.login-departure-loader {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding-top: 0;
}

.login-loader-fade-enter-active,
.login-loader-fade-leave-active {
  transition:
    opacity 0.24s ease,
    filter 0.24s ease;
}

.login-loader-fade-enter-from,
.login-loader-fade-leave-to {
  opacity: 0;
  filter: blur(3px);
}

.form-container {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
  align-items: center;
}

/* Prevent iOS zoom on input focus */
.form-container :deep(input) {
  font-size: var(--text-base-size) !important;
}

/* PrimeVue input override — underline-only, photo shows through */
:deep(input.mac-input),
:deep(.mac-input.p-inputtext) {
  width: 100%;
  border-radius: 14px !important;
  background: rgba(255, 255, 255, 0.11) !important;
  background-color: rgba(255, 255, 255, 0.11) !important;
  border: 1px solid rgba(255, 255, 255, 0.14) !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3) !important;
  color: var(--on-photo-text-strong) !important;
  text-shadow:
    0 1px 3px rgba(0, 0, 0, 0.7),
    0 3px 8px rgba(0, 0, 0, 0.35) !important;
  backdrop-filter: blur(4px) saturate(120%) !important;
  -webkit-backdrop-filter: blur(4px) saturate(120%) !important;
  padding: 0.72rem 0.95rem !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 10px 24px rgba(0, 0, 0, 0.1) !important;
  transition:
    background-color 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

:deep(input.mac-input:focus),
:deep(.mac-input.p-inputtext:focus) {
  background-color: rgba(255, 255, 255, 0.11) !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
  border-bottom-color: rgba(255, 255, 255, 0.34) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 12px 28px rgba(0, 0, 0, 0.14) !important;
  outline: none;
}

:deep(input.mac-input::placeholder),
:deep(.mac-input.p-inputtext::placeholder) {
  color: rgba(255, 255, 255, 0.58) !important;
  text-shadow:
    0 1px 3px rgba(0, 0, 0, 0.55),
    0 3px 8px rgba(0, 0, 0, 0.35) !important;
  opacity: 1 !important;
}

:deep(input.mac-input:-webkit-autofill),
:deep(input.mac-input:-webkit-autofill:hover),
:deep(input.mac-input:-webkit-autofill:focus),
:deep(.mac-input.p-inputtext:-webkit-autofill),
:deep(.mac-input.p-inputtext:-webkit-autofill:hover),
:deep(.mac-input.p-inputtext:-webkit-autofill:focus) {
  -webkit-text-fill-color: var(--on-photo-text-strong) !important;
  caret-color: var(--on-photo-text-strong) !important;
  border-color: rgba(255, 255, 255, 0.16) !important;
  border-bottom-color: rgba(255, 255, 255, 0.3) !important;
  -webkit-box-shadow:
    inset 0 0 0 1000px rgba(255, 255, 255, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 10px 24px rgba(0, 0, 0, 0.1) !important;
  box-shadow:
    inset 0 0 0 1000px rgba(255, 255, 255, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 10px 24px rgba(0, 0, 0, 0.1) !important;
  transition: background-color 9999s ease-out 0s !important;
}

:deep(.mac-button) {
  width: 100%;
  border-radius: 20px !important;
  background-color: rgba(255, 255, 255, 0.16) !important;
  border: 1px solid rgba(255, 255, 255, 0.24) !important;
  color: #ffffff !important;
  font-weight: 650 !important;
  padding: 0.75rem 1rem !important;
  margin-top: 0.5rem;
  backdrop-filter: blur(12px) saturate(120%);
  -webkit-backdrop-filter: blur(12px) saturate(120%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.12),
    0 10px 28px rgba(0, 0, 0, 0.26) !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
  transition: all 0.25s ease;
}

:deep(.mac-button:hover) {
  background-color: rgba(255, 255, 255, 0.22) !important;
  border-color: rgba(255, 255, 255, 0.32) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.14),
    0 12px 32px rgba(0, 0, 0, 0.3) !important;
  transform: translateY(-1px);
}

:deep(.mac-button .p-button-label) {
  color: #ffffff !important;
  letter-spacing: 0.01em;
}

.notice-fade-enter-active,
.notice-fade-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.notice-fade-enter-from,
.notice-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -6px);
}

/* Accessibility: respect reduced-motion */
@media (prefers-reduced-motion: reduce) {
  .login-controls {
    transition: none;
  }
}

/* Demo-mode hint banner */
.demo-hint {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--on-photo-surface-hi);
  backdrop-filter: var(--blur-light);
  -webkit-backdrop-filter: var(--blur-light);
  border: 1px solid var(--on-photo-border-hi);
  border-radius: 12px;
  padding: 0.55rem 1rem;
  margin-bottom: 1.5rem;
  color: var(--on-photo-text-strong);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.demo-badge {
  display: inline-block;
  background: rgba(255, 180, 60, 0.85);
  color: var(--on-photo-button-text);
  font-weight: 700;
  font-size: var(--text-xs-size);
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

/* AGPL-3.0 trigger — bottom-left, minimal */
.legal-trigger {
  position: absolute;
  bottom: calc(env(safe-area-inset-bottom) + 0.5rem);
  left: 0.75rem;
  z-index: 11;
  background: none;
  border: none;
  cursor: pointer;
  font-size: var(--text-xs-size);
  color: rgba(255, 255, 255, 0.35);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.7);
  padding: 0.2rem 0.4rem;
  letter-spacing: 0.04em;
  transition: color 0.2s ease;
}

.legal-trigger:hover {
  color: rgba(255, 255, 255, 0.58);
}

/* Full-screen transparent backdrop to catch outside clicks */
.legal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
}

.legal-panel {
  position: absolute;
  bottom: calc(env(safe-area-inset-bottom) + 2.4rem);
  left: 0.75rem;
  max-width: min(240px, calc(100vw - 1.5rem));
  padding: 0.5rem 0.75rem;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
}

:global([data-theme="dark"]) .legal-panel {
  background: rgba(14, 18, 26, 0.88);
  border-color: rgba(255, 255, 255, 0.07);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.38);
}

.legal-close {
  position: absolute;
  top: 0.3rem;
  right: 0.4rem;
  background: none;
  border: none;
  cursor: pointer;
  color: rgba(0, 0, 0, 0.25);
  font-size: 0.6rem;
  padding: 0.15rem 0.3rem;
  line-height: 1;
  transition: color 0.15s ease;
}

.legal-close:hover {
  color: rgba(0, 0, 0, 0.55);
}

:global([data-theme="dark"]) .legal-close {
  color: rgba(255, 255, 255, 0.25);
}

:global([data-theme="dark"]) .legal-close:hover {
  color: rgba(255, 255, 255, 0.58);
}

.legal-line {
  margin: 0;
  font-size: var(--text-xs-size);
  color: rgba(0, 0, 0, 0.45);
  padding-right: 1rem;
}

.legal-line strong {
  color: rgba(0, 0, 0, 0.62);
  font-weight: 600;
}

:global([data-theme="dark"]) .legal-line {
  color: rgba(255, 255, 255, 0.42);
  text-shadow: none;
}

:global([data-theme="dark"]) .legal-line strong {
  color: rgba(255, 255, 255, 0.62);
}

.legal-about-link {
  font-size: var(--text-xs-size);
  color: rgba(0, 0, 0, 0.35);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  text-align: left;
  text-decoration: none;
  transition: color 0.15s ease;
}

.legal-about-link:hover {
  color: rgba(0, 0, 0, 0.6);
  text-decoration: underline;
}

:global([data-theme="dark"]) .legal-about-link {
  color: rgba(255, 255, 255, 0.32);
  text-shadow: none;
}

:global([data-theme="dark"]) .legal-about-link:hover {
  color: rgba(255, 255, 255, 0.58);
}

.legal-fade-enter-active,
.legal-fade-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.legal-fade-enter-from,
.legal-fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* ─── About overlay ─── */
.about-overlay-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(1rem, 3vw, 2rem);
  background: linear-gradient(to bottom, rgba(9, 12, 18, 0.08), rgba(9, 12, 18, 0.18));
  backdrop-filter: blur(1.5px);
  -webkit-backdrop-filter: blur(1.5px);
}

.about-overlay-panel {
  position: relative;
  width: 100%;
  max-width: min(34rem, calc(100vw - 2rem));
  max-height: min(40rem, calc(100vh - 3rem));
  overflow: hidden;
  padding: 1rem 1rem 0.95rem;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.78));
  backdrop-filter: blur(20px) saturate(115%);
  -webkit-backdrop-filter: blur(20px) saturate(115%);
  border: 1px solid rgba(255, 255, 255, 0.42);
  box-shadow:
    0 14px 40px rgba(0, 0, 0, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.48);
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

:global([data-theme="dark"]) .about-overlay-panel {
  background: linear-gradient(180deg, rgba(13, 17, 24, 0.86), rgba(13, 17, 24, 0.78));
  border-color: rgba(255, 255, 255, 0.09);
  box-shadow:
    0 18px 44px rgba(0, 0, 0, 0.42),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.about-overlay-close {
  position: absolute;
  top: 0.75rem;
  right: 0.8rem;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  cursor: pointer;
  font-size: 0.65rem;
  color: rgba(0, 0, 0, 0.28);
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  line-height: 1;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    border-color 0.15s ease;
}
.about-overlay-close:hover {
  color: rgba(0, 0, 0, 0.62);
  background: rgba(0, 0, 0, 0.08);
  border-color: rgba(0, 0, 0, 0.08);
}

:global([data-theme="dark"]) .about-overlay-close {
  color: rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.06);
}

:global([data-theme="dark"]) .about-overlay-close:hover {
  color: rgba(255, 255, 255, 0.68);
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.08);
}

.about-overlay-header {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  padding-right: 2rem;
}

.about-overlay-kicker {
  margin: 0;
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(0, 0, 0, 0.36);
}

.about-overlay-title {
  margin: 0;
  font-size: clamp(1.05rem, 2vw, 1.25rem);
  font-weight: 600;
  line-height: 1.15;
  color: rgba(0, 0, 0, 0.72);
}

.about-overlay-version {
  margin: 0;
  font-size: 0.7rem;
  color: rgba(0, 0, 0, 0.36);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

:global([data-theme="dark"]) .about-overlay-kicker {
  color: rgba(255, 255, 255, 0.34);
}

:global([data-theme="dark"]) .about-overlay-title {
  color: rgba(255, 255, 255, 0.74);
}

:global([data-theme="dark"]) .about-overlay-version {
  color: rgba(255, 255, 255, 0.3);
}

.about-overlay-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.about-overlay-chip {
  display: inline-flex;
  align-items: center;
  min-height: 1.5rem;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.46);
  border: 1px solid rgba(0, 0, 0, 0.06);
  color: rgba(0, 0, 0, 0.46);
  font-size: 0.68rem;
  white-space: nowrap;
}

:global([data-theme="dark"]) .about-overlay-chip {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.4);
}

.about-overlay-divider {
  height: 1px;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.03));
  margin: 0;
}
:global([data-theme="dark"]) .about-overlay-divider {
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.03));
}

.about-overlay-body {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  overflow-y: auto;
  padding-right: 0.2rem;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.14) transparent;
}

.about-overlay-body::-webkit-scrollbar {
  width: 5px;
}

.about-overlay-body::-webkit-scrollbar-track {
  background: transparent;
}

.about-overlay-body::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.14);
}

:global([data-theme="dark"]) .about-overlay-body {
  scrollbar-color: rgba(255, 255, 255, 0.14) transparent;
}

:global([data-theme="dark"]) .about-overlay-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.14);
}

.about-overlay-section {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  padding: 0.55rem 0.7rem;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.34);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

:global([data-theme="dark"]) .about-overlay-section {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.06);
}

.about-overlay-label {
  margin: 0;
  font-size: 0.66rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(0, 0, 0, 0.34);
}

:global([data-theme="dark"]) .about-overlay-label {
  color: rgba(255, 255, 255, 0.3);
}

.about-overlay-text {
  margin: 0;
  font-size: 0.76rem;
  line-height: 1.45;
  color: rgba(0, 0, 0, 0.5);
}
.about-overlay-text a {
  color: rgba(0, 0, 0, 0.58);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.about-overlay-text a:hover {
  color: rgba(0, 0, 0, 0.78);
}

:global([data-theme="dark"]) .about-overlay-text {
  color: rgba(255, 255, 255, 0.42);
}

:global([data-theme="dark"]) .about-overlay-text a {
  color: rgba(255, 255, 255, 0.48);
}

:global([data-theme="dark"]) .about-overlay-text a:hover {
  color: rgba(255, 255, 255, 0.68);
}

.about-overlay-copyright {
  color: rgba(0, 0, 0, 0.3);
  padding-top: 0.15rem;
}
:global([data-theme="dark"]) .about-overlay-copyright {
  color: rgba(255, 255, 255, 0.24);
}

@media (max-width: 540px) {
  .about-overlay-panel {
    max-width: calc(100vw - 1.25rem);
    max-height: calc(100vh - 1.5rem);
    border-radius: 16px;
    padding: 0.9rem 0.9rem 0.85rem;
  }
}

.about-fade-enter-active,
.about-fade-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.about-fade-enter-from,
.about-fade-leave-to {
  opacity: 0;
  transform: scale(0.97);
}
</style>
