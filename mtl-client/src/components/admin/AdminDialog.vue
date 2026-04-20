<template>
  <div>
    <!-- ── Tile home sheet ── -->
    <BottomSheet v-model="isOpen" title="Admin" icon="bi bi-gear" :detents="[{ height: '42vh' }, { height: '92vh' }]"
                 @closed="onSheetClosed">
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
                  :class="['admin-state-chip', isIndexing ? 'admin-state-chip--live admin-state-chip--linked' : 'admin-state-chip--quiet']"
                  @click="isIndexing ? openPanel('jobs') : undefined"
              >
                <span class="admin-state-chip__dot"></span>
                {{ isIndexing ? 'Jobs active' : 'Quiet state' }}
              </component>
            </div>
          </section>

          <section class="admin-tile-section" aria-labelledby="admin-actions-heading">
            <div class="admin-section-heading">
              <span id="admin-actions-heading" class="admin-section-heading__label">Actions</span>
              <span class="admin-section-heading__hint">Operational tools and diagnostics</span>
            </div>
            <div class="admin-tile-grid">
              <button
                  v-for="tile in adminPrimaryTiles"
                  :key="tile.panel"
                  :class="['admin-tile', { 'admin-tile--badge': tile.badge, 'admin-tile--live': tile.live }]"
                  :aria-label="`Open ${tile.label}`"
                  @click="openPanel(tile.panel)"
              >
                <span class="admin-tile__icon-shell">
                  <i :class="[tile.icon, 'admin-tile__icon']"/>
                </span>
                <span class="admin-tile__content">
                  <span class="admin-tile__topline">
                    <span class="admin-tile__label">{{ tile.label }}</span>
                    <span v-if="tile.meta" class="admin-tile__meta">{{ tile.meta }}</span>
                  </span>
                  <span class="admin-tile__description">{{ tile.description }}</span>
                </span>
                <i class="pi pi-angle-right admin-tile__arrow"/>
                <span v-if="tile.badge" class="tile-pulse-dot"/>
              </button>
            </div>
          </section>

          <section class="admin-tile-section" aria-labelledby="admin-reference-heading">
            <div class="admin-section-heading admin-section-heading--compact">
              <span id="admin-reference-heading" class="admin-section-heading__label">Reference</span>
              <span class="admin-section-heading__hint">Build details, credits, and supporting information</span>
            </div>
            <div class="admin-tile-grid admin-tile-grid--secondary">
              <button
                  v-for="tile in adminSecondaryTiles"
                  :key="tile.panel"
                  class="admin-tile admin-tile--quiet"
                  :aria-label="`Open ${tile.label}`"
                  @click="openPanel(tile.panel)"
              >
                <span class="admin-tile__icon-shell admin-tile__icon-shell--quiet">
                  <i :class="[tile.icon, 'admin-tile__icon']"/>
                </span>
                <span class="admin-tile__content">
                  <span class="admin-tile__topline">
                    <span class="admin-tile__label">{{ tile.label }}</span>
                    <span v-if="tile.meta" class="admin-tile__meta">{{ tile.meta }}</span>
                  </span>
                  <span class="admin-tile__description">{{ tile.description }}</span>
                </span>
                <i class="pi pi-angle-right admin-tile__arrow"/>
              </button>
            </div>
          </section>
        </div>
      </div>
    </BottomSheet>

    <!-- ── Panel detail sheet (opens on top of tile sheet) ── -->
    <BottomSheet v-model="isPanelOpen" :title="activePanelTitle" :icon="activePanelIcon"
                 :detents="[{ height: '55vh' }, { height: '92vh' }]" :zIndex="5050"
                 @closed="onPanelClosed">
      <div class="admin-root">

        <!-- ══════════════════════════════════════════════ TOOLS -->
        <div v-if="activePanel === 'tools'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Quick actions</span>
                <span class="panel-section__hint">Maintenance tasks that act on local cache or trigger server-side export flows.</span>
              </div>
            </div>
            <div class="action-list">
              <div class="action-row">
                <div class="action-info">
                  <span class="action-label">Reload Tracks</span>
                  <span class="action-hint">Clear cache and reload from server</span>
                </div>
                <div class="action-controls">
                  <span v-if="reloadLoading" class="status-pill loading"><i class="pi pi-spin pi-spinner"/> Reloading…</span>
                  <span v-else-if="reloadError" class="status-pill error">{{ reloadError }}</span>
                  <span v-else-if="reloadSuccess" class="status-pill success"><i class="pi pi-check"/> Done</span>
                  <Button label="Reload" icon="pi pi-refresh" size="small" :disabled="reloadLoading" @click="onReloadTracks"/>
                </div>
              </div>

              <div class="action-row">
                <div class="action-info">
                  <span class="action-label">Full Reload</span>
                  <span class="action-hint">Clear all local data (cache, IndexedDB, localStorage, cookies) and restart</span>
                </div>
                <div class="action-controls">
                  <span v-if="fullReloadLoading" class="status-pill loading"><i class="pi pi-spin pi-spinner"/> Reloading…</span>
                  <Button label="Full Reload" icon="pi pi-power-off" size="small" :disabled="fullReloadLoading" @click="confirmFullReload"/>
                </div>
              </div>

              <div class="action-row">
                <div class="action-info">
                  <span class="action-label">Garmin Export</span>
                  <span class="action-hint">Trigger remote export job</span>
                </div>
                <div class="action-controls">
                  <span v-if="loading" class="status-pill loading"><i class="pi pi-spin pi-spinner"/> Running…</span>
                  <span v-else-if="error" class="status-pill error">{{ error }}</span>
                  <span v-else-if="success" class="status-pill success"><i class="pi pi-check"/> Done</span>
                  <Button label="Run" icon="pi pi-play" size="small" :disabled="loading" @click="onTrigger"/>
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
              <i class="pi pi-spin pi-spinner"/> Loading helper status…
            </div>
            <div v-else-if="toolStatusError" class="panel-message panel-message--error">{{ toolStatusError }}</div>
            <div v-else class="tool-list">
              <div class="tool-row">
                <div class="tool-row-top">
                  <span class="tool-name">gcexport</span>
                  <span :class="['status-badge', toolStatus.gcexportVenvPresent ? 'ok' : 'missing']">
                    {{ toolStatus.gcexportVenvPresent ? 'ready' : 'missing' }}
                  </span>
                  <a href="https://github.com/pe-st/garmin-connect-export/tags" target="_blank"
                     rel="noopener noreferrer" class="gh-link">
                    releases <i class="pi pi-external-link"/>
                  </a>
                </div>
                <div class="tool-row-form">
                  <InputText v-model="gcexportVersionInput" placeholder="version e.g. v4.6.2" :disabled="loading" class="tool-input"/>
                  <Button label="Install" icon="pi pi-download" size="small"
                          :disabled="loading || !gcexportVersionInput"
                          @click="onInstallGcexport"/>
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
                  <InputText v-model="fitProfileInput" placeholder="profile" :disabled="loading" class="tool-input tool-input--sm"/>
                  <InputText v-model="fitPackagesInput" placeholder="packages e.g. garth fitparse gpxpy" :disabled="loading" class="tool-input"/>
                  <Button label="Install" icon="pi pi-download" size="small"
                          :disabled="loading || !fitProfileInput || !fitPackagesInput"
                          @click="onInstallFitExport"/>
                </div>
              </div>
            </div>
          </section>

          <section class="panel-section panel-section--console">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Command output</span>
                <span class="panel-section__hint">Recent install and export output appears here.</span>
              </div>
            </div>
            <div class="output-area">
              <pre class="output-pre" v-text="output || placeholder"/>
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
            <GpxUploadTab ref="gpxUploadTab"/>
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
                  <span class="action-hint">Controls date, time, and number formatting without changing app language.</span>
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
                  Auto-detected: <strong>{{ localeDetection.value || 'no match' }}</strong>
                  &nbsp;(browser: {{ localeDetection.browserLang }} · timezone: {{ localeDetection.timezone }})
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
          <IndexerStatusTab/>
        </div>

        <!-- ══════════════════════════════════════════════ LOG -->
        <div v-else-if="activePanel === 'log'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>
          <div class="panel-embed">
            <ServerLogTab ref="serverLogTab"/>
          </div>
        </div>

        <!-- ══════════════════════════════════════════════ ABOUT -->
        <div v-else-if="activePanel === 'about'" class="tab-content panel-shell">
          <div class="panel-intro">
            <span class="panel-intro__eyebrow">{{ activePanelMeta.eyebrow }}</span>
            <h3 class="panel-intro__title">{{ activePanelMeta.title }}</h3>
            <p class="panel-intro__copy">{{ activePanelMeta.description }}</p>
          </div>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Build information</span>
                <span class="panel-section__hint">Client and server build metadata for quick diagnostics.</span>
              </div>
            </div>
            <div class="info-rows">
              <div class="info-row" v-if="serverBuild">
                <span class="info-label">Server version</span>
                <code class="info-value">{{ serverBuild.version }}</code>
              </div>
              <div class="info-row" v-if="serverBuild?.buildTime">
                <span class="info-label">Server built</span>
                <code class="info-value">{{ formatDateAndTimeWithSeconds(new Date(serverBuild.buildTime)) }}</code>
              </div>
              <div class="info-row" v-if="clientVersion">
                <span class="info-label">Client version</span>
                <code class="info-value">{{ clientVersion }}</code>
              </div>
              <div class="info-row">
                <span class="info-label">Client built</span>
                <code class="info-value">{{ clientBuildFormatted }}</code>
              </div>
              <div class="info-row">
                <span class="info-label">Running as</span>
                <code class="info-value">{{ isPwaMode ? 'PWA (installed)' : 'Browser' }}</code>
              </div>
            </div>
          </section>

          <section class="panel-section">
            <div class="panel-section__header">
              <div>
                <span class="panel-section__title">Session controls</span>
                <span class="panel-section__hint">Sign out cleanly and remove local client state if needed.</span>
              </div>
            </div>
            <div class="logout-area">
              <p class="action-hint">Clear all local storage, offline tracks, and log out.</p>
              <Button label="Logout & Clear Data" icon="pi pi-power-off" severity="danger"
                      size="small" @click="confirmLogout" :disabled="loading"/>
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
          <AttributionTab :is-demo-mode="isDemoMode"/>
        </div>

      </div>
    </BottomSheet>

    <!-- Full Reload Confirmation Dialog -->
    <Dialog v-model:visible="showFullReloadConfirm" header="Full Reload" :modal="true" :closable="false" :style="{width: '450px'}">
      <div class="confirmation-content" style="display: flex; align-items: flex-start; padding: 1rem;">
        <i class="pi pi-exclamation-triangle" style="font-size: 2rem; color: var(--warning); margin-right: 1.25rem;"/>
        <span style="line-height: 1.5;">This will delete <strong>all local data</strong> — localStorage, sessionStorage, IndexedDB (cached tracks), cookies, and service worker caches — then reload the app. You will need to log in again.</span>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="showFullReloadConfirm = false"/>
        <Button label="Yes, wipe &amp; reload" icon="pi pi-power-off" class="p-button-danger" @click="executeFullReload" autofocus/>
      </template>
    </Dialog>

    <!-- Logout Confirmation Dialog -->
    <Dialog v-model:visible="showLogoutConfirm" header="Confirm Logout" :modal="true" :closable="false" :style="{width: '450px'}">
      <div class="confirmation-content" style="display: flex; align-items: flex-start; padding: 1rem;">
        <i class="pi pi-exclamation-triangle" style="font-size: 2rem; color: var(--warning); margin-right: 1.25rem;"/>
        <span style="line-height: 1.5;">Are you sure you want to clear all storage, delete offline tracks, and logout?</span>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="showLogoutConfirm = false"/>
        <Button label="Yes, Logout" icon="pi pi-check" class="p-button-danger" @click="executeLogout" autofocus/>
      </template>
    </Dialog>
  </div>
</template>

<script>
import {computed, defineComponent} from 'vue';
import {useTheme} from '@/composables/useTheme';
import {detectBestLocale, LOCALE_PRESETS, useLocale} from '@/composables/useLocale';
import {getDemoStatus, getGarminToolStatus, getServerBuildInfo, installFitExport, installGcexport, triggerGarminExport} from '@/utils/ServiceHelper';
import {serverLogout} from '@/utils/auth';
import {formatDateAndTimeWithSeconds} from '@/utils/Utils';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import ServerLogTab from '@/components/admin/ServerLogTab.vue';
import IndexerStatusTab from '@/components/admin/IndexerStatusTab.vue';
import AttributionTab from '@/components/admin/AttributionTab.vue';
import {trackStore} from '@/utils/trackStore';
import {useIndexerStatus} from '@/composables/useIndexerStatus';

export default defineComponent({
  name: 'AdminDialog',
  components: {BottomSheet, GpxUploadTab, ServerLogTab, IndexerStatusTab, AttributionTab},
  emits: ['tool-opened', 'tool-closed', 'reload-tracks'],
  setup() {
      const {isIndexing, isJobPending} = useIndexerStatus();
      const isAnyPending = computed(() => isIndexing.value || isJobPending.value);
      const {colorScheme, setScheme} = useTheme();
      const schemeOptions = [
        {label: '☀️ Light', value: 'light'},
        {label: '🌙 Dark', value: 'dark'},
      ];
      const colorSchemeModel = computed({
        get: () => colorScheme.value,
        set: (value) => setScheme(value),
      });

      const {formatLocale, setLocale} = useLocale();
      const localeModel = computed({
        get: () => formatLocale.value,
        set: (value) => setLocale(value),
      });
      const localePresets = LOCALE_PRESETS;
      const localePreview = computed(() => {
        const locale = formatLocale.value || undefined;
        const now = new Date();
        const dateSample = now.toLocaleDateString(locale, {year: 'numeric', month: '2-digit', day: '2-digit'});
        const timeSample = now.toLocaleTimeString(locale, {hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false});
        const numberSample = (12345.67).toLocaleString(locale, {minimumFractionDigits: 2, maximumFractionDigits: 2});
        return `${dateSample} ${timeSample} — ${numberSample}`;
      });
      const localeDetection = detectBestLocale();

      return {
        colorScheme: colorSchemeModel,
        schemeOptions,
        localeModel,
        localePresets,
        localePreview,
        localeDetection,
        formatDateAndTimeWithSeconds,
        isIndexing: isAnyPending,
      };
    },
    data() {
      return {
        isOpen: false,
        isPanelOpen: false,
        activePanel: null,
        loading: false,
        output: '',
        error: '',
        success: false,
        placeholder: 'Waiting for response…',
        toolStatus: {
          gcexportConfiguredVersion: '…',
          gcexportVenvPresent: false,
          fitExportConfiguredProfile: '…',
          fitExportConfiguredPackages: '…',
          fitExportVenvPresent: false,
        },
        toolStatusLoading: false,
        toolStatusError: '',
        gcexportVersionInput: '',
        fitProfileInput: '',
        fitPackagesInput: '',
        serverBuild: null,
        showLogoutConfirm: false,
        showFullReloadConfirm: false,
        clientBuild: typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : null,
        clientVersion: typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : null,
        reloadLoading: false,
        reloadSuccess: false,
        reloadError: '',
        fullReloadLoading: false,
        isDemoMode: false,
      };
    },
    computed: {
      readyToolCount() {
        return [this.toolStatus.gcexportVenvPresent, this.toolStatus.fitExportVenvPresent].filter(Boolean).length;
      },
      adminPrimaryTiles() {
        return [
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
            description: 'Track indexer activity and background processing.',
            meta: this.isIndexing ? 'Live' : 'Idle',
            badge: this.isIndexing,
            live: this.isIndexing,
          },
          {
            panel: 'tools',
            icon: 'pi pi-wrench',
            label: 'Tools',
            description: 'Reload tracks, trigger exports, and manage helpers.',
            meta: this.toolStatusLoading ? 'Checking' : `${this.readyToolCount}/2 ready`,
            badge: false,
            live: false,
          },
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
            panel: 'settings',
            icon: 'pi pi-cog',
            label: 'Settings',
            description: 'Adjust color scheme and locale formatting.',
            meta: this.colorScheme === 'dark' ? 'Dark' : 'Light',
            badge: false,
            live: false,
          },
        ];
      },
      adminSecondaryTiles() {
        return [
          {
            panel: 'about',
            icon: 'pi pi-info-circle',
            label: 'About',
            description: 'Client and server build details plus logout tools.',
            meta: 'Build',
          },
          {
            panel: 'attribution',
            icon: 'pi pi-book',
            label: 'Attribution',
            description: 'Libraries, datasets, and map data references.',
            meta: 'Sources',
          },
        ];
      },
      activePanelMeta() {
        const map = {
          tools: {
            eyebrow: 'Maintenance',
            title: 'Tools',
            description: 'Run maintenance actions, helper installs, and export commands from one place.',
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
          log: {
            eyebrow: 'Diagnostics',
            title: 'Log',
            description: 'Inspect recent runtime output and keep server-side issues visible while you work.',
          },
          about: {
            eyebrow: 'Build',
            title: 'About',
            description: 'Client and server build details, plus session-level cleanup controls.',
          },
          attribution: {
            eyebrow: 'Credits',
            title: 'Attribution',
            description: 'A quick reference for libraries, map sources, and optional demo datasets.',
          },
        };

        return map[this.activePanel] || {
          eyebrow: 'Admin',
          title: 'Admin',
          description: 'Manage application tools and diagnostics.',
        };
      },
      clientBuildFormatted() {
        if (!this.clientBuild) return 'dev environment';
        const date = new Date(this.clientBuild);
        return isNaN(date.getTime()) ? this.clientBuild : formatDateAndTimeWithSeconds(date);
      },
      isPwaMode() {
        if (typeof window === 'undefined') return false;
        return window.matchMedia('(display-mode: standalone)').matches ||
          /** @type {any} */ (window.navigator).standalone === true;
      },
      activePanelTitle() {
        return this.activePanel ? this.activePanelMeta.title : '';
      },
      activePanelIcon() {
        const map = {
          tools: 'bi bi-wrench',
          upload: 'bi bi-upload',
          settings: 'bi bi-sliders',
          jobs: 'bi bi-list-check',
          log: 'bi bi-terminal',
          about: 'bi bi-info-circle',
          attribution: 'bi bi-book',
        };
        return map[this.activePanel] || 'bi bi-gear';
      },
    },
    watch: {
      isOpen(opened) {
        if (opened) this.loadToolStatus();
      },
    },
    methods: {
      toggle() {
        this.isOpen = !this.isOpen;
        if (this.isOpen) this.$emit('tool-opened');
      },
      close() {
        this.isOpen = false;
      },
      onSheetClosed() {
        this.isOpen = false;
        this.isPanelOpen = false;
        this.$refs.serverLogTab?.deactivate();
        this.$emit('tool-closed');
      },
      openPanel(panel) {
        this.activePanel = panel;
        this.isPanelOpen = true;
        if (panel === 'upload') {
          this.$nextTick(() => this.$refs.gpxUploadTab?.loadStatus());
        } else if (panel === 'log') {
          this.$nextTick(() => this.$refs.serverLogTab?.activate());
        }
      },
      onPanelClosed() {
        if (this.activePanel === 'log') {
          this.$refs.serverLogTab?.deactivate();
        }
        this.isPanelOpen = false;
        setTimeout(() => {
          this.activePanel = null;
        }, 300);
      },
      async loadToolStatus() {
        this.toolStatusLoading = true;
        this.toolStatusError = '';
        try {
          const [toolStatus, serverBuild, demoStatus] = await Promise.all([
            getGarminToolStatus(),
            getServerBuildInfo(),
            getDemoStatus(),
          ]);
          this.toolStatus = toolStatus;
          this.gcexportVersionInput = toolStatus.gcexportConfiguredVersion;
          this.fitProfileInput = toolStatus.fitExportConfiguredProfile;
          this.fitPackagesInput = toolStatus.fitExportConfiguredPackages;
          this.serverBuild = serverBuild;
          this.isDemoMode = demoStatus.demoMode;
        } catch (error) {
          this.toolStatusError = 'Failed to load tool status: ' + (error?.message || error);
        } finally {
          this.toolStatusLoading = false;
        }
      },
      async onTrigger() {
        await this.runAction(() => triggerGarminExport());
      },
      async onInstallGcexport() {
        await this.runAction(() => installGcexport(this.gcexportVersionInput.trim()));
        this.loadToolStatus();
      },
      async onInstallFitExport() {
        await this.runAction(() => installFitExport(this.fitProfileInput.trim(), this.fitPackagesInput.trim()));
        this.loadToolStatus();
      },
      confirmLogout() {
        this.showLogoutConfirm = true;
      },
      async onReloadTracks() {
        this.reloadLoading = true;
        this.reloadSuccess = false;
        this.reloadError = '';
        try {
          await trackStore.clearCache();
          this.$emit('reload-tracks', () => {
            this.reloadSuccess = true;
            this.reloadLoading = false;
          });
        } catch (error) {
          this.reloadError = error?.message || 'Failed to reload';
          this.reloadLoading = false;
        }
      },
      confirmFullReload() {
        this.showFullReloadConfirm = true;
      },
      async executeFullReload() {
        this.showFullReloadConfirm = false;
        this.fullReloadLoading = true;
        try {
          await trackStore.clearCache();
        } catch (_) { /* ignore */ }
        try {
          localStorage.clear();
          sessionStorage.clear();
        } catch (_) { /* ignore */ }
        try {
          const dbs = await indexedDB.databases();
          await Promise.all(dbs.map(db => new Promise((res) => {
            const req = indexedDB.deleteDatabase(db.name);
            req.onsuccess = res;
            req.onerror = res;
            req.onblocked = res;
          })));
        } catch (_) { /* ignore */ }
        try {
          document.cookie.split(';').forEach(c => {
            document.cookie = c.trim().split('=')[0] + '=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/';
          });
        } catch (_) { /* ignore */ }
        try {
          const cacheKeys = await caches.keys();
          await Promise.all(cacheKeys.map(k => caches.delete(k)));
        } catch (_) { /* ignore */ }
        window.location.reload();
      },
      async executeLogout() {
        this.showLogoutConfirm = false;
        try {
          this.loading = true;
          await trackStore.clearCache();
          await serverLogout();
          localStorage.clear();
          sessionStorage.clear();
          this.isOpen = false;
          this.$router.push('/login');
        } catch (error) {
          this.error = 'Logout failed: ' + (error?.message || error);
        } finally {
          this.loading = false;
        }
      },
      async runAction(fn) {
        this.loading = true;
        this.error = '';
        this.success = false;
        this.output = '';
        try {
          this.output = await fn();
          this.success = true;
        } catch (error) {
          this.error = error?.message ? error.message : 'Failed';
          if (error?.installLog) {
            this.output = error.installLog;
          }
        } finally {
          this.loading = false;
        }
      },
    },
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
    border-radius: 1.25rem;
    background:
      linear-gradient(145deg, var(--surface-glass-heavy), var(--surface-glass-subtle)),
      linear-gradient(135deg, var(--accent-bg), transparent 65%);
    border: 1px solid var(--border-medium);
    box-shadow: var(--shadow-sm);
  }

  .admin-hero__eyebrow {
    font-size: 0.7rem;
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
    font-size: 1.18rem;
    line-height: 1.1;
    color: var(--text-primary);
  }

  .admin-hero__copy {
    margin: 0.35rem 0 0;
    max-width: 40rem;
    font-size: 0.83rem;
    line-height: 1.5;
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
    font-size: 0.76rem;
    font-weight: 600;
  }

  .admin-state-chip--linked {
    cursor: pointer;
    background: none;
    border: 1px solid color-mix(in srgb, var(--accent) 28%, transparent);
    font: inherit;
    transition: background 0.15s, border-color 0.15s;
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
    border-radius: 1.1rem;
    border: 1px solid var(--border-default);
    background: var(--surface-glass-light);
    box-shadow: var(--shadow-sm);
  }

  .admin-summary-card--live {
    border-color: color-mix(in srgb, var(--accent) 25%, transparent);
    background: linear-gradient(180deg, color-mix(in srgb, var(--accent) 11%, var(--surface-glass-heavy)), var(--surface-glass-light));
  }

  .admin-summary-card--ok {
    border-color: color-mix(in srgb, var(--success) 28%, transparent);
  }

  .admin-summary-card--warning {
    border-color: color-mix(in srgb, var(--warning) 32%, transparent);
    background: linear-gradient(180deg, color-mix(in srgb, var(--warning) 10%, var(--surface-glass-heavy)), var(--surface-glass-light));
  }

  .admin-summary-card__label {
    font-size: 0.7rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--text-faint);
  }

  .admin-summary-card__value {
    font-size: 1rem;
    font-weight: 700;
    color: var(--text-primary);
  }

  .admin-summary-card__hint {
    font-size: 0.76rem;
    line-height: 1.45;
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
    font-size: 0.82rem;
    font-weight: 700;
    color: var(--text-primary);
  }

  .admin-section-heading__hint {
    font-size: 0.74rem;
    color: var(--text-faint);
  }

  .admin-tile-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(178px, 1fr));
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
    background: linear-gradient(180deg, var(--surface-glass-heavy), var(--surface-glass-light));
    border: 1px solid var(--border-medium);
    border-radius: 1.15rem;
    box-shadow: var(--shadow-sm);
    cursor: pointer;
    transition: background 0.18s, border-color 0.18s, transform 0.16s ease, box-shadow 0.18s;
    position: relative;
    -webkit-tap-highlight-color: transparent;
    touch-action: manipulation;
    text-align: left;
  }

  .admin-tile:hover {
    background: linear-gradient(180deg, var(--surface-glass-heavy), color-mix(in srgb, var(--accent) 6%, var(--surface-glass-light)));
    border-color: var(--border-hover);
    box-shadow: var(--shadow-md);
    transform: translateY(-1px);
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
    background: linear-gradient(180deg, var(--surface-glass-subtle), var(--surface-glass-light));
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
    font-size: 1.15rem;
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
    justify-content: space-between;
    gap: 0.75rem;
  }

  .admin-tile__label {
    font-size: 0.88rem;
    font-weight: 650;
    color: var(--text-primary);
    line-height: 1.2;
  }

  .admin-tile__meta {
    display: inline-flex;
    align-items: center;
    padding: 0.18rem 0.45rem;
    border-radius: 999px;
    background: var(--surface-elevated);
    color: var(--text-muted);
    font-size: 0.66rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }

  .admin-tile__description {
    font-size: 0.77rem;
    line-height: 1.5;
    color: var(--text-muted);
  }

  .admin-tile__arrow {
    position: absolute;
    right: 0.85rem;
    bottom: 0.85rem;
    font-size: 0.8rem;
    color: var(--text-faint);
  }

  .tile-pulse-dot {
    position: absolute;
    top: 0.8rem;
    right: 0.8rem;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--alert-dot, #f59e0b);
    box-shadow: 0 0 6px var(--alert-dot-glow, rgba(245, 158, 11, 0.5));
    animation: alert-pulse 2s ease-in-out infinite;
  }

  @keyframes alert-pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.4; transform: scale(0.7); }
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
    border-radius: 1.2rem;
    border: 1px solid var(--border-medium);
    background:
      linear-gradient(180deg, var(--surface-glass-heavy), var(--surface-glass-light)),
      linear-gradient(135deg, var(--accent-bg), transparent 70%);
    box-shadow: var(--shadow-sm);
  }

  .panel-intro__eyebrow {
    font-size: 0.68rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-faint);
  }

  .panel-intro__title {
    margin: 0;
    font-size: 1rem;
    color: var(--text-primary);
  }

  .panel-intro__copy {
    margin: 0;
    font-size: 0.8rem;
    line-height: 1.5;
    color: var(--text-muted);
  }

  .panel-section {
    display: flex;
    flex-direction: column;
    gap: 0.8rem;
    padding: 0.95rem 1rem;
    border-radius: 1.2rem;
    border: 1px solid var(--border-default);
    background: var(--surface-glass-light);
    box-shadow: var(--shadow-sm);
  }

  .panel-section--console {
    background: linear-gradient(180deg, color-mix(in srgb, var(--surface-glass-heavy) 75%, transparent), var(--surface-glass-light));
  }

  .panel-section__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 0.75rem;
  }

  .panel-section__title {
    display: block;
    font-size: 0.84rem;
    font-weight: 700;
    color: var(--text-primary);
  }

  .panel-section__hint {
    display: block;
    margin-top: 0.2rem;
    font-size: 0.74rem;
    line-height: 1.45;
    color: var(--text-faint);
  }

  .panel-message {
    display: inline-flex;
    align-items: center;
    gap: 0.45rem;
    padding: 0.8rem 0.9rem;
    border-radius: 1rem;
    border: 1px solid var(--border-default);
    background: var(--surface-elevated);
    font-size: 0.8rem;
  }

  .panel-message--loading {
    color: var(--text-muted);
  }

  .panel-message--error {
    color: var(--error);
    background: color-mix(in srgb, var(--error-bg) 65%, var(--surface-glass-light));
  }

  .panel-embed {
    border-radius: 1.2rem;
    border: 1px solid var(--border-default);
    background: var(--surface-glass-light);
    box-shadow: var(--shadow-sm);
    overflow: hidden;
  }

  .action-list,
  .tool-list {
    display: flex;
    flex-direction: column;
    gap: 0.7rem;
  }

  .action-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 0.9rem 1rem;
    border: 1px solid var(--border-default);
    border-radius: 1rem;
    background: var(--surface-elevated);
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
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--text-primary);
  }

  .action-hint {
    font-size: 0.75rem;
    color: var(--text-faint);
    line-height: 1.4;
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
    border-radius: 0.85rem;
    background: var(--code-bg);
    border: 1px solid var(--code-border);
    font-size: 0.8rem;
  }

  .panel-caption {
    font-size: 0.77rem;
    line-height: 1.5;
    color: var(--text-faint);
  }

  .status-pill {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
    font-size: 0.75rem;
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
    font-size: 0.65rem;
    font-weight: 600;
    padding: 0.18rem 0.5rem;
    border-radius: 1rem;
    border: 1px solid transparent;
  }

  .status-badge.ok {
    background: rgba(34, 197, 94, 0.12);
    border-color: rgba(34, 197, 94, 0.2);
    color: var(--success);
  }

  .status-badge.missing {
    background: rgba(239, 68, 68, 0.12);
    border-color: rgba(239, 68, 68, 0.2);
    color: var(--error);
  }

  .tool-row {
    padding: 0.9rem 1rem;
    border: 1px solid var(--border-default);
    border-radius: 1rem;
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
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--text-primary);
    font-family: 'SF Mono', 'Fira Code', monospace;
  }

  .gh-link {
    font-size: 0.7rem;
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
    font-size: 0.6rem;
  }

  .tool-row-form {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .tool-input {
    font-size: 0.8rem !important;
    width: 11rem;
  }

  .tool-input--sm {
    width: 7rem;
  }

  .output-area {
    background: linear-gradient(180deg, rgba(15, 23, 42, 0.9), rgba(15, 23, 42, 0.76));
    border: 1px solid color-mix(in srgb, var(--border-default) 75%, transparent);
    border-radius: 1rem;
    padding: 0.9rem;
    overflow: auto;
    min-height: 9rem;
    flex: 1 1 auto;
    -webkit-overflow-scrolling: touch;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }

  .output-pre {
    margin: 0;
    font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
    font-size: 0.72rem;
    color: rgba(226, 232, 240, 0.92);
    white-space: pre;
    word-break: normal;
    line-height: 1.5;
  }

  .info-rows {
    display: flex;
    flex-direction: column;
    gap: 0.65rem;
  }

  .info-row {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 0.85rem 1rem;
    border: 1px solid var(--border-default);
    border-radius: 1rem;
    background: var(--surface-elevated);
  }

  .info-label {
    font-size: 0.8rem;
    color: var(--text-muted);
    min-width: 5.5rem;
  }

  .info-value {
    font-size: 0.78rem;
    color: var(--text-secondary);
    font-family: 'SF Mono', 'Fira Code', monospace;
  }

  .logout-area {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 0.2rem 0 0;
    flex-wrap: wrap;
  }

  @media (max-width: 480px) {
    .admin-hero__headline,
    .admin-section-heading,
    .action-row,
    .info-row {
      flex-wrap: wrap;
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
  }
.output-area {
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.9), rgba(15, 23, 42, 0.76));
  border: 1px solid color-mix(in srgb, var(--border-default) 75%, transparent);
  border-radius: 1rem;
  padding: 0.9rem;
  overflow: auto;
  min-height: 9rem;
  flex: 1 1 auto;
  -webkit-overflow-scrolling: touch;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.output-pre {
  margin: 0;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  font-size: 0.72rem;
  color: rgba(226, 232, 240, 0.92);
  white-space: pre;
  word-break: normal;
  line-height: 1.5;
}

/* ── About tab ── */
.info-rows {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.85rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 1rem;
  background: var(--surface-elevated);
}

.info-label {
  font-size: 0.8rem;
  color: var(--text-muted);
  min-width: 4rem;
}

.info-value {
  font-size: 0.78rem;
  color: var(--text-secondary);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.logout-area {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.2rem 0 0;
  flex-wrap: wrap;
}

/* ── Responsive ── */
@media (max-width: 480px) {
  .admin-hero__headline,
  .admin-section-heading,
  .action-row {
    flex-wrap: wrap;
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
}
</style>

