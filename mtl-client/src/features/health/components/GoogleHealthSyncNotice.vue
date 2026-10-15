<template>
  <div v-if="authorizationRequired || label || failedRoutes" class="gh-sync-notice" role="status">
    <GoogleHealthAuthorizationNotice v-if="authorizationRequired" :status="status" />
    <RouterLink
      v-if="label"
      :to="syntheticData ? { name: 'admin', params: { section: 'processing' } } : GOOGLE_HEALTH_PROCESSING_ROUTE"
    >
      {{ label }} <i class="bi bi-arrow-right" aria-hidden="true" />
    </RouterLink>
    <span v-if="showCoverageHint && historyPending">Older data may be missing.</span>
    <div v-if="failedRoutes" class="gh-sync-notice__routes">
      <RouterLink :to="routeRecoveryRoute">
        {{ failedRoutes.toLocaleString() }} {{ failedRoutes === 1 ? 'route import needs' : 'route imports need' }} retry
        <i class="bi bi-arrow-right" aria-hidden="true" />
      </RouterLink>
      <span v-if="status?.routeSummary?.lastError">{{ status.routeSummary.lastError }}</span>
      <span>{{ routeRetryLabel }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import type { GHStatusResponse } from '../api/googleHealthApi';
import {
  googleHealthReauthorizationExplanation,
  hasPendingGoogleHealthHistory,
} from '../utils/googleHealthPresentation';
import GoogleHealthAuthorizationNotice from './GoogleHealthAuthorizationNotice.vue';
import { formatHealthDateTime } from '../utils/healthDateTime';
import { GOOGLE_HEALTH_PROCESSING_ROUTE, GOOGLE_HEALTH_SETTINGS_ROUTE } from '../utils/googleHealthNavigation';

const props = withDefaults(
  defineProps<{
    status?: GHStatusResponse | null;
    showCoverageHint?: boolean;
    always?: boolean;
    syntheticData?: boolean;
    showAuthorization?: boolean;
  }>(),
  { showAuthorization: true }
);
const authorizationRequired = computed(
  () =>
    props.showAuthorization !== false &&
    !props.syntheticData &&
    Boolean(googleHealthReauthorizationExplanation(props.status))
);
const failedRoutes = computed(() => props.status?.routeSummary?.failedCount ?? 0);
const routeAccessMissing = computed(
  () => props.status?.grantedCategories?.activity === false || props.status?.grantedCategories?.location === false
);
const routeRecoveryRoute = computed(() =>
  props.status?.configurationState !== 'CONFIGURED' ||
  props.status?.connectionState !== 'CONNECTED' ||
  routeAccessMissing.value
    ? GOOGLE_HEALTH_SETTINGS_ROUTE
    : GOOGLE_HEALTH_PROCESSING_ROUTE
);
const routeRetryLabel = computed(() => {
  if (props.status?.configurationState !== 'CONFIGURED' || props.status?.connectionState !== 'CONNECTED')
    return 'Route retries are paused until Google Health is enabled and connected.';
  if (routeAccessMissing.value)
    return 'Route retries need Activity and Location access. Review permissions in Settings.';
  const retryAt = props.status.routeSummary?.nextRetryAt;
  return retryAt ? `Retry during a sync after ${formatHealthDateTime(retryAt)}.` : 'Retry during a later sync.';
});
const historyPending = computed(() => hasPendingGoogleHealthHistory(props.status));
const label = computed(() => {
  if (props.syntheticData) return 'View demo generation progress';
  const status = props.status;
  if (!status) return '';
  if (authorizationRequired.value) return '';
  const job = status.job;
  if (['QUEUED', 'RUNNING'].includes(job?.state ?? '')) {
    if (job?.kind === 'FULL_RECONCILIATION') return 'Rechecking history';
    return historyPending.value ? 'Importing older history' : 'Sync in progress';
  }
  if (historyPending.value && (status.configurationState !== 'CONFIGURED' || status.connectionState !== 'CONNECTED'))
    return 'History import paused';
  if (status.syncStates?.some((state) => Boolean(state.lastError))) return 'History ranges awaiting retry';
  if (['FAILED', 'COMPLETED_WITH_ERRORS'].includes(job?.state ?? '') || status.lastError) return 'Sync needs attention';
  return historyPending.value ? 'History import pending' : props.always ? 'View sync details' : '';
});
</script>

<style scoped>
.gh-sync-notice {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.25rem 0.6rem;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}
.gh-sync-notice a {
  color: var(--accent-text);
  padding-block: 0.35rem;
  text-underline-offset: 0.2em;
}
.gh-sync-notice__routes {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  flex-basis: 100%;
  overflow-wrap: anywhere;
}
.gh-sync-notice__routes a {
  color: var(--warning);
}
@media (pointer: coarse) {
  .gh-sync-notice a {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
    min-height: 44px;
  }
}
</style>
