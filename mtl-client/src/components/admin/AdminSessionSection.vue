<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Session"
      description="Inspect the current session, sign out, or remove local application data."
      icon="bi bi-person-lock"
    />

    <section class="admin-card" aria-labelledby="admin-current-session-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-current-session-heading">Current session</h3>
          <p>Your sign-in details for this browser.</p>
        </div>
      </div>
      <dl class="admin-info-list">
        <div class="admin-info-row">
          <dt>User</dt>
          <dd>
            <code>{{ username || unavailable }}</code>
          </dd>
        </div>
        <div class="admin-info-row">
          <dt>Created</dt>
          <dd>
            <code>{{ issuedAt }}</code>
          </dd>
        </div>
        <div class="admin-info-row">
          <dt>Expires</dt>
          <dd>
            <code>{{ expiresAt }}</code>
          </dd>
        </div>
      </dl>
      <div class="admin-client-login-ref">
        <label for="admin-client-login-ref">ClientLoginRef</label>
        <p id="admin-client-login-ref-help">Reference for this login. It cannot grant access on its own.</p>
        <div class="admin-client-login-ref__controls">
          <input
            id="admin-client-login-ref"
            type="text"
            aria-describedby="admin-client-login-ref-help"
            readonly
            :value="clientLoginRef || unavailable"
            @focus="selectInputText"
          />
          <Button
            :label="clientLoginRefCopied ? 'Copied' : 'Copy'"
            :icon="clientLoginRefCopied ? 'bi bi-clipboard-check' : 'bi bi-copy'"
            size="small"
            severity="secondary"
            aria-label="Copy ClientLoginRef"
            :disabled="!clientLoginRef"
            @click="copyClientLoginRef"
          />
        </div>
        <span v-if="copyError" class="admin-status-pill admin-status-pill--error">{{ copyError }}</span>
      </div>
    </section>

    <section class="admin-card" aria-labelledby="admin-sign-out-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-sign-out-heading">Sign out</h3>
          <p>Remove credentials while keeping cached tracks and preferences for the next sign-in.</p>
        </div>
      </div>
      <div class="admin-action-row">
        <div class="admin-action-copy">
          <span class="admin-action-label">Sign out of this browser</span>
          <span class="admin-action-hint">You will need to sign in again to access this installation.</span>
        </div>
        <div class="admin-action-controls">
          <span v-if="credentialsLogoutLoading" class="admin-status-pill admin-status-pill--loading">
            <i class="pi pi-spin pi-spinner" /> Signing out…
          </span>
          <Button
            label="Sign out"
            icon="pi pi-sign-out"
            size="small"
            :disabled="credentialsLogoutLoading || fullLogoutLoading"
            @click="executeCredentialsLogout"
          />
        </div>
      </div>
    </section>

    <section class="admin-card admin-card--danger" aria-labelledby="admin-danger-zone-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-danger-zone-heading">Clear local data</h3>
          <p>Sign out and remove all local application data this browser can access.</p>
        </div>
      </div>
      <div class="admin-action-row">
        <div class="admin-action-copy">
          <span class="admin-action-label">Clear this browser</span>
          <span class="admin-action-hint">
            Remove saved preferences, cached tracks, and sign-in details. Files and data on the server are kept.
          </span>
        </div>
        <div class="admin-action-controls">
          <span v-if="fullLogoutLoading" class="admin-status-pill admin-status-pill--loading">
            <i class="pi pi-spin pi-spinner" /> Clearing…
          </span>
          <Button
            label="Clear & sign out"
            icon="pi pi-trash"
            severity="danger"
            size="small"
            :disabled="fullLogoutLoading || credentialsLogoutLoading"
            @click="showFullLogoutConfirm = true"
          />
        </div>
      </div>
    </section>

    <Dialog
      v-model:visible="showFullLogoutConfirm"
      modal
      header="Clear local data"
      :closable="false"
      class="logout-confirm-dialog"
      :style="{ width: 'min(92vw, 34rem)' }"
    >
      <div class="admin-logout-confirm">
        <i class="pi pi-exclamation-triangle" />
        <div>
          <h3>Clear saved data from this browser?</h3>
          <p>
            This signs you out and removes saved preferences and cached data from this browser. Files and data on the
            server are kept.
          </p>
          <small>Browser password-manager entries cannot be removed by a web application.</small>
        </div>
      </div>
      <template #footer>
        <div class="admin-logout-footer">
          <Button label="Cancel" icon="pi pi-times" text autofocus @click="showFullLogoutConfirm = false" />
          <Button label="Clear & sign out" icon="pi pi-trash" severity="danger" @click="executeFullLogout" />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import {
  getAuthenticatedUsername,
  getTokenExpiresAt,
  getTokenIssuedAt,
  getClientLoginRef,
  logoutAndForgetEverything,
  logoutCredentialsOnly,
} from '@/utils/auth';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';
import { selectInputText, writeTextToClipboard } from '@/utils/clipboard';

defineOptions({ name: 'AdminSessionSection' });

const CREDENTIALS_LOGOUT_TIMEOUT_MS = 2500;
const FULL_LOGOUT_TIMEOUT_MS = 5000;
const HARD_LOGOUT_REDIRECT_MS = 6000;
const COPY_STATUS_RESET_MS = 1800;
const unavailable = 'Unavailable';

const clientLoginRefCopied = ref(false);
const copyError = ref('');
const showFullLogoutConfirm = ref(false);
const credentialsLogoutLoading = ref(false);
const fullLogoutLoading = ref(false);
let copyResetTimer: ReturnType<typeof setTimeout> | null = null;

const username = computed(() => getAuthenticatedUsername());
const clientLoginRef = computed(() => getClientLoginRef());
const issuedAt = computed(() => {
  const value = getTokenIssuedAt();
  return value ? formatDateAndTimeWithSeconds(value) : unavailable;
});
const expiresAt = computed(() => {
  const value = getTokenExpiresAt();
  return value ? formatDateAndTimeWithSeconds(value) : unavailable;
});

onBeforeUnmount(() => {
  if (copyResetTimer) clearTimeout(copyResetTimer);
});

async function copyClientLoginRef() {
  if (!clientLoginRef.value) return;
  try {
    copyError.value = '';
    await writeTextToClipboard(clientLoginRef.value);
    clientLoginRefCopied.value = true;
    if (copyResetTimer) clearTimeout(copyResetTimer);
    copyResetTimer = setTimeout(() => {
      clientLoginRefCopied.value = false;
      copyResetTimer = null;
    }, COPY_STATUS_RESET_MS);
  } catch (error) {
    copyError.value = error instanceof Error ? error.message : 'Failed to copy ClientLoginRef.';
  }
}

async function executeCredentialsLogout() {
  credentialsLogoutLoading.value = true;
  await paintBeforeLogout();
  await runLogoutWithRedirect(() => logoutCredentialsOnly(), CREDENTIALS_LOGOUT_TIMEOUT_MS);
}

async function executeFullLogout() {
  showFullLogoutConfirm.value = false;
  fullLogoutLoading.value = true;
  await paintBeforeLogout();
  await runLogoutWithRedirect(() => logoutAndForgetEverything(), FULL_LOGOUT_TIMEOUT_MS);
}

async function paintBeforeLogout() {
  await nextTick();
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
}

async function runLogoutWithRedirect(logout: () => Promise<unknown> | unknown, timeoutMs: number) {
  const loginUrl = `${import.meta.env.BASE_URL}login`;
  const hardRedirect = setTimeout(() => window.location.replace(loginUrl), HARD_LOGOUT_REDIRECT_MS);
  await Promise.race([
    Promise.resolve(logout()).catch(() => undefined),
    new Promise((resolve) => setTimeout(resolve, timeoutMs)),
  ]);
  clearTimeout(hardRedirect);
  window.location.replace(loginUrl);
}
</script>

<style scoped>
.admin-client-login-ref {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-top: 0.75rem;
}

.admin-client-login-ref label,
.admin-client-login-ref p {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.admin-client-login-ref p {
  margin: 0;
}

.admin-client-login-ref__controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.45rem;
}

.admin-client-login-ref input {
  min-width: 0;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--border-default);
  border-radius: 0.45rem;
  background: var(--surface-elevated);
  color: var(--text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.admin-logout-confirm {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.85rem;
  padding: 1rem;
}

.admin-logout-confirm > i {
  color: var(--error);
  font-size: var(--text-2xl-size);
}

.admin-logout-confirm h3 {
  margin: 0 0 0.45rem;
  color: var(--text-primary);
}

.admin-logout-confirm p {
  margin: 0 0 0.45rem;
  color: var(--text-muted);
  line-height: 1.45;
}

.admin-logout-confirm small {
  color: var(--text-faint);
}

.admin-logout-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  width: 100%;
  padding: 0.75rem 1rem;
}

@media (max-width: 768px) {
  .admin-logout-confirm {
    grid-template-columns: 1fr;
  }

  .admin-logout-footer {
    flex-direction: column-reverse;
  }
}
</style>
