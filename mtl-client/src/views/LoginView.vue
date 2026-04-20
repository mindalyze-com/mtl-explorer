<template>
  <div class="login-wrapper">
    <div class="photo-backdrop" :style="{ backgroundImage: `url(${bgImage})` }"></div>
    <div class="photo-vignette"></div>
    <span class="photo-credit">© Patrick Heusser</span>
    <router-link to="/about" class="license-link" title="About, license & source code">
      AGPL-3.0 · About &amp; Source
    </router-link>
    <div class="login-container">
      <div class="mac-login-panel">
        <h2 class="app-title">My Trail Log</h2>

        <div v-if="demoMode" class="demo-hint">
          <span class="demo-badge">DEMO</span>
          <span>User: <strong>{{ demoUsername }}</strong> &nbsp;/&nbsp; Password: <strong>{{ demoPassword }}</strong></span>
        </div>

        <form @submit.prevent="handleLogin" class="form-container">
          <InputText 
            id="username" 
            type="text" 
            v-model="username" 
            placeholder="Username"
            class="mac-input"
            autocomplete="username"
          />
          
          <InputText 
            id="password" 
            type="password" 
            v-model="password" 
            placeholder="Password"
            class="mac-input"
            autocomplete="current-password"
          />

          <div v-if="sessionExpiredMessage" class="error-message">
            <Message severity="warn" :closable="false">{{ sessionExpiredMessage }}</Message>
          </div>

          <div v-if="errorMessage" class="error-message">
            <Message severity="error" :closable="false">{{ errorMessage }}</Message>
          </div>

          <Button type="submit" label="Sign In" class="mac-button" :loading="loading" />
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import axios from 'axios';
import { setToken } from '@/utils/auth';
import { getRandomBackground } from '@/utils/backgrounds';
import { trackStore, OVERVIEW_PRECISION } from '@/utils/trackStore';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { getDemoStatus } from '@/utils/ServiceHelper';
import { describeError, startStartupTimer, startupLog } from '@/utils/startupDiagnostics';

const router = useRouter();
const route = useRoute();
const username = ref('');
const password = ref('');
const errorMessage = ref('');
const sessionExpiredMessage = ref('');
const loading = ref(false);
const bgImage = getRandomBackground();
const demoMode = ref(false);
const demoUsername = ref('');
const demoPassword = ref('');

onMounted(async () => {
  startupLog('login', 'Login view mounted', { reason: route.query.reason ?? null });
  if (route.query.reason === 'expired') {
    sessionExpiredMessage.value = 'Your session has expired. Please sign in again.';
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

const backendUrl = import.meta.env.VITE_BACKEND_URL;

async function handleLogin() {
  if (!username.value || !password.value) {
    errorMessage.value = "Please enter username and password.";
    return;
  }

  loading.value = true;
  errorMessage.value = '';
  const loginTimer = startStartupTimer('login', 'Submitting login request');

  try {
    const response = await axios.post(`${backendUrl}api/auth/login`, {
      username: username.value,
      password: password.value
    });

    if (response.data && response.data.token) {
      loginTimer.success('Login accepted by server');
      setToken(response.data.token);
      // Warm the map-config request on a free HTTP connection BEFORE the
      // heavy bulk track prefetches below — otherwise on a fresh browser
      // (empty cache → full 95-track downloads) this tiny request gets
      // queued behind them and hits its own timeout, which forces the map
      // into the offline raster fallback on the very first login.
      fetchMapConfig();
      // Fire prefetch so Map.vue can consume it via consumePrefetch()
      startupLog('prefetch', 'Starting overview and 10m track prefetch', { precisions: [OVERVIEW_PRECISION, 10] });
      trackStore.prefetchAllTracks(OVERVIEW_PRECISION);
      trackStore.prefetchAllTracks(10);
      startupLog('login', 'Navigating to home after login');
      router.push('/');
    } else {
      loginTimer.warn('Login response did not contain a token');
      errorMessage.value = "Login failed. Invalid response.";
    }
  } catch (error) {
    loginTimer.error('Login request failed', describeError(error));
    if (axios.isAxiosError(error)) {
      if (!error.response) {
        errorMessage.value = "Network error: Unable to reach the server. Please check your connection.";
      } else if (error.response.status === 401 || error.response.status === 403) {
        errorMessage.value = "Invalid username or password.";
      } else {
        errorMessage.value = `Login failed (Status: ${error.response.status}).`;
      }
    } else {
      errorMessage.value = "An unexpected error occurred.";
    }
    console.error("Login error", error);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrapper {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);
}

.login-container {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 320px;
  padding: 1rem;
}

.mac-login-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: contentIn 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.app-title {
  color: var(--on-photo-text);
  font-weight: 500;
  font-size: 1.5rem;
  margin: 0 0 2rem 0;
  text-shadow: var(--on-photo-text-shadow);
  letter-spacing: 0.5px;
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
  font-size: 16px !important;
}

/* PrimeVue input override — !important is required to beat PrimeVue's
   high-specificity component styles. All values come from on-photo tokens. */
:deep(.mac-input) {
  width: 100%;
  border-radius: 20px !important;
  background-color: var(--on-photo-input-bg) !important;
  border: 1px solid var(--on-photo-border) !important;
  color: var(--on-photo-input-text) !important;
  backdrop-filter: var(--blur-light);
  -webkit-backdrop-filter: var(--blur-light);
  padding: 0.75rem 1.25rem !important;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1) !important;
  transition: all 0.3s ease;
}

:deep(.mac-input:focus) {
  background-color: var(--on-photo-input-bg-hi) !important;
  border-color: var(--on-photo-border-hi) !important;
  box-shadow: 0 0 0 2px var(--on-photo-border) !important;
  outline: none;
}

:deep(.mac-input::placeholder) {
  color: var(--on-photo-text-muted) !important;
  opacity: 1 !important;
}

:deep(.mac-button) {
  width: 100%;
  border-radius: 20px !important;
  background-color: var(--on-photo-button-bg) !important;
  border: none !important;
  color: var(--on-photo-button-text) !important;
  font-weight: 600 !important;
  padding: 0.75rem 1rem !important;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2) !important;
  transition: all 0.3s ease;
}

:deep(.mac-button:hover) {
  background-color: #ffffff !important;
  color: var(--on-photo-button-text) !important;
  transform: translateY(-1px);
}

:deep(.mac-button .p-button-label) {
  color: var(--on-photo-button-text) !important;
}

.error-message {
  width: 100%;
}

:deep(.error-message .p-message) {
  border-radius: 12px;
  background-color: var(--error-heavy);
  backdrop-filter: var(--blur-light);
  -webkit-backdrop-filter: var(--blur-light);
  border: 1px solid var(--on-photo-border-hi);
  color: var(--text-primary);
}

:deep(.error-message .p-message .p-message-text) {
  color: var(--text-primary);
}

:deep(.error-message .p-message .p-message-icon) {
  color: var(--text-primary);
}

/* ─── Keyframes ─── */

@keyframes contentIn {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* Landscape phones */
@media (max-height: 500px) and (orientation: landscape) {
  .app-title {
    font-size: 1.2rem;
    margin-bottom: 1rem;
  }
}

/* Accessibility: respect reduced-motion */
@media (prefers-reduced-motion: reduce) {
  .mac-login-panel { animation: none; }
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
  font-size: 0.82rem;
  line-height: 1.4;
}

.demo-badge {
  display: inline-block;
  background: rgba(255, 180, 60, 0.85);
  color: var(--on-photo-button-text);
  font-weight: 700;
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

/* AGPL-3.0 source-offer link, shown on the login page so every network
   user has access to the About/License/source info without signing in. */
.license-link {
  position: absolute;
  bottom: calc(env(safe-area-inset-bottom) + 0.5rem);
  right: 0.75rem;
  z-index: 11;
  font-size: 0.72rem;
  color: var(--on-photo-text, #fff);
  text-decoration: none;
  opacity: 0.75;
  text-shadow: var(--on-photo-text-shadow, 0 1px 2px rgba(0, 0, 0, 0.5));
  padding: 0.2rem 0.4rem;
}

.license-link:hover {
  opacity: 1;
  text-decoration: underline;
}
</style>

