<template>
  <div class="release-details">
    <div v-if="updates.browserUpdateAvailable" class="release-details__browser">
      <p>This installation was updated. Reload when you are ready to use the updated interface.</p>
      <button type="button" class="release-details__text-action" @click="updates.reloadBrowser()">
        Reload interface
      </button>
    </div>
    <div class="release-details__heading">
      <i class="bi bi-arrow-up-circle" aria-hidden="true" />
      <div>
        <h3>{{ title }}</h3>
        <p>{{ description }}</p>
      </div>
    </div>

    <dl class="release-details__versions">
      <div>
        <dt>Installed release</dt>
        <dd>{{ updates.status?.installedReleaseVersion || 'Unconfirmed combination' }}</dd>
      </div>
      <div v-if="updates.status?.latestReleaseVersion">
        <dt>Available</dt>
        <dd>{{ updates.status.latestReleaseVersion }}</dd>
      </div>
    </dl>

    <ul
      v-if="updates.status?.components?.length"
      class="release-details__components"
      aria-label="Installation components"
    >
      <li v-for="component in updates.status.components" :key="component.id">
        <div class="release-details__component-heading">
          <strong>{{ component.label }}</strong>
          <span>{{ componentStatus(component) }}</span>
        </div>
        <p v-if="component.management === 'SELF_MANAGED'">
          Installed: {{ component.installedBuildId || 'Unknown' }}
          <template v-if="component.targetBuildId"> · Release target: {{ component.targetBuildId }}</template>
        </p>
        <p v-else-if="component.management === 'PROVIDER_MANAGED'">The service provider manages updates.</p>
        <p v-else-if="component.management === 'UNKNOWN'">
          The installation owner can configure who manages this service.
        </p>
        <small v-if="component.compatibility === 'UPDATE_REQUIRED'"
          >Update this component to match the release's supported builds.</small
        >
        <small v-else-if="component.status === 'UPDATE_AVAILABLE' && component.compatibility === 'SUPPORTED'"
          >The installed build remains supported by this release.</small
        >
        <small v-if="component.checkFailed">Could not read the current build. Any displayed build is cached.</small>
      </li>
    </ul>

    <template v-if="updates.status?.latestReleaseVersion">
      <p class="release-details__summary">{{ updates.status.summary }}</p>
      <div class="release-details__actions">
        <a
          :href="updates.status.releaseNotesUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="release-details__primary"
        >
          Release notes <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
        </a>
        <a
          :href="updates.status.updateGuideUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="release-details__secondary"
        >
          How to update <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
        </a>
      </div>
      <p v-if="updates.updateAvailable" class="release-details__help">
        The installation owner can update MTL Explorer with Docker Compose. You can continue using this version.
      </p>
      <button
        v-if="updates.hasUpdate"
        type="button"
        class="release-details__text-action"
        @click="updates.skipVersion()"
      >
        Skip this version
      </button>
      <button
        v-else-if="updates.skipped"
        type="button"
        class="release-details__text-action"
        @click="updates.remindAgain()"
      >
        Show the update badge again
      </button>
    </template>

    <div class="release-details__check">
      <div>
        <span>{{ updates.status?.channel || 'stable' }} channel</span>
        <small v-if="updates.status?.checkedAt">Last checked {{ formatDateAndTime(updates.status.checkedAt) }}</small>
        <small v-if="updates.status?.checkFailed || updates.requestFailed"
          >Could not check for updates. Try again later.</small
        >
      </div>
      <button
        type="button"
        :disabled="updates.loading || updates.status?.status === 'DISABLED'"
        @click="updates.refresh(true)"
      >
        <i class="bi bi-arrow-clockwise" aria-hidden="true" /> {{ updates.loading ? 'Checking…' : 'Check now' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import { formatDateAndTime } from '@/utils/Utils';
import type { ReleaseComponentUpdate } from 'x8ing-mtl-api-typescript-fetch';

const updates = useReleaseUpdatesStore();
onMounted(() => void updates.refresh());
function componentStatus(component: ReleaseComponentUpdate): string {
  switch (component.status) {
    case 'UPDATE_AVAILABLE':
      return 'Update available';
    case 'CURRENT':
      return 'Current';
    case 'PROVIDER_MANAGED':
      return 'Managed by provider';
    case 'DISABLED':
      return 'Disabled';
    case 'UNTRACKED':
      return 'No target in this release';
    default:
      return 'Update status unknown';
  }
}
const title = computed(() =>
  updates.updateAvailable
    ? `MTL Explorer ${updates.status?.latestReleaseVersion} is available`
    : updates.status?.status === 'UP_TO_DATE'
      ? 'This installation is up to date'
      : updates.status?.status === 'DISABLED'
        ? 'Update checks are disabled'
        : updates.loading
          ? 'Checking for updates…'
          : 'Update information'
);
const description = computed(() =>
  updates.skipped
    ? 'You skipped the reminder for this release.'
    : updates.updateAvailable
      ? 'Review the changes and update when convenient.'
      : updates.status?.status === 'DISABLED'
        ? 'The installation owner can enable automatic update checks.'
        : updates.status?.status === 'UNKNOWN'
          ? 'Update status could not be confirmed for this installation.'
          : updates.status?.status === 'UNAVAILABLE'
            ? 'The release service is currently unavailable.'
            : 'Release information for this installation.'
);
</script>

<style scoped>
.release-details {
  padding: 1rem;
  color: var(--text-secondary);
}
.release-details__browser {
  margin-bottom: 1.25rem;
  padding: 0.8rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
}
.release-details__browser p {
  margin: 0;
}
.release-details__heading {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
}
.release-details__heading > i {
  color: var(--accent);
  font-size: var(--text-xl-size);
}
.release-details__heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  line-height: 1.4;
}
.release-details__heading p {
  margin: 0.35rem 0 0;
}
.release-details__versions {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem 3rem;
  margin: 1.5rem 0;
  padding: 1rem;
  background: var(--surface-hover);
  border-radius: 0.7rem;
}
.release-details__versions dt {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}
.release-details__versions dd {
  margin: 0.3rem 0 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  font-weight: var(--font-semibold);
}
.release-details__summary {
  line-height: 1.6;
}
.release-details__components {
  list-style: none;
  padding: 0;
  margin: 1rem 0;
}
.release-details__components li {
  padding: 0.85rem 0;
  border-bottom: 1px solid var(--border-default);
}
.release-details__component-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  justify-content: space-between;
}
.release-details__component-heading strong {
  color: var(--text-primary);
}
.release-details__components p,
.release-details__components small {
  margin: 0.35rem 0 0;
  font-size: var(--text-sm-size);
  overflow-wrap: anywhere;
}
.release-details__components small {
  display: block;
  color: var(--text-muted);
}
.release-details__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin: 1.2rem 0;
}
.release-details__actions a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 2.75rem;
  padding: 0.5rem 0.9rem;
  border-radius: 0.6rem;
  text-decoration: none;
}
.release-details__primary {
  background: var(--accent);
  color: var(--text-on-accent, #fff);
}
.release-details__secondary {
  border: 1px solid var(--border-default);
  color: var(--text-primary);
}
.release-details__help {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: 1.6;
}
.release-details__text-action {
  border: 0;
  padding: 0.5rem 0;
  background: transparent;
  color: var(--text-secondary);
  text-decoration: underline;
  cursor: pointer;
}
.release-details__check {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  justify-content: space-between;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-default);
  font-size: var(--text-sm-size);
}
.release-details__check small {
  display: block;
  margin-top: 0.3rem;
  color: var(--text-muted);
}
.release-details__check button {
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  padding: 0.65rem;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
}
.release-details__check button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
