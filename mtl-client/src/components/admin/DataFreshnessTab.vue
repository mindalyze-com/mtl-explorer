<template>
  <div class="freshness-tab">
    <div v-if="!currentFreshness && !isFreshnessPollingHealthy" class="freshness-empty freshness-empty--error">
      <i class="pi pi-exclamation-triangle"/>
      <span>Freshness status unavailable</span>
    </div>

    <div v-else-if="!currentFreshness" class="freshness-empty">
      <i class="pi pi-spin pi-spinner"/>
      <span>Loading…</span>
    </div>

    <template v-else>
      <section :class="['freshness-hero', freshnessHeroClass]">
        <span class="freshness-hero__icon">
          <i :class="freshnessHeroIcon"/>
        </span>
        <div class="freshness-hero__body">
          <span class="freshness-hero__title">{{ freshnessHeroTitle }}</span>
          <span class="freshness-hero__detail">{{ freshnessHeroDetail }}</span>
        </div>
        <div class="freshness-hero__aside">
          <span v-if="lastChecked" class="freshness-hero__checked">Checked {{ lastChecked }}</span>
          <Button
            label="Refresh"
            icon="pi pi-refresh"
            size="small"
            :loading="refreshing"
            :disabled="refreshing"
            @click="onRefresh"
          />
        </div>
      </section>

      <section class="freshness-details">
        <div class="freshness-token-diff">
          <div class="freshness-token-diff__row">
            <span class="freshness-token-diff__label">Server</span>
            <code class="freshness-token__value" :aria-label="displayToken(serverToken)">
              <span
                v-for="part in serverTokenDiff"
                :key="part.key"
                :class="{ 'freshness-token__char--changed': part.changed }"
              >{{ part.char }}</span>
            </code>
          </div>

          <div class="freshness-token-diff__row">
            <span class="freshness-token-diff__label">Client</span>
            <code class="freshness-token__value" :aria-label="displayToken(clientLastToken)">
              <span
                v-for="part in clientTokenDiff"
                :key="part.key"
                :class="{ 'freshness-token__char--changed': part.changed }"
              >{{ part.char }}</span>
            </code>
          </div>
        </div>

        <div class="freshness-overview">
          <div class="freshness-metric">
            <span class="freshness-metric__label">Latest change</span>
            <span class="freshness-metric__value">{{ formatChangedAt(currentFreshness.changedAt) }}</span>
          </div>
          <div class="freshness-metric">
            <span class="freshness-metric__label">Domains</span>
            <span class="freshness-metric__value">{{ items.length }}</span>
          </div>
          <div class="freshness-metric">
            <span class="freshness-metric__label">
              Revision sum
              <i
                class="pi pi-info-circle freshness-metric__help"
                title="Sum of all domain revision counters. This is a quick activity signal only; freshness is detected by token equality."
              />
            </span>
            <span class="freshness-metric__value">{{ totalRevisions }}</span>
            <span class="freshness-metric__hint">Tracked writes across all domains; compare tokens, not this number.</span>
          </div>
        </div>

        <div class="freshness-grid">
          <div
            v-for="item in items"
            :key="item.key"
            :class="['freshness-card', { 'freshness-card--outdated': isDomainOutdated(item) }]"
          >
            <div class="freshness-card__top">
              <span class="freshness-card__icon">
                <i :class="domainIcon(item.key)"/>
              </span>
              <span class="freshness-card__title">{{ domainLabel(item.key) }}</span>
              <span v-if="isDomainOutdated(item)" class="freshness-card__badge">Outdated</span>
              <span class="freshness-card__revision">r{{ item.revision ?? 0 }}</span>
            </div>
            <div class="freshness-card__bar">
              <span class="freshness-card__bar-fill" :style="{ width: revisionWidth(item.revision) }"></span>
            </div>
            <div class="freshness-card__meta">
              <span>{{ item.key }}</span>
              <span v-if="isDomainOutdated(item)">client r{{ clientRevision(item) ?? '?' }}</span>
              <span v-else>{{ formatChangedAt(item.changedAt) }}</span>
            </div>
          </div>
        </div>

        <div class="freshness-actions">
          <span :class="['freshness-health', isFreshnessPollingHealthy ? 'freshness-health--ok' : 'freshness-health--error']">
            <i :class="isFreshnessPollingHealthy ? 'pi pi-check-circle' : 'pi pi-exclamation-triangle'"/>
            {{ isFreshnessPollingHealthy ? 'Polling healthy' : 'Polling failed' }}
          </span>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import Button from 'primevue/button';
import type { DataFreshnessItemDto } from 'x8ing-mtl-api-typescript-fetch';
import { useDataFreshness } from '@/composables/useDataFreshness';
import { getAppliedDataFreshnessToken } from '@/utils/dataFreshnessStorage';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';

const DOMAIN_LABELS: Record<string, string> = {
  config: 'Config',
  filters: 'Filters',
  index: 'Index',
  media: 'Media',
  track_geometry: 'Geometry',
  tracks: 'Tracks',
};

const DOMAIN_ICONS: Record<string, string> = {
  config: 'pi pi-cog',
  filters: 'pi pi-filter',
  index: 'pi pi-folder',
  media: 'pi pi-images',
  track_geometry: 'pi pi-map',
  tracks: 'pi pi-map-marker',
};

const DOMAIN_DISPLAY_ORDER = ['index', 'tracks', 'track_geometry', 'media', 'filters', 'config'] as const;
const UNKNOWN_DOMAIN_ORDER = DOMAIN_DISPLAY_ORDER.length;
const MIN_BAR_PERCENT = 4;
const FULL_BAR_PERCENT = 100;
const EMPTY_TOKEN_LABEL = 'not recorded';
const TOKEN_DIFF_SERVER_KEY_PREFIX = 'server-token';
const TOKEN_DIFF_CLIENT_KEY_PREFIX = 'client-token';
const TOKEN_READABLE_SEPARATOR = '__|';
const TOKEN_ITEM_SEPARATOR = '|';
const TOKEN_KEY_REVISION_SEPARATOR = ':';

interface TokenDiffPart {
  key: string;
  char: string;
  changed: boolean;
}

const emit = defineEmits<{
  (e: 'refresh-data', done: (success?: boolean) => void): void;
}>();

const { currentFreshness, lastChecked, refresh, isFreshnessPollingHealthy } = useDataFreshness();
const refreshing = ref(false);
const clientLastToken = ref(getAppliedDataFreshnessToken() ?? '');

const items = computed(() => [...(currentFreshness.value?.items ?? [])].sort(compareFreshnessItems));
const serverToken = computed(() => currentFreshness.value?.freshnessToken ?? '');
const maxRevision = computed(() => Math.max(1, ...items.value.map((item) => item.revision ?? 0)));
const totalRevisions = computed(() => items.value.reduce((sum, item) => sum + (item.revision ?? 0), 0));
const clientRevisionMap = computed(() => parseFreshnessTokenRevisions(clientLastToken.value));
const shouldApplyDataRefresh = computed(() => Boolean(serverToken.value && serverToken.value !== clientLastToken.value));
const serverTokenDiff = computed(() =>
  buildTokenDiffParts(serverToken.value, clientLastToken.value, TOKEN_DIFF_SERVER_KEY_PREFIX)
);
const clientTokenDiff = computed(() =>
  buildTokenDiffParts(clientLastToken.value, serverToken.value, TOKEN_DIFF_CLIENT_KEY_PREFIX)
);
const freshnessHeroTitle = computed(() => {
  if (!serverToken.value) return 'Server token pending';
  if (!clientLastToken.value) return 'Client token missing';
  return serverToken.value === clientLastToken.value ? 'In sync' : 'Out of sync';
});
const freshnessHeroDetail = computed(() => {
  if (!serverToken.value) return 'Waiting for the server freshness check to return a token.';
  if (!clientLastToken.value) return 'This browser has not recorded the last applied freshness token.';
  return serverToken.value === clientLastToken.value
    ? 'The client is showing the latest server data.'
    : 'The server has changes that this client has not applied yet.';
});
const freshnessHeroClass = computed(() => {
  if (!serverToken.value || !clientLastToken.value) return 'freshness-hero--neutral';
  return serverToken.value === clientLastToken.value ? 'freshness-hero--ok' : 'freshness-hero--stale';
});
const freshnessHeroIcon = computed(() => {
  if (!serverToken.value || !clientLastToken.value) return 'pi pi-info-circle';
  return serverToken.value === clientLastToken.value ? 'pi pi-check-circle' : 'pi pi-exclamation-triangle';
});

function refreshClientLastToken(): void {
  clientLastToken.value = getAppliedDataFreshnessToken() ?? '';
}

function onStorageChanged(): void {
  refreshClientLastToken();
}

onMounted(() => {
  refreshClientLastToken();
  window.addEventListener('focus', refreshClientLastToken);
  window.addEventListener('storage', onStorageChanged);
});

onUnmounted(() => {
  window.removeEventListener('focus', refreshClientLastToken);
  window.removeEventListener('storage', onStorageChanged);
});

function domainLabel(key?: string): string {
  if (!key) return 'Unknown';
  return DOMAIN_LABELS[key] ?? key;
}

function domainIcon(key?: string): string {
  if (!key) return 'pi pi-database';
  return DOMAIN_ICONS[key] ?? 'pi pi-database';
}

function compareFreshnessItems(a: DataFreshnessItemDto, b: DataFreshnessItemDto): number {
  const orderDiff = domainOrder(a.key) - domainOrder(b.key);
  if (orderDiff !== 0) return orderDiff;
  return (a.key ?? '').localeCompare(b.key ?? '');
}

function domainOrder(key?: string): number {
  if (!key) return UNKNOWN_DOMAIN_ORDER;
  const index = DOMAIN_DISPLAY_ORDER.indexOf(key as (typeof DOMAIN_DISPLAY_ORDER)[number]);
  return index === -1 ? UNKNOWN_DOMAIN_ORDER : index;
}

function revisionWidth(revision?: number): string {
  const pct = ((revision ?? 0) / maxRevision.value) * FULL_BAR_PERCENT;
  return `${Math.max(MIN_BAR_PERCENT, pct)}%`;
}

function clientRevision(item: DataFreshnessItemDto): number | null {
  if (!item.key) return null;
  return clientRevisionMap.value.get(item.key) ?? null;
}

function isDomainOutdated(item: DataFreshnessItemDto): boolean {
  const clientValue = clientRevision(item);
  if (clientValue == null) return false;
  return clientValue !== (item.revision ?? 0);
}

function formatChangedAt(value?: Date): string {
  if (!value) return 'not recorded';
  return formatDateAndTimeWithSeconds(value);
}

function displayToken(token?: string): string {
  return token || EMPTY_TOKEN_LABEL;
}

function parseFreshnessTokenRevisions(token: string): Map<string, number> {
  const revisions = new Map<string, number>();
  if (!token) return revisions;

  const readableToken = token.includes(TOKEN_READABLE_SEPARATOR)
    ? token.slice(token.indexOf(TOKEN_READABLE_SEPARATOR) + TOKEN_READABLE_SEPARATOR.length)
    : token;

  for (const item of readableToken.split(TOKEN_ITEM_SEPARATOR)) {
    const separatorIndex = item.lastIndexOf(TOKEN_KEY_REVISION_SEPARATOR);
    if (separatorIndex <= 0) continue;

    const key = item.slice(0, separatorIndex);
    const revision = Number(item.slice(separatorIndex + TOKEN_KEY_REVISION_SEPARATOR.length));
    if (key && Number.isFinite(revision)) {
      revisions.set(key, revision);
    }
  }

  return revisions;
}

function buildTokenDiffParts(token: string, counterpart: string, keyPrefix: string): TokenDiffPart[] {
  const text = displayToken(token);
  if (!token || !counterpart) {
    return [{ key: `${keyPrefix}-empty`, char: text, changed: false }];
  }

  const unchangedIndexes = longestCommonSubsequenceIndexes(token, counterpart);
  return Array.from(token).map((char, index) => ({
    key: `${keyPrefix}-${index}`,
    char,
    changed: !unchangedIndexes.has(index),
  }));
}

function longestCommonSubsequenceIndexes(token: string, counterpart: string): Set<number> {
  const tokenChars = Array.from(token);
  const counterpartChars = Array.from(counterpart);
  const lengths: number[][] = Array.from({ length: tokenChars.length + 1 }, () =>
    Array(counterpartChars.length + 1).fill(0)
  );

  for (let tokenIndex = tokenChars.length - 1; tokenIndex >= 0; tokenIndex--) {
    for (let counterpartIndex = counterpartChars.length - 1; counterpartIndex >= 0; counterpartIndex--) {
      lengths[tokenIndex][counterpartIndex] =
        tokenChars[tokenIndex] === counterpartChars[counterpartIndex]
          ? lengths[tokenIndex + 1][counterpartIndex + 1] + 1
          : Math.max(lengths[tokenIndex + 1][counterpartIndex], lengths[tokenIndex][counterpartIndex + 1]);
    }
  }

  const unchangedIndexes = new Set<number>();
  let tokenIndex = 0;
  let counterpartIndex = 0;
  while (tokenIndex < tokenChars.length && counterpartIndex < counterpartChars.length) {
    if (tokenChars[tokenIndex] === counterpartChars[counterpartIndex]) {
      unchangedIndexes.add(tokenIndex);
      tokenIndex++;
      counterpartIndex++;
    } else if (lengths[tokenIndex + 1][counterpartIndex] >= lengths[tokenIndex][counterpartIndex + 1]) {
      tokenIndex++;
    } else {
      counterpartIndex++;
    }
  }

  return unchangedIndexes;
}

async function onRefresh() {
  refreshing.value = true;
  try {
    if (shouldApplyDataRefresh.value) {
      const refreshed = await refreshMapData();
      if (!refreshed) return;
    } else {
      await refresh();
    }
    await refresh();
    refreshClientLastToken();
  } finally {
    refreshing.value = false;
  }
}

function refreshMapData(): Promise<boolean> {
  return new Promise((resolve) => {
    emit('refresh-data', (success = true) => resolve(success));
  });
}
</script>

<style scoped>
.freshness-tab {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.freshness-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-faint);
  font-size: var(--text-sm-size);
  padding: 1rem 0;
}

.freshness-empty--error {
  color: var(--error);
}

.freshness-hero {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.8rem 0.9rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.5rem;
  background: var(--surface-elevated);
}

.freshness-hero--ok {
  border-color: color-mix(in srgb, var(--success) 34%, transparent);
  background: color-mix(in srgb, var(--success) 8%, var(--surface-elevated));
}

.freshness-hero--stale {
  border-color: color-mix(in srgb, var(--error) 42%, transparent);
  background: color-mix(in srgb, var(--error-bg) 60%, var(--surface-elevated));
}

.freshness-hero--neutral {
  border-color: var(--border-subtle);
}

.freshness-hero__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--surface-glass-heavy);
  color: var(--text-muted);
  font-size: 1rem;
}

.freshness-hero--ok .freshness-hero__icon {
  color: var(--success);
}

.freshness-hero--stale .freshness-hero__icon {
  color: var(--error);
}

.freshness-hero__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
}

.freshness-hero__title {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: 800;
  line-height: 1.2;
}

.freshness-hero__detail {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.freshness-hero__checked {
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  font-weight: 600;
}

.freshness-hero__aside {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 0.6rem;
}

.freshness-details {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 0.9rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: var(--surface-glass-light);
}

.freshness-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 0.75rem;
  padding-top: 0.15rem;
}

.freshness-metric {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.freshness-metric__label {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: var(--text-2xs-size);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

.freshness-metric__help {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}

.freshness-metric__value {
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
}

.freshness-metric__hint {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-muted);
}

.freshness-token-diff {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
}

.freshness-token-diff__row {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr);
  align-items: start;
  gap: 0.35rem;
}

.freshness-token-diff__label {
  padding-top: 0.28rem;
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  font-weight: 700;
}

.freshness-token__value {
  display: block;
  padding: 0.24rem 0.34rem;
  border-radius: 0.35rem;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  font-size: var(--text-2xs-size);
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.freshness-token__char--changed {
  color: var(--error);
  font-weight: 800;
}

.freshness-grid {
  display: grid;
  grid-template-columns: 1fr;
  border-top: 1px solid var(--border-subtle);
}

.freshness-card {
  display: grid;
  grid-template-columns: minmax(11rem, 1.25fr) minmax(7rem, 0.75fr) minmax(10rem, 1fr);
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0;
  border-bottom: 1px solid var(--border-subtle);
}

.freshness-card--outdated {
  margin-inline: -0.45rem;
  padding-inline: 0.45rem;
  border-radius: 0.45rem;
  background: color-mix(in srgb, var(--error-bg) 45%, transparent);
}

.freshness-card__top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.freshness-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  color: var(--accent-text);
}

.freshness-card__title {
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
}

.freshness-card__revision {
  margin-left: auto;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: var(--text-xs-size);
  font-weight: 700;
  color: var(--text-muted);
}

.freshness-card__badge {
  margin-left: auto;
  padding: 0.12rem 0.38rem;
  border-radius: 999px;
  background: var(--error-bg);
  color: var(--error);
  font-size: var(--text-2xs-size);
  font-weight: 800;
  text-transform: uppercase;
}

.freshness-card__badge + .freshness-card__revision {
  margin-left: 0;
  color: var(--error);
}

.freshness-card__bar {
  height: 0.4rem;
  border-radius: 999px;
  background: var(--border-subtle);
  overflow: hidden;
}

.freshness-card__bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), var(--success));
}

.freshness-card--outdated .freshness-card__bar-fill {
  background: linear-gradient(90deg, var(--error), var(--warning));
}

.freshness-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  color: var(--text-faint);
  font-size: var(--text-xs-size);
}

.freshness-card__meta span:first-child {
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.freshness-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-top: 0;
}

.freshness-health {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: var(--text-xs-size);
}

.freshness-health--ok {
  color: var(--success);
}

.freshness-health--error {
  color: var(--error);
}

@media (max-width: 640px) {
  .freshness-hero {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .freshness-hero__aside {
    justify-content: space-between;
    width: 100%;
  }

  .freshness-card {
    grid-template-columns: 1fr;
    gap: 0.45rem;
  }

  .freshness-card__meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.2rem;
  }
}
</style>
