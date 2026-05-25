<template>
  <div>
    <!-- ── Tile home sheet ── -->
    <BottomSheet
      v-model="isOpen"
      title="Admin"
      icon="bi bi-gear"
      :detents="[
        { id: 'comfortable', height: 'min(710px, 82vh)' },
        { id: 'large', height: '92vh' },
      ]"
      initial-detent="comfortable"
      @closed="onSheetClosed"
    >
      <div class="admin-root">
        <div class="admin-home">
          <section class="admin-hero">
            <span class="admin-hero__eyebrow">System utility</span>
            <div class="admin-hero__headline">
              <div>
                <h2 class="admin-hero__title">Admin workspace</h2>
                <p class="admin-hero__copy">
                  Manage imports, runtime tools, diagnostics, and local preferences without leaving the map.
                </p>
              </div>
              <component
                :is="isIndexing ? 'button' : 'span'"
                :class="[
                  'admin-state-chip',
                  isIndexing ? 'admin-state-chip--live admin-state-chip--linked' : 'admin-state-chip--quiet',
                ]"
                @click="isIndexing ? openPanel('jobs') : undefined"
              >
                <span class="admin-state-chip__dot"></span>
                {{ isIndexing ? 'Jobs active' : 'Quiet state' }}
              </component>
            </div>
          </section>

          <section v-for="group in adminTileGroups" :key="group.heading" class="admin-tile-section">
            <div class="admin-section-heading">
              <span class="admin-section-heading__label">{{ group.heading }}</span>
              <span class="admin-section-heading__hint">{{ group.hint }}</span>
            </div>
            <div class="admin-tile-grid">
              <button
                v-for="tile in group.tiles"
                :key="tile.panel"
                :class="['admin-tile', { 'admin-tile--badge': tile.badge, 'admin-tile--live': tile.live }]"
                :aria-label="`Open ${tile.label}`"
                @click="openPanel(tile.panel)"
              >
                <span class="admin-tile__icon-shell">
                  <i :class="[tile.icon, 'admin-tile__icon']" />
                </span>
                <span class="admin-tile__content">
                  <span class="admin-tile__topline">
                    <span class="admin-tile__label">{{ tile.label }}</span>
                    <span v-if="tile.meta" class="admin-tile__meta">{{ tile.meta }}</span>
                  </span>
                  <span class="admin-tile__description">{{ tile.description }}</span>
                </span>
                <i class="pi pi-angle-right admin-tile__arrow" />
                <span v-if="tile.badge" class="tile-pulse-dot" />
              </button>
            </div>
          </section>
        </div>
      </div>
    </BottomSheet>

    <!-- ── Panel detail sheet (opens on top of tile sheet) ── -->
    <BottomSheet
      v-model="isPanelOpen"
      :title="activePanelTitle"
      :icon="activePanelIcon"
      :detents="[{ height: '55vh' }, { height: '92vh' }]"
      :z-index="5050"
      :no-backdrop="true"
      @closed="onPanelClosed"
    >
      <div class="admin-root">
        <!-- ══════════════════════════════════════════════ GARMIN SYNC -->
        <div v-if="activePanel === 'garmin'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Remote export</span>
                <span class="panel-section__hint"
                  >Trigger the server-side Garmin export job to pull new activity files.</span
                >
              </div>
            </div>
            <div class="action-list">
              <div class="action-row">
                <div class="action-info">
                  <span class="action-label">Garmin Export</span>
                  <span class="action-hint">Trigger remote export job</span>
                </div>
                <div class="action-controls">
                  <span v-if="loading" class="status-pill loading"><i class="pi pi-spin pi-spinner" /> Running…</span>
                  <span v-else-if="error" class="status-pill error">{{ error }}</span>
                  <span v-else-if="success" class="status-pill success"><i class="pi pi-check" /> Done</span>
                  <Button label="Run" icon="pi pi-play" size="small" :disabled="loading" @click="onTrigger" />
                </div>
              </div>
            </div>
          </section>

          <section class="panel-section panel-section--console">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Command output</span>
                <span class="panel-section__hint">Export output appears here.</span>
              </div>
            </div>
            <div class="output-area">
              <pre class="output-pre" v-text="output || placeholder" />
            </div>
          </section>
        </div>

        <!-- ══════════════════════════════════════════════ HELPERS -->
        <div v-else-if="activePanel === 'helpers'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Cache</span>
                <span class="panel-section__hint">Clear the local track cache and reload from server.</span>
              </div>
            </div>
            <div class="action-list">
              <div class="action-row">
                <div class="action-info">
                  <span class="action-label">Reload Tracks</span>
                  <span class="action-hint">Clear cache and reload from server</span>
                </div>
                <div class="action-controls">
                  <span v-if="reloadLoading" class="status-pill loading"
                    ><i class="pi pi-spin pi-spinner" /> Reloading…</span
                  >
                  <span v-else-if="reloadError" class="status-pill error">{{ reloadError }}</span>
                  <span v-else-if="reloadSuccess" class="status-pill success"><i class="pi pi-check" /> Done</span>
                  <Button
                    label="Reload"
                    icon="pi pi-refresh"
                    size="small"
                    :disabled="reloadLoading"
                    @click="onReloadTracks"
                  />
                </div>
              </div>
            </div>
          </section>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Tool setup</span>
                <span class="panel-section__hint">Local helper environments for export and conversion tasks.</span>
              </div>
            </div>

            <div v-if="toolStatusLoading" class="panel-message panel-message--loading">
              <i class="pi pi-spin pi-spinner" /> Loading helper status…
            </div>
            <div v-else-if="toolStatusError" class="panel-message panel-message--error">{{ toolStatusError }}</div>
            <div v-else class="tool-list">
              <div class="tool-row">
                <div class="tool-row-top">
                  <span class="tool-name">gcexport</span>
                  <span :class="['status-badge', toolStatus.gcexportVenvPresent ? 'ok' : 'missing']">
                    {{ toolStatus.gcexportVenvPresent ? 'ready' : 'missing' }}
                  </span>
                  <a
                    href="https://github.com/pe-st/garmin-connect-export/tags"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="gh-link"
                  >
                    releases <i class="pi pi-external-link" />
                  </a>
                </div>
                <div class="tool-row-form">
                  <InputText
                    v-model="gcexportVersionInput"
                    placeholder="version e.g. v4.6.2"
                    :disabled="loading"
                    class="tool-input"
                  />
                  <Button
                    label="Install"
                    icon="pi pi-download"
                    size="small"
                    :disabled="loading || !gcexportVersionInput"
                    @click="onInstallGcexport"
                  />
                </div>
              </div>

              <div class="tool-row">
                <div class="tool-row-top">
                  <span class="tool-name">fit-export</span>
                  <span :class="['status-badge', toolStatus.fitExportVenvPresent ? 'ok' : 'missing']">
                    {{ toolStatus.fitExportVenvPresent ? 'ready' : 'missing' }}
                  </span>
                </div>
                <div class="tool-row-form">
                  <InputText
                    v-model="fitProfileInput"
                    placeholder="profile"
                    :disabled="loading"
                    class="tool-input tool-input--sm"
                  />
                  <InputText
                    v-model="fitPackagesInput"
                    placeholder="packages e.g. garth fitparse gpxpy"
                    :disabled="loading"
                    class="tool-input"
                  />
                  <Button
                    label="Install"
                    icon="pi pi-download"
                    size="small"
                    :disabled="loading || !fitProfileInput || !fitPackagesInput"
                    @click="onInstallFitExport"
                  />
                </div>
              </div>
            </div>
          </section>

          <section class="panel-section panel-section--console">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Command output</span>
                <span class="panel-section__hint">Recent install output appears here.</span>
              </div>
            </div>
            <div class="output-area">
              <pre class="output-pre" v-text="output || placeholder" />
            </div>
          </section>
        </div>

        <!-- ══════════════════════════════════════════════ UPLOAD -->
        <div v-else-if="activePanel === 'upload'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>
          <div class="panel-embed">
            <GpxUploadTab ref="gpxUploadTab" />
          </div>
        </div>

        <!-- ══════════════════════════════════════════════ SETTINGS -->
        <div v-else-if="activePanel === 'settings'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Appearance</span>
                <span class="panel-section__hint">Adjust local presentation without affecting map data.</span>
              </div>
            </div>
            <div class="action-list">
              <div class="action-row">
                <div class="action-info">
                  <span class="action-label">Color scheme</span>
                  <span class="action-hint">Switch between the light and dark client surfaces.</span>
                </div>
                <div class="action-controls action-controls--wrap">
                  <SelectButton
                    v-model="colorScheme"
                    :options="schemeOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                  />
                </div>
              </div>

              <div class="action-row action-row--vertical">
                <div class="action-info">
                  <span class="action-label">Format locale</span>
                  <span class="action-hint"
                    >Controls date, time, and number formatting without changing app language.</span
                  >
                </div>
                <Select
                  v-model="localeModel"
                  :options="localePresets"
                  option-label="label"
                  option-value="value"
                  placeholder="Browser default"
                  class="admin-select"
                />
                <code class="panel-preview">Preview: {{ localePreview }}</code>
                <span class="panel-caption">
                  Auto-detected: <strong>{{ localeDetection.value || 'no match' }}</strong> &nbsp;(browser:
                  {{ localeDetection.browserLang }} · timezone: {{ localeDetection.timezone }})
                </span>
              </div>
            </div>
          </section>
        </div>

        <!-- ══════════════════════════════════════════════ JOBS -->
        <div v-else-if="activePanel === 'jobs'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>
          <IndexerStatusTab />
        </div>

        <!-- ══════════════════════════════════════════════ FRESHNESS -->
        <div v-else-if="activePanel === 'freshness'" class="tab-content panel-shell">
          <DataFreshnessTab @refresh-data="onRefreshFreshnessData" />
        </div>

        <!-- ══════════════════════════════════════════════ LOG -->
        <div v-else-if="activePanel === 'log'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>
          <div class="panel-embed">
            <ServerLogTab ref="serverLogTab" />
          </div>
        </div>

        <!-- ══════════════════════════════════════════════ ABOUT -->
        <div v-else-if="activePanel === 'about'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <div class="build-overview-grid">
            <section class="build-card">
              <div class="build-card__header">
                <span class="build-card__icon"><i class="pi pi-server" /></span>
                <div class="build-card__title-block">
                  <span class="build-card__title">Server</span>
                  <span class="build-card__subtitle">Backend and container build</span>
                </div>
              </div>
              <dl class="build-detail-list">
                <div class="build-detail-row">
                  <dt>Version</dt>
                  <dd>
                    <code>{{ serverBuild?.version ?? unavailableValue }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Built</dt>
                  <dd>
                    <code>{{
                      serverBuild?.buildTime
                        ? formatDateAndTimeWithSeconds(new Date(serverBuild.buildTime))
                        : unavailableValue
                    }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Server ID</dt>
                  <dd>
                    <code>{{ serverBuild?.serverId ?? unavailableValue }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Image</dt>
                  <dd>
                    <code>{{ serverBuild?.image ? displayValue(serverBuild.image.version) : unavailableValue }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Image built</dt>
                  <dd>
                    <code>{{
                      serverBuild?.image ? formatOptionalBuildTime(serverBuild.image.buildTime) : unavailableValue
                    }}</code>
                  </dd>
                </div>
              </dl>
            </section>

            <section class="build-card">
              <div class="build-card__header">
                <span class="build-card__icon"><i class="pi pi-desktop" /></span>
                <div class="build-card__title-block">
                  <span class="build-card__title">Client</span>
                  <span class="build-card__subtitle">Browser build and runtime mode</span>
                </div>
              </div>
              <dl class="build-detail-list">
                <div class="build-detail-row">
                  <dt>Version</dt>
                  <dd>
                    <code>{{ clientVersion || unavailableValue }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Built</dt>
                  <dd>
                    <code>{{ clientBuildFormatted }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Running as</dt>
                  <dd>
                    <code>{{ isPwaMode ? 'PWA (installed)' : 'Browser' }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>User</dt>
                  <dd>
                    <code>{{ authenticatedUsername || unavailableValue }}</code>
                  </dd>
                </div>
                <div class="build-detail-row">
                  <dt>Session ID</dt>
                  <dd>
                    <span class="sensitive-value">
                      <code class="sensitive-value__text">{{ sessionIdDisplayValue }}</code>
                      <button
                        type="button"
                        class="sensitive-value__toggle"
                        :aria-label="sessionIdToggleLabel"
                        :aria-pressed="isSessionIdVisible"
                        :title="sessionIdToggleLabel"
                        :disabled="!userSessionId"
                        @click="toggleSessionIdVisibility"
                      >
                        <i :class="sessionIdToggleIcon" />
                      </button>
                    </span>
                  </dd>
                </div>
              </dl>
            </section>
          </div>

          <section class="panel-section panel-section--external">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">External components</span>
                <span class="panel-section__hint"
                  >Grouped by sidecar so image, tool, and data versions stay together.</span
                >
              </div>
            </div>
            <div v-if="externalVersionGroups.length > 0" class="external-component-grid">
              <article
                v-for="group in externalVersionGroups"
                :key="group.key"
                :class="['external-component-card', `external-component-card--${group.state}`]"
              >
                <div class="external-component-card__header">
                  <span class="external-component-card__icon"><i :class="group.icon" /></span>
                  <div class="external-component-card__title-block">
                    <span class="external-component-card__title">{{ group.label }}</span>
                    <span class="external-component-card__status">{{ group.statusLabel }}</span>
                  </div>
                </div>
                <dl class="external-version-list">
                  <div v-for="row in group.rows" :key="row.key" class="external-version-row">
                    <dt>{{ row.label }}</dt>
                    <dd>
                      <code>{{ row.value }}</code>
                    </dd>
                  </div>
                </dl>
              </article>
            </div>
            <div v-else class="external-empty">
              <i class="pi pi-info-circle" />
              <span>No external component version metadata is available yet.</span>
            </div>
          </section>
        </div>

        <!-- ══════════════════════════════════════════════ SESSION -->
        <div v-else-if="activePanel === 'session'" class="tab-content panel-shell session-panel">
          <div class="panel-intro panel-intro--compact">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <section class="panel-section panel-section--compact session-actions">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Logout modes</span>
                <span class="panel-section__hint"
                  >Keep local data for fast sign-in, or wipe everything this app can access.</span
                >
              </div>
            </div>
            <div class="action-list action-list--compact">
              <div class="action-row action-row--compact action-row--prominent">
                <div class="action-info">
                  <span class="action-label">Credentials only</span>
                  <span class="action-hint"
                    >Remove the JWT and server session cookie. Tracks, map cache, preferences, and UI state stay
                    available.</span
                  >
                </div>
                <div class="action-controls">
                  <span v-if="credentialsLogoutLoading" class="status-pill loading"
                    ><i class="pi pi-spin pi-spinner" /> Signing out…</span
                  >
                  <Button
                    label="Logout"
                    icon="pi pi-sign-out"
                    size="small"
                    :disabled="credentialsLogoutLoading || fullLogoutLoading"
                    @click="executeCredentialsOnlyLogout"
                  />
                </div>
              </div>

              <div class="action-row action-row--compact action-row--danger">
                <div class="action-info">
                  <span class="action-label">Forget everything</span>
                  <span class="action-hint"
                    >Logout, clear local/session storage, IndexedDB tracks, browser caches, readable cookies, and
                    service workers.</span
                  >
                </div>
                <div class="action-controls">
                  <span v-if="fullLogoutLoading" class="status-pill loading"
                    ><i class="pi pi-spin pi-spinner" /> Clearing…</span
                  >
                  <Button
                    label="Wipe &amp; Logout"
                    icon="pi pi-trash"
                    severity="danger"
                    size="small"
                    :disabled="fullLogoutLoading || credentialsLogoutLoading"
                    @click="confirmFullLogout"
                  />
                </div>
              </div>
            </div>
          </section>

          <section class="panel-section panel-section--compact">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Your Session</span>
                <span class="panel-section__hint">Request correlation id and token timing.</span>
              </div>
            </div>
            <div class="copy-field copy-field--compact">
              <input
                class="copy-field__input"
                :type="sessionIdInputType"
                readonly
                :value="userSessionId || unavailableValue"
                aria-label="User session id"
                @focus="selectCopyField"
              />
              <Button
                :aria-label="sessionIdToggleLabel"
                :title="sessionIdToggleLabel"
                :icon="sessionIdToggleIcon"
                size="small"
                severity="secondary"
                :disabled="!userSessionId"
                @click="toggleSessionIdVisibility"
              />
              <Button
                :label="sessionIdCopied ? 'Copied' : 'Copy'"
                :icon="sessionIdCopied ? 'bi bi-clipboard-check' : 'bi bi-copy'"
                size="small"
                severity="secondary"
                :disabled="!userSessionId"
                @click="copyUserSessionId"
              />
            </div>
            <span v-if="sessionIdCopyError" class="copy-field__error">{{ sessionIdCopyError }}</span>
            <div class="info-rows info-rows--compact">
              <div class="info-row info-row--compact">
                <span class="info-label">Created</span>
                <code class="info-value">{{ tokenIssuedAtFormatted }}</code>
              </div>
              <div class="info-row info-row--compact">
                <span class="info-label">Expires</span>
                <code class="info-value">{{ tokenExpiresAtFormatted }}</code>
              </div>
            </div>
          </section>
        </div>

        <!-- ══════════════════════════════════════════════ ATTRIBUTION -->
        <div v-else-if="activePanel === 'attribution'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>
          <AttributionTab :is-demo-mode="isDemoMode" />
        </div>
      </div>
    </BottomSheet>

    <!-- Full local cleanup confirmation dialog -->
    <Dialog
      v-model:visible="showFullLogoutConfirm"
      modal
      :closable="false"
      class="logout-confirm-dialog"
      :style="{ width: 'min(92vw, 34rem)' }"
    >
      <div class="logout-confirm">
        <div class="logout-confirm__icon">
          <i class="pi pi-exclamation-triangle" />
        </div>
        <div class="logout-confirm__body">
          <span class="logout-confirm__eyebrow">Destructive logout</span>
          <h3 class="logout-confirm__title">Forget everything on this device?</h3>
          <p class="logout-confirm__copy">
            This removes the JWT, asks the server to clear the session cookie, deletes local app storage, cached tracks,
            browser caches, readable cookies, and service worker registrations.
          </p>
          <p class="logout-confirm__note">Browser password-manager entries cannot be removed by a web app.</p>
        </div>
      </div>
      <template #footer>
        <div class="logout-confirm__footer">
          <Button label="Cancel" icon="pi pi-times" text @click="showFullLogoutConfirm = false" />
          <Button label="Wipe &amp; Logout" icon="pi pi-trash" severity="danger" autofocus @click="executeFullLogout" />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useTheme } from '@/composables/useTheme';
import { detectBestLocale, LOCALE_PRESETS, useLocale } from '@/composables/useLocale';
import {
  getDemoStatus,
  getGarminToolStatus,
  getServerBuildInfo,
  installFitExport,
  installGcexport,
  triggerGarminExport,
  type AdminOperationalTask,
  type BuildInfo,
  type GarminToolStatus,
} from '@/utils/ServiceHelper';
import {
  getAuthenticatedUsername,
  getTokenExpiresAt,
  getTokenIssuedAt,
  getUserSessionId,
  logoutAndForgetEverything,
  logoutCredentialsOnly,
} from '@/utils/auth';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import ServerLogTab from '@/components/admin/ServerLogTab.vue';
import IndexerStatusTab from '@/components/admin/IndexerStatusTab.vue';
import DataFreshnessTab from '@/components/admin/DataFreshnessTab.vue';
import AttributionTab from '@/components/admin/AttributionTab.vue';
import { clearTrackCache } from '@/utils/tracks/trackCollectionLoader';
import { clearAppliedDataFreshnessToken } from '@/utils/dataFreshnessStorage';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import { displayValue, versionInfoRows, type VersionInfoRow } from '@/utils/versionInfo';

const CREDENTIALS_LOGOUT_TIMEOUT_MS = 2500;
const FULL_LOGOUT_TIMEOUT_MS = 5000;
const HARD_LOGOUT_REDIRECT_MS = 6000;
const COPY_STATUS_RESET_MS = 1800;
const PANEL_RESET_DELAY_MS = 300;
const UNAVAILABLE_VALUE = 'Unavailable';
const MASKED_SESSION_ID = '********';
const TASK_ID_VECTOR_MAP = 'vector-map-tiles';
const TASK_ID_LOCATION_SEARCH = 'location-search';
const TASK_ID_ROUTING_SEGMENTS = 'routing-segments';
const EXTERNAL_VERSION_GROUP_FALLBACK_ORDER = 99;
const EXTERNAL_VERSION_GROUP_ORDER: Record<string, number> = {
  [TASK_ID_VECTOR_MAP]: 0,
  [TASK_ID_ROUTING_SEGMENTS]: 1,
  [TASK_ID_LOCATION_SEARCH]: 2,
};

type AdminPanel =
  | 'garmin'
  | 'helpers'
  | 'upload'
  | 'settings'
  | 'jobs'
  | 'freshness'
  | 'log'
  | 'about'
  | 'session'
  | 'attribution';
type PanelMeta = { eyebrow: string; title: string; description: string };
type AdminTile = {
  panel: AdminPanel;
  icon: string;
  label: string;
  description: string;
  meta: string;
  badge: boolean;
  live: boolean;
};
type AdminTileGroup = { heading: string; hint: string; tiles: AdminTile[] };
type ExternalVersionGroup = {
  key: string;
  label: string;
  state: AdminOperationalTask['state'];
  statusLabel: string;
  icon: string;
  rows: VersionInfoRow[];
};
type GpxUploadTabPublic = { loadStatus: () => Promise<void> | void };
type ServerLogTabPublic = { activate: () => void; deactivate: () => void };
type Emits = {
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
  (event: 'reload-tracks', done: (success?: boolean, message?: string) => void): void;
  (event: 'refresh-freshness-data', done: () => void): void;
};

defineOptions({ name: 'AdminDialog' });

const emit = defineEmits<Emits>();
const { isIndexing: indexerIsIndexing, isJobPending, isOperationalTaskActive, operationalTasks } = useIndexerStatus();
const isAnyPending = computed(() => indexerIsIndexing.value || isJobPending.value || isOperationalTaskActive.value);
const { colorScheme: themeColorScheme, setScheme } = useTheme();
const schemeOptions = [
  { label: '☀️ Light', value: 'light' },
  { label: '🌙 Dark', value: 'dark' },
];
const colorSchemeModel = computed({
  get: () => themeColorScheme.value,
  set: (value) => setScheme(value),
});

const { formatLocale, setLocale } = useLocale();
const localeModel = computed({
  get: () => formatLocale.value,
  set: (value) => setLocale(value),
});
const localePresets = [...LOCALE_PRESETS];
const localePreview = computed(() => {
  const locale = formatLocale.value || undefined;
  const now = new Date();
  const dateSample = now.toLocaleDateString(locale, { year: 'numeric', month: '2-digit', day: '2-digit' });
  const timeSample = now.toLocaleTimeString(locale, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  const numberSample = (12345.67).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `${dateSample} ${timeSample} — ${numberSample}`;
});
const localeDetection = detectBestLocale();

const colorScheme = colorSchemeModel;
const isIndexing = isAnyPending;
const unavailableValue = UNAVAILABLE_VALUE;

const isOpen = ref(false);
const isPanelOpen = ref(false);
const activePanel = ref<AdminPanel | null>(null);
const loading = ref(false);
const output = ref('');
const error = ref('');
const success = ref(false);
const placeholder = 'Waiting for response…';
const toolStatus = ref<GarminToolStatus>({
  gcexportConfiguredVersion: '…',
  gcexportVenvPresent: false,
  fitExportConfiguredProfile: '…',
  fitExportConfiguredPackages: '…',
  fitExportVenvPresent: false,
});
const toolStatusLoading = ref(false);
const toolStatusError = ref('');
const gcexportVersionInput = ref('');
const fitProfileInput = ref('');
const fitPackagesInput = ref('');
const serverBuild = ref<BuildInfo | null>(null);
const showFullLogoutConfirm = ref(false);
const clientBuild = ref(typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : null);
const clientVersion = ref(typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : null);
const reloadLoading = ref(false);
const reloadSuccess = ref(false);
const reloadError = ref('');
const credentialsLogoutLoading = ref(false);
const fullLogoutLoading = ref(false);
const isDemoMode = ref(false);
const isSessionIdVisible = ref(false);
const sessionIdCopied = ref(false);
const sessionIdCopyError = ref('');
const gpxUploadTab = ref<GpxUploadTabPublic | null>(null);
const serverLogTab = ref<ServerLogTabPublic | null>(null);
let sessionCopyResetTimer: ReturnType<typeof setTimeout> | null = null;

const authenticatedUsername = computed(() => getAuthenticatedUsername());
const userSessionId = computed(() => getUserSessionId());
const sessionIdDisplayValue = computed(() => {
  if (!userSessionId.value) return UNAVAILABLE_VALUE;
  return isSessionIdVisible.value ? userSessionId.value : MASKED_SESSION_ID;
});
const sessionIdInputType = computed(() => (userSessionId.value && !isSessionIdVisible.value ? 'password' : 'text'));
const sessionIdToggleIcon = computed(() => (isSessionIdVisible.value ? 'bi bi-eye-slash' : 'bi bi-eye'));
const sessionIdToggleLabel = computed(() => (isSessionIdVisible.value ? 'Hide session id' : 'Show session id'));
const tokenIssuedAtFormatted = computed(() => {
  const issuedAt = getTokenIssuedAt();
  return issuedAt ? formatDateAndTimeWithSeconds(issuedAt) : UNAVAILABLE_VALUE;
});
const tokenExpiresAtFormatted = computed(() => {
  const expiresAt = getTokenExpiresAt();
  return expiresAt ? formatDateAndTimeWithSeconds(expiresAt) : UNAVAILABLE_VALUE;
});
const readyToolCount = computed(
  () => [toolStatus.value.gcexportVenvPresent, toolStatus.value.fitExportVenvPresent].filter(Boolean).length
);
const adminTileGroups = computed<AdminTileGroup[]>(() => [
  {
    heading: 'Data',
    hint: 'Import and processing activity',
    tiles: [
      {
        panel: 'upload',
        icon: 'pi pi-upload',
        label: 'Upload',
        description: 'Import GPX files and inspect ingest readiness.',
        meta: 'Import',
        badge: false,
        live: false,
      },
      {
        panel: 'jobs',
        icon: 'pi pi-list-check',
        label: 'Jobs',
        description: 'Track indexer, map, and routing activity.',
        meta: isIndexing.value ? 'Live' : 'Idle',
        badge: isIndexing.value,
        live: isIndexing.value,
      },
      {
        panel: 'freshness',
        icon: 'pi pi-database',
        label: 'Freshness',
        description: 'Inspect domain revisions and the current map data token.',
        meta: 'Token',
        badge: false,
        live: false,
      },
      {
        panel: 'garmin',
        icon: 'pi pi-cloud-download',
        label: 'Garmin Sync',
        description: 'Trigger a remote Garmin export to pull new activity files.',
        meta: 'Remote',
        badge: false,
        live: false,
      },
    ],
  },
  {
    heading: 'System',
    hint: 'Diagnostics, tools, and build details',
    tiles: [
      {
        panel: 'log',
        icon: 'pi pi-align-left',
        label: 'Log',
        description: 'Inspect recent server output and runtime issues.',
        meta: 'Server',
        badge: false,
        live: false,
      },
      {
        panel: 'helpers',
        icon: 'pi pi-wrench',
        label: 'Helpers',
        description: 'Reload tracks, manage helper tools, and run installs.',
        meta: toolStatusLoading.value ? 'Checking' : `${readyToolCount.value}/2 ready`,
        badge: false,
        live: false,
      },
      {
        panel: 'about',
        icon: 'pi pi-info-circle',
        label: 'About',
        description: 'Client and server build details and runtime info.',
        meta: 'Build',
        badge: false,
        live: false,
      },
    ],
  },
  {
    heading: 'Session',
    hint: 'Preferences and account controls',
    tiles: [
      {
        panel: 'settings',
        icon: 'pi pi-cog',
        label: 'Settings',
        description: 'Adjust color scheme and locale formatting.',
        meta: colorScheme.value === 'dark' ? 'Dark' : 'Light',
        badge: false,
        live: false,
      },
      {
        panel: 'session',
        icon: 'pi pi-sign-out',
        label: 'Session',
        description: 'Logout credentials only or wipe all local app data.',
        meta: 'Logout',
        badge: false,
        live: false,
      },
      {
        panel: 'attribution',
        icon: 'pi pi-book',
        label: 'Attribution',
        description: 'Libraries, datasets, and map data references.',
        meta: 'Sources',
        badge: false,
        live: false,
      },
    ],
  },
]);
const activePanelMeta = computed<PanelMeta>(() => {
  const map: Record<AdminPanel, PanelMeta> = {
    garmin: {
      eyebrow: 'Ingestion',
      title: 'Garmin Sync',
      description: 'Trigger a remote Garmin export to pull new activity files into the system.',
    },
    helpers: {
      eyebrow: 'Maintenance',
      title: 'Helpers',
      description: 'Reload the local track cache, install helper tools, and run export commands.',
    },
    session: {
      eyebrow: 'Account',
      title: 'Session',
      description: 'Choose exactly what logout should remove from this device.',
    },
    upload: {
      eyebrow: 'Ingestion',
      title: 'Upload',
      description: 'Import new GPS material and verify ingest status before processing.',
    },
    settings: {
      eyebrow: 'Preferences',
      title: 'Settings',
      description: 'Tune the local client experience with a calmer set of presentation controls.',
    },
    jobs: {
      eyebrow: 'Processing',
      title: 'Jobs',
      description: 'Monitor background indexing and understand what the system is currently working on.',
    },
    freshness: {
      eyebrow: 'Data state',
      title: 'Current status',
      description: 'Inspect the DB-backed revision token used by the map to detect stale data.',
    },
    log: {
      eyebrow: 'Diagnostics',
      title: 'Log',
      description: 'Inspect recent runtime output and keep server-side issues visible while you work.',
    },
    about: {
      eyebrow: 'Build',
      title: 'About',
      description: 'Client and server build details and current runtime environment.',
    },
    attribution: {
      eyebrow: 'Credits',
      title: 'Attribution',
      description: 'A quick reference for libraries, map sources, and optional demo datasets.',
    },
  };

  return activePanel.value
    ? map[activePanel.value]
    : {
        eyebrow: 'Admin',
        title: 'Admin',
        description: 'Manage application tools and diagnostics.',
      };
});
const clientBuildFormatted = computed(() => {
  if (!clientBuild.value) return 'dev environment';
  const date = new Date(clientBuild.value);
  return isNaN(date.getTime()) ? clientBuild.value : formatDateAndTimeWithSeconds(date);
});
const externalVersionGroups = computed<ExternalVersionGroup[]>(() =>
  operationalTasks.value
    .map((task) => {
      const rows = versionInfoRows(task.versionInfo);
      if (rows.length === 0) return null;
      return {
        key: task.id,
        label: task.label,
        state: task.state,
        statusLabel: task.statusLabel,
        icon: externalComponentIcon(task.id),
        rows,
      };
    })
    .filter((group): group is ExternalVersionGroup => group !== null)
    .sort(
      (left, right) =>
        (EXTERNAL_VERSION_GROUP_ORDER[left.key] ?? EXTERNAL_VERSION_GROUP_FALLBACK_ORDER) -
        (EXTERNAL_VERSION_GROUP_ORDER[right.key] ?? EXTERNAL_VERSION_GROUP_FALLBACK_ORDER)
    )
);
const isPwaMode = computed(() => {
  if (typeof window === 'undefined') return false;
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  );
});
const activePanelTitle = computed(() => (activePanel.value ? activePanelMeta.value.title : ''));
const activePanelIcon = computed(() => {
  const map: Record<AdminPanel, string> = {
    garmin: 'bi bi-cloud-download',
    helpers: 'bi bi-wrench',
    session: 'bi bi-box-arrow-right',
    upload: 'bi bi-upload',
    settings: 'bi bi-sliders',
    jobs: 'bi bi-list-check',
    freshness: 'bi bi-database',
    log: 'bi bi-terminal',
    about: 'bi bi-info-circle',
    attribution: 'bi bi-book',
  };
  return activePanel.value ? map[activePanel.value] : 'bi bi-gear';
});

function formatOptionalBuildTime(value: string | null | undefined): string {
  return formatDateAndTimeWithSeconds(value) || displayValue(value);
}

function toggleSessionIdVisibility() {
  if (!userSessionId.value) return;
  isSessionIdVisible.value = !isSessionIdVisible.value;
}

function hideSessionId() {
  isSessionIdVisible.value = false;
}

function externalComponentIcon(taskId: string): string {
  if (taskId === TASK_ID_VECTOR_MAP) return 'pi pi-map';
  if (taskId === TASK_ID_LOCATION_SEARCH) return 'pi pi-search';
  if (taskId === TASK_ID_ROUTING_SEGMENTS) return 'pi pi-directions';
  return 'pi pi-box';
}

watch(isOpen, (opened) => {
  if (opened) {
    void loadToolStatus();
  } else {
    hideSessionId();
  }
});

watch(userSessionId, () => hideSessionId());

onBeforeUnmount(() => {
  if (sessionCopyResetTimer) {
    clearTimeout(sessionCopyResetTimer);
  }
});

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function toggle() {
  isOpen.value = !isOpen.value;
  if (isOpen.value) emit('tool-opened');
}

function close() {
  isOpen.value = false;
}

function onSheetClosed() {
  isOpen.value = false;
  isPanelOpen.value = false;
  hideSessionId();
  serverLogTab.value?.deactivate();
  emit('tool-closed');
}

function openPanel(panel: AdminPanel) {
  activePanel.value = panel;
  isPanelOpen.value = true;
  if (panel === 'upload') {
    void nextTick(() => gpxUploadTab.value?.loadStatus());
  } else if (panel === 'log') {
    void nextTick(() => serverLogTab.value?.activate());
  }
}

function onPanelClosed() {
  if (activePanel.value === 'log') {
    serverLogTab.value?.deactivate();
  }
  hideSessionId();
  isPanelOpen.value = false;
  setTimeout(() => {
    activePanel.value = null;
  }, PANEL_RESET_DELAY_MS);
}

async function loadToolStatus() {
  toolStatusLoading.value = true;
  toolStatusError.value = '';
  try {
    const [nextToolStatus, nextServerBuild, demoStatus] = await Promise.all([
      getGarminToolStatus(),
      getServerBuildInfo(),
      getDemoStatus(),
    ]);
    toolStatus.value = nextToolStatus;
    gcexportVersionInput.value = nextToolStatus.gcexportConfiguredVersion;
    fitProfileInput.value = nextToolStatus.fitExportConfiguredProfile;
    fitPackagesInput.value = nextToolStatus.fitExportConfiguredPackages;
    serverBuild.value = nextServerBuild;
    isDemoMode.value = demoStatus.demoMode;
  } catch (loadError) {
    toolStatusError.value = 'Failed to load tool status: ' + errorMessage(loadError);
  } finally {
    toolStatusLoading.value = false;
  }
}

async function onTrigger() {
  await runAction(() => triggerGarminExport());
}

async function onInstallGcexport() {
  await runAction(() => installGcexport(gcexportVersionInput.value.trim()));
  void loadToolStatus();
}

async function onInstallFitExport() {
  await runAction(() => installFitExport(fitProfileInput.value.trim(), fitPackagesInput.value.trim()));
  void loadToolStatus();
}

async function executeCredentialsOnlyLogout() {
  credentialsLogoutLoading.value = true;
  await paintBeforeLogout();
  await runLogoutWithRedirect(() => logoutCredentialsOnly(), CREDENTIALS_LOGOUT_TIMEOUT_MS);
}

function confirmFullLogout() {
  showFullLogoutConfirm.value = true;
}

async function onReloadTracks() {
  reloadLoading.value = true;
  reloadSuccess.value = false;
  reloadError.value = '';
  try {
    await clearTrackCache();
    clearAppliedDataFreshnessToken();
    emit('reload-tracks', (success = true, message = '') => {
      reloadSuccess.value = success;
      reloadError.value = success ? '' : message || 'Failed to reload';
      reloadLoading.value = false;
    });
  } catch (reloadFailure) {
    reloadError.value = errorMessage(reloadFailure) || 'Failed to reload';
    reloadLoading.value = false;
  }
}

function onRefreshFreshnessData(done: () => void) {
  emit('refresh-freshness-data', done);
}

async function copyUserSessionId() {
  const sessionId = userSessionId.value;
  if (!sessionId) return;

  try {
    sessionIdCopyError.value = '';
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(sessionId);
    } else {
      copyTextWithFallback(sessionId);
    }
    sessionIdCopied.value = true;
    if (sessionCopyResetTimer) {
      clearTimeout(sessionCopyResetTimer);
    }
    sessionCopyResetTimer = setTimeout(() => {
      sessionIdCopied.value = false;
      sessionCopyResetTimer = null;
    }, COPY_STATUS_RESET_MS);
  } catch (copyError) {
    sessionIdCopyError.value = errorMessage(copyError) || 'Failed to copy session id';
  }
}

function selectCopyField(event: Event) {
  const target = event.target as HTMLInputElement | null;
  target?.select?.();
}

function copyTextWithFallback(text: string) {
  const textArea = document.createElement('textarea');
  textArea.value = text;
  textArea.setAttribute('readonly', '');
  textArea.style.position = 'fixed';
  textArea.style.opacity = '0';
  document.body.appendChild(textArea);
  textArea.select();
  document.execCommand('copy');
  document.body.removeChild(textArea);
}

async function executeFullLogout() {
  showFullLogoutConfirm.value = false;
  fullLogoutLoading.value = true;
  await paintBeforeLogout();
  await runLogoutWithRedirect(() => logoutAndForgetEverything(), FULL_LOGOUT_TIMEOUT_MS);
}

async function paintBeforeLogout() {
  await nextTick();
  await new Promise<void>((res) => requestAnimationFrame(() => res()));
}

async function runLogoutWithRedirect(logoutFn: () => Promise<unknown> | unknown, timeoutMs: number) {
  const loginUrl = `${import.meta.env.BASE_URL}login`;
  const hardRedirect = setTimeout(() => {
    try {
      window.location.replace(loginUrl);
    } catch {
      /* ignore */
    }
  }, HARD_LOGOUT_REDIRECT_MS);
  await Promise.race([Promise.resolve(logoutFn()).catch(() => {}), new Promise((res) => setTimeout(res, timeoutMs))]);
  clearTimeout(hardRedirect);
  window.location.replace(loginUrl);
}

async function runAction(fn: () => Promise<string>) {
  loading.value = true;
  error.value = '';
  success.value = false;
  output.value = '';
  try {
    output.value = await fn();
    success.value = true;
  } catch (actionError) {
    const installError = actionError as Error & { installLog?: string | null };
    error.value = installError?.message ? installError.message : 'Failed';
    if (installError?.installLog) {
      output.value = installError.installLog;
    }
  } finally {
    loading.value = false;
  }
}

defineExpose({
  toggle,
  close,
});
</script>

<style scoped>
/*
   * IMPORTANT: .admin-root is the wrapper div required for :deep() selectors to work
   * inside a BottomSheet (which uses <Teleport to="body">). Without this wrapper,
   * scoped selectors like :deep(.p-tabs) have no ancestor carrying data-v-xxx
   * and won't match. See BottomSheet.vue comment about the neutral body contract.
   */

/* ── Root layout ── */
.admin-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 0 0.5rem;
}

.admin-home {
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
  padding: 0.15rem 0.1rem 1.25rem;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

.admin-hero {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding: 1rem 1rem 0.95rem;
  border-radius: 0.625rem;
  background: var(--surface-glass-light);
  border: 1px solid var(--border-medium);
}

.admin-hero__eyebrow {
  font-size: var(--text-xs-size);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.admin-hero__headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.admin-hero__title {
  margin: 0;
  font-size: var(--text-lg-size);
  line-height: var(--text-lg-lh);
  color: var(--text-primary);
}

.admin-hero__copy {
  margin: 0.35rem 0 0;
  max-width: 40rem;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-muted);
}

.admin-state-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.4rem 0.65rem;
  border-radius: 999px;
  border: 1px solid var(--border-default);
  background: var(--surface-elevated);
  color: var(--text-secondary);
  white-space: nowrap;
  font-size: var(--text-xs-size);
  font-weight: 600;
}

.admin-state-chip--linked {
  cursor: pointer;
  background: none;
  border: 1px solid color-mix(in srgb, var(--accent) 28%, transparent);
  font: inherit;
  transition:
    background 0.15s,
    border-color 0.15s;
  -webkit-tap-highlight-color: transparent;
}

.admin-state-chip--linked:hover {
  background: color-mix(in srgb, var(--accent) 15%, var(--surface-glass-heavy));
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
}

.admin-state-chip--linked:active {
  transform: scale(0.95);
}

.admin-state-chip--live {
  border-color: color-mix(in srgb, var(--accent) 28%, transparent);
  background: color-mix(in srgb, var(--accent) 9%, var(--surface-glass-heavy));
  color: var(--accent-text);
}

.admin-state-chip__dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0.65rem color-mix(in srgb, currentColor 35%, transparent);
}

.admin-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0.75rem;
}

.admin-summary-card {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.85rem 0.9rem;
  border-radius: 0.625rem;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-light);
}

.admin-summary-card--live {
  border-color: color-mix(in srgb, var(--accent) 25%, transparent);
  background: color-mix(in srgb, var(--accent) 6%, var(--surface-glass-light));
}

.admin-summary-card--ok {
  border-color: color-mix(in srgb, var(--success) 28%, transparent);
}

.admin-summary-card--warning {
  border-color: color-mix(in srgb, var(--warning) 32%, transparent);
  background: color-mix(in srgb, var(--warning) 6%, var(--surface-glass-light));
}

.admin-summary-card__label {
  font-size: var(--text-xs-size);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

.admin-summary-card__value {
  font-size: var(--text-base-size);
  font-weight: 700;
  color: var(--text-primary);
}

.admin-summary-card__hint {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-muted);
}

.admin-tile-section {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.admin-section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  padding-inline: 0.2rem;
}

.admin-section-heading--compact {
  margin-top: 0.15rem;
}

.admin-section-heading__label {
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
}

.admin-section-heading__hint {
  font-size: var(--text-xs-size);
  color: var(--text-faint);
}

.admin-tile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(15.5rem, 100%), 1fr));
  gap: 0.75rem;
}

.admin-tile-grid--secondary {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.admin-tile {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
  padding: 0.95rem;
  min-height: 8rem;
  background: var(--surface-glass-light);
  border: 1px solid var(--border-medium);
  border-radius: 0.625rem;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
  position: relative;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  text-align: left;
  min-width: 0;
  overflow: hidden;
}

.admin-tile:hover {
  background: color-mix(in srgb, var(--accent) 5%, var(--surface-glass-light));
  border-color: var(--border-hover);
}

.admin-tile:active {
  background: var(--surface-active);
  transform: scale(0.96);
}

.admin-tile:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--accent) 70%, transparent);
  outline-offset: 2px;
}

.admin-tile--live {
  border-color: color-mix(in srgb, var(--accent) 28%, transparent);
}

.admin-tile--quiet {
  min-height: 6.8rem;
}

.admin-tile__icon-shell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  flex-shrink: 0;
}

.admin-tile__icon-shell--quiet {
}

.admin-tile__icon {
  font-size: var(--text-lg-size);
  color: var(--accent-text);
}

.admin-tile__content {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
  flex: 1 1 auto;
}

.admin-tile__topline {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.35rem 0.5rem;
  min-width: 0;
  flex-wrap: wrap;
}

.admin-tile__label {
  font-size: var(--text-sm-size);
  font-weight: 650;
  color: var(--text-primary);
  line-height: var(--text-sm-lh);
  min-width: 0;
  overflow-wrap: anywhere;
}

.admin-tile__meta {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 0.18rem 0.45rem;
  border-radius: 999px;
  background: var(--surface-elevated);
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.admin-tile__description {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-muted);
}

.admin-tile__arrow {
  position: absolute;
  right: 0.85rem;
  bottom: 0.85rem;
  font-size: var(--text-sm-size);
  color: var(--text-faint);
}

.tile-pulse-dot {
  position: absolute;
  top: 0.8rem;
  right: 0.8rem;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--alert-dot);
  box-shadow: 0 0 6px var(--alert-dot-glow);
  animation: alert-pulse 2s ease-in-out infinite;
}

@keyframes alert-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.7);
  }
}

.tab-content {
  padding: 0.25rem 0.35rem 1.5rem;
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

.panel-shell {
  gap: 0.95rem;
}

.panel-intro {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.95rem 1rem;
  border-radius: 0.625rem;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass-light);
}

.panel-intro__eyebrow {
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.panel-intro__title {
  margin: 0;
  font-size: var(--text-base-size);
  color: var(--text-primary);
}

.panel-intro__copy {
  margin: 0;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-muted);
}

.panel-intro--compact {
  gap: 0.22rem;
  padding-block: 0.72rem;
}

.panel-intro--compact .panel-intro__copy {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.panel-section {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 0.95rem 1rem;
  border-radius: 0.625rem;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-light);
}

.panel-section--compact {
  gap: 0.6rem;
  padding-block: 0.75rem;
}

.panel-section--console {
  background: var(--surface-glass-light);
}

.panel-section--danger {
  border-color: color-mix(in srgb, var(--error) 20%, transparent);
  background: color-mix(in srgb, var(--error-bg) 60%, var(--surface-glass-light));
}

.panel-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.panel-section__title {
  display: block;
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
}

.panel-section__hint {
  display: block;
  margin-top: 0.2rem;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-faint);
}

.build-overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(18rem, 100%), 1fr));
  gap: 0.75rem;
}

.build-card {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: color-mix(in srgb, var(--surface-elevated) 82%, var(--surface-glass-light));
}

.build-card__header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.build-card__icon,
.external-component-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 2.25rem;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--accent) 10%, var(--surface-glass-heavy));
  color: var(--accent-text);
}

.build-card__title-block,
.external-component-card__title-block {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.build-card__title,
.external-component-card__title {
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
  font-weight: 700;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.build-card__subtitle,
.external-component-card__status {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-faint);
}

.build-detail-list,
.external-version-list {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.build-detail-row,
.external-version-row {
  display: grid;
  grid-template-columns: minmax(6rem, 0.42fr) minmax(0, 1fr);
  gap: 0.75rem;
  align-items: baseline;
  padding: 0.5rem 0;
  border-top: 1px solid var(--border-subtle);
}

.build-detail-row:first-child,
.external-version-row:first-child {
  border-top: 0;
}

.build-detail-row dt,
.external-version-row dt {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.build-detail-row dd,
.external-version-row dd {
  margin: 0;
  min-width: 0;
}

.build-detail-row code,
.external-version-row code {
  color: var(--text-secondary);
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: var(--text-xs-size);
  overflow-wrap: anywhere;
}

.panel-section--external {
  gap: 0.85rem;
}

.external-component-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(18rem, 100%), 1fr));
  gap: 0.75rem;
}

.external-component-card {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 0.95rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: var(--surface-elevated);
}

.external-component-card--done {
  border-color: color-mix(in srgb, var(--success) 24%, var(--border-default));
}

.external-component-card--running {
  border-color: color-mix(in srgb, var(--warning) 34%, var(--border-default));
  background: color-mix(in srgb, var(--warning-bg) 20%, var(--surface-elevated));
}

.external-component-card--warning {
  border-color: color-mix(in srgb, var(--warning) 44%, var(--border-default));
}

.external-component-card--disabled {
  color: var(--text-faint);
}

.external-component-card__header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.external-empty {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.75rem 0;
  color: var(--text-faint);
  font-size: var(--text-sm-size);
}

.panel-message {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.8rem 0.9rem;
  border-radius: 0.625rem;
  border: 1px solid var(--border-default);
  background: var(--surface-elevated);
  font-size: var(--text-sm-size);
}

.panel-message--loading {
  color: var(--text-muted);
}

.panel-message--error {
  color: var(--error);
  background: color-mix(in srgb, var(--error-bg) 65%, var(--surface-glass-light));
}

.panel-embed {
  border-radius: 0.625rem;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-light);
  overflow: hidden;
}

.action-list,
.tool-list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.action-list--compact {
  gap: 0.5rem;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: var(--surface-elevated);
}

.action-row--compact {
  padding: 0.68rem 0.8rem;
}

.action-row--prominent {
  border-color: color-mix(in srgb, var(--accent) 20%, var(--border-default));
  background: color-mix(in srgb, var(--accent) 5%, var(--surface-elevated));
}

.action-row--danger {
  border-color: color-mix(in srgb, var(--error) 24%, var(--border-default));
  background: color-mix(in srgb, var(--error-bg) 55%, var(--surface-elevated));
}

.action-row--vertical {
  align-items: stretch;
  flex-direction: column;
}

.action-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.action-label {
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-primary);
}

.action-hint {
  font-size: var(--text-xs-size);
  color: var(--text-faint);
  line-height: var(--text-xs-lh);
  margin: 0;
}

.action-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.action-controls--wrap {
  justify-content: flex-end;
}

.admin-select {
  width: 100%;
}

.panel-preview {
  margin-top: 0.1rem;
  padding: 0.55rem 0.7rem;
  border-radius: 0.5rem;
  background: var(--code-bg);
  border: 1px solid var(--code-border);
  font-size: var(--text-sm-size);
}

.panel-caption {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-faint);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: var(--text-xs-size);
  padding: 0.28rem 0.55rem;
  border-radius: 999px;
  background: var(--surface-glass-subtle);
  border: 1px solid var(--border-default);
}

.status-pill.loading {
  color: var(--text-muted);
}

.status-pill.error {
  color: var(--error);
  background: color-mix(in srgb, var(--error-bg) 55%, var(--surface-glass-light));
}

.status-pill.success {
  color: var(--success);
  background: color-mix(in srgb, var(--success-bg) 55%, var(--surface-glass-light));
}

.status-badge {
  font-size: var(--text-2xs-size);
  font-weight: 600;
  padding: 0.18rem 0.5rem;
  border-radius: 1rem;
  border: 1px solid transparent;
}

.status-badge.ok {
  background: var(--success-bg);
  border-color: color-mix(in srgb, var(--success) 24%, transparent);
  color: var(--success);
}

.status-badge.missing {
  background: var(--error-bg);
  border-color: color-mix(in srgb, var(--error) 24%, transparent);
  color: var(--error);
}

.tool-row {
  padding: 0.9rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: var(--surface-elevated);
}

.tool-row-top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.45rem;
  flex-wrap: wrap;
}

.tool-name {
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.gh-link {
  font-size: var(--text-xs-size);
  color: var(--accent-text);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  margin-left: auto;
}

.gh-link:hover {
  text-decoration: underline;
  color: var(--accent-text-light);
}

.gh-link .pi {
  font-size: var(--text-2xs-size);
}

.tool-row-form {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.tool-input {
  font-size: var(--text-sm-size) !important;
  width: 11rem;
}

.tool-input--sm {
  width: 7rem;
}

.output-area {
  background: var(--code-bg);
  border: 1px solid var(--border-strong);
  border-radius: 0.5rem;
  padding: 0.9rem;
  overflow: auto;
  min-height: 9rem;
  flex: 1 1 auto;
  -webkit-overflow-scrolling: touch;
}

.output-pre {
  margin: 0;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  font-size: var(--text-xs-size);
  color: var(--code-text);
  white-space: pre;
  word-break: normal;
  line-height: var(--text-xs-lh);
}

.info-rows {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.info-rows--compact {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.85rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.625rem;
  background: var(--surface-elevated);
}

.info-row--compact {
  gap: 0.55rem;
  padding: 0.58rem 0.7rem;
}

.info-label {
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  min-width: 5.5rem;
}

.info-value {
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
  font-family: 'SF Mono', 'Fira Code', monospace;
  min-width: 0;
  overflow-wrap: anywhere;
}

.sensitive-value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.4rem;
  align-items: center;
  min-width: 0;
}

.sensitive-value__text {
  min-width: 0;
}

.sensitive-value__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  background: var(--surface-glass-light);
  color: var(--text-muted);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.sensitive-value__toggle:hover:not(:disabled) {
  color: var(--accent-text);
  border-color: color-mix(in srgb, var(--accent) 36%, var(--border-default));
}

.sensitive-value__toggle:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--accent) 52%, transparent);
  outline-offset: 1px;
}

.sensitive-value__toggle:disabled {
  cursor: default;
  color: var(--text-faint);
  opacity: 0.6;
}

.copy-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 0.55rem;
  align-items: center;
}

.copy-field--compact {
  gap: 0.45rem;
}

.copy-field__input {
  min-width: 0;
  width: 100%;
  padding: 0.72rem 0.8rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  background: var(--code-bg);
  color: var(--code-text);
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.copy-field--compact .copy-field__input {
  padding: 0.55rem 0.65rem;
}

.copy-field__input:focus {
  outline: 2px solid color-mix(in srgb, var(--accent) 52%, transparent);
  outline-offset: 1px;
}

.copy-field__error {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--error);
}

:deep(.logout-confirm-dialog) {
  border-radius: 0.75rem;
  overflow: hidden;
}

:deep(.logout-confirm-dialog .p-dialog-header) {
  display: none;
}

:deep(.logout-confirm-dialog .p-dialog-content) {
  padding: 0;
  background: var(--surface-elevated);
}

:deep(.logout-confirm-dialog .p-dialog-footer) {
  padding: 0;
  border-top: 1px solid var(--border-default);
  background: var(--surface-elevated);
}

.logout-confirm {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 1rem;
  padding: 1.1rem;
}

.logout-confirm__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--error-bg) 72%, var(--surface-glass-light));
  color: var(--error);
  border: 1px solid color-mix(in srgb, var(--error) 22%, transparent);
}

.logout-confirm__icon .pi {
  font-size: var(--text-xl-size);
}

.logout-confirm__body {
  min-width: 0;
}

.logout-confirm__eyebrow {
  display: block;
  margin-bottom: 0.25rem;
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--error);
}

.logout-confirm__title {
  margin: 0;
  font-size: var(--text-lg-size);
  line-height: var(--text-lg-lh);
  color: var(--text-primary);
}

.logout-confirm__copy,
.logout-confirm__note {
  margin: 0.55rem 0 0;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-muted);
}

.logout-confirm__note {
  color: var(--text-faint);
}

.logout-confirm__footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  width: 100%;
  padding: 0.8rem 1rem;
}

@media (max-width: 480px) {
  .admin-hero__headline,
  .admin-section-heading,
  .action-row,
  .info-row {
    flex-wrap: wrap;
  }

  .copy-field {
    grid-template-columns: 1fr;
  }

  .build-detail-row,
  .external-version-row {
    grid-template-columns: 1fr;
    gap: 0.15rem;
  }

  .info-rows--compact {
    grid-template-columns: 1fr;
  }

  .copy-field :deep(.p-button) {
    width: 100%;
  }

  .admin-tile-grid,
  .admin-tile-grid--secondary,
  .admin-summary-grid {
    grid-template-columns: 1fr;
  }

  .admin-tile {
    min-height: auto;
  }

  .tool-row-form {
    flex-direction: column;
    align-items: stretch;
  }

  .tool-input,
  .tool-input--sm {
    width: 100%;
  }

  .gh-link {
    margin-left: 0;
  }

  .panel-section,
  .panel-intro,
  .admin-hero {
    padding-inline: 0.85rem;
  }

  .logout-confirm {
    grid-template-columns: 1fr;
  }

  .logout-confirm__footer {
    flex-direction: column-reverse;
  }

  .logout-confirm__footer :deep(.p-button) {
    width: 100%;
  }
}
</style>
