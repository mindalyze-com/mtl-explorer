<template>
  <BottomSheet
    :model-value="visible"
    :title="`Reauthorize ${GOOGLE_HEALTH_FEATURE_LABEL}`"
    icon="bi bi-arrow-repeat"
    :detents="[{ id: 'comfortable', height: 'min(680px, 86vh)' }]"
    initial-detent="comfortable"
    no-scroll-hint
    sheet-class="google-health-reauthorization-sheet"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="gh-reauthorization">
      <div v-if="completed" class="admin-message admin-message--success" role="status">
        <i class="bi bi-check-circle" aria-hidden="true" />
        <span>
          <strong>Google Health is authorized again.</strong>
          Existing mirrored data was kept, and regular sync can continue.
        </span>
      </div>
      <template v-else>
        <div class="admin-message admin-message--warning" role="status">
          <i class="bi bi-calendar-week" aria-hidden="true" />
          <span>
            <strong>{{ explanation?.heading ?? 'Confirm Google Health access again.' }}</strong>
            {{
              explanation?.detail ??
              'Approve read-only access with the same Google account. Stored records and routes are kept.'
            }}
          </span>
        </div>
        <div v-if="displayError" class="admin-message admin-message--error" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true" />
          <span>{{ displayError }}</span>
        </div>
        <template v-if="manualMode">
          <ol>
            <li>Select <strong>Open Google consent</strong> and use the same Google Health account.</li>
            <li>Approve the read-only access again.</li>
            <li>
              The browser opens <code>{{ manualRedirectUri }}</code> and shows a connection error. This is expected.
            </li>
            <li>Copy the complete address bar and paste it below. Developer tools are not needed.</li>
          </ol>
          <Button
            label="Open Google consent"
            icon="bi bi-box-arrow-up-right"
            :loading="connecting"
            :disabled="connecting || completing"
            @click="emit('connect')"
          />
          <label>
            <strong>Google return address</strong>
            <span
              >Paste the complete <code>127.0.0.1</code> address, including <code>code</code> and
              <code>state</code>.</span
            >
            <textarea
              v-model.trim="redirectUrl"
              rows="4"
              autocomplete="off"
              spellcheck="false"
              :aria-invalid="Boolean(redirectError)"
              :aria-describedby="redirectError ? 'gh-reauthorization-url-error' : undefined"
              :placeholder="`${manualRedirectUri}?code=...&state=...`"
            />
            <small
              v-if="redirectError"
              id="gh-reauthorization-url-error"
              class="gh-reauthorization__validation-error"
              role="alert"
              >{{ redirectError }}</small
            >
          </label>
          <Button
            label="Complete reauthorization"
            icon="bi bi-check-circle"
            :loading="completing"
            :disabled="!redirectUrl || Boolean(redirectError) || connecting || completing"
            @click="complete"
          />
        </template>
        <Button
          v-else
          label="Reauthorize Google"
          icon="pi pi-link"
          :loading="connecting"
          :disabled="connecting"
          @click="emit('connect')"
        />
      </template>
    </div>

    <template #footer>
      <Button label="Close" severity="secondary" @click="emit('update:visible', false)" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import Button from 'primevue/button';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import type { GHSetupResponse, GHStatusResponse } from '../../api/googleHealthApi';
import { validateGoogleHealthManualRedirectUrl } from '../../utils/googleHealthSetup';
import {
  GOOGLE_HEALTH_FEATURE_LABEL,
  googleHealthReauthorizationExplanation,
} from '../../utils/googleHealthPresentation';

const props = withDefaults(
  defineProps<{
    visible: boolean;
    setup: GHSetupResponse | null;
    status?: GHStatusResponse | null;
    connecting?: boolean;
    completing?: boolean;
    error?: string;
    result?: string;
  }>(),
  { connecting: false, completing: false, error: '', result: '' }
);

const emit = defineEmits<{
  (event: 'update:visible', visible: boolean): void;
  (event: 'connect'): void;
  (event: 'complete', redirectUrl: string): void;
}>();

const redirectUrl = ref('');
const explanation = computed(() => googleHealthReauthorizationExplanation(props.status));
const manualMode = computed(() => props.setup?.oauthMode !== 'PROXY');
const manualRedirectUri = computed(() => props.setup?.manualRedirectUri ?? 'http://127.0.0.1:1');
const redirectError = computed(() =>
  redirectUrl.value ? validateGoogleHealthManualRedirectUrl(redirectUrl.value, manualRedirectUri.value) : ''
);
const completed = computed(() => props.result === 'connected');
const displayError = computed(() => {
  if (props.error) return props.error;
  if (props.result === 'denied') return 'Google authorization was cancelled or denied.';
  if (props.result === 'expired') return 'The authorization attempt expired. Start it again.';
  if (props.result === 'error') return 'Google authorization did not complete. Start it again.';
  return '';
});

watch(
  () => [props.visible, props.result] as const,
  ([visible, result]) => {
    if (!visible || result === 'connected') redirectUrl.value = '';
  }
);

function complete() {
  if (redirectUrl.value && !redirectError.value) emit('complete', redirectUrl.value.trim());
}
</script>

<style scoped>
.gh-reauthorization {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.9rem;
  padding: 0 var(--bs-header-px, 1rem) 0.5rem;
}
.gh-reauthorization ol {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.gh-reauthorization li + li {
  margin-top: 0.45rem;
}
.gh-reauthorization label {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 0.35rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.gh-reauthorization label span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.gh-reauthorization textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  color: var(--text-primary);
  background: var(--surface-elevated);
  font-family: inherit;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  resize: vertical;
}
.gh-reauthorization textarea:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-subtle);
}
.gh-reauthorization__validation-error {
  color: var(--error);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
code {
  user-select: text;
}
</style>
