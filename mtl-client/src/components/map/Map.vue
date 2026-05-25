<template>
  <div class="container">
    <!-- Base map: base tiles only. CSS filter (grayscale + brightness) applied here to dim
         the base independently of overlays, which live on the overlay map above. -->
    <div ref="mapBaseContainer" class="map-base" :style="baseMapStyle"></div>

    <!-- Overlay map: Swiss Mobility overlays, tracks, highlights, heatmap, media —
         always full color, transparent background. -->
    <div ref="mapOverlayContainer" class="map-overlay"></div>

    <!-- ─── Navigation sheet (bottom bar mobile / left panel desktop) ─── -->
    <NavigationSheet
      ref="navSheet"
      :tools="toolDefs"
      :active-tool="activeToolId"
      :primary-ids="['stats', 'filter', 'planner', 'map']"
      :alert-tool-ids="alertToolIds"
      :drifted-tool-ids="driftedToolIds"
      @select="onToolSelect"
    />

    <transition name="fade">
      <button
        v-if="showLocationSearchFab"
        class="mtl-location-search-fab"
        type="button"
        aria-label="Search location"
        title="Search location"
        @click="openLocationSearch"
      >
        <i class="bi bi-search"></i>
      </button>
    </transition>

    <LocationSearchSheet
      v-model="locationSearchVisible"
      :map-center="locationSearchMapCenter"
      @select="onLocationSearchSelect"
    />

    <!-- ─── Tool components (hidden triggers, only sheets/logic) ─── -->

    <MapSettingsPanel
      ref="mapSettingsTool"
      :model-value="mapThemeSelected"
      :themes="mapThemes"
      :layer-states="layerStatesForPanel"
      @update:model-value="onMapThemeChangeEvent"
      @toggle-layer="onToggleLayer"
      @change-layer-opacity="onLayerOpacityChange"
      @reset-settings="onResetMapSettings"
      @tool-opened="onToolOpened('mapSettingsTool')"
      @tool-closed="onToolClosed"
    />

    <AnimateMap
      ref="animateTool"
      :map="overlayMap"
      @animation-start="onAnimationStartEvent"
      @animation-finished="onAnimationFinishedEvent"
      @animation-stop="onAnimationStopEvent"
      @animate="onAnimateEvent"
      @tool-opened="onToolOpened('animateTool')"
      @tool-closed="onToolClosed"
    />

    <MeasureBetweenPoints
      ref="measureTool"
      :map="overlayMap"
      @active-changed="onMeasureActiveChanged"
      @tool-opened="onToolOpened('measureTool')"
      @tool-closed="onToolClosed"
      @show-track-details="onMeasureShowTrackDetails"
    />

    <PlannerTool
      ref="plannerTool"
      :map="overlayMap"
      @active-changed="onPlannerActiveChanged"
      @tool-opened="onToolOpened('plannerTool')"
      @tool-closed="onToolClosed"
    />

    <Statistics
      ref="statistics"
      :tracks="trackBrowserTracks"
      :tracks-count="visibleTrackCount"
      :unfiltered-total="totalTrackCount"
      :selected-track-id="selectedFeature?.properties?.id ?? null"
      @select-track="onTrackBrowserSelect"
      @open-details="onTrackBrowserOpenDetails"
      @tool-opened="onToolOpened('statistics')"
      @tool-closed="onToolClosed"
    />

    <GpsLocate
      ref="gpsLocate"
      @location-update="onLocationUpdate"
      @device-enabled-disabled="onGPSDeviceEnabledDisabled"
    />

    <Filter
      ref="filterTool"
      :palette="colorPalette"
      :total-track-count="totalTrackCount"
      :visible-track-count="visibleTrackCount"
      @filter-applied-event="onFilterApplied"
      @filter-style-changed="onFilterStyleChanged"
      @tool-opened="onToolOpened('filterTool')"
      @tool-closed="onToolClosed"
      @start-geo-drawing="onStartGeoDrawing"
      @clear-geo-shape="onClearGeoShape"
    />

    <!-- ─── Top-right anchor: unified chip + legend card ─── -->
    <div class="mtl-top-right">
      <MapLegend
        :entries="legendEntries"
        :legend-mode="legendMode"
        :gradient-colors="legendGradientColors"
        :gradient-bucket-count="legendGradientBucketCount"
        :collapsed="legendCollapsed"
        :visible-track-count="visibleTrackCount"
        :total-track-count="totalTrackCount"
        :filter-active="filterActive"
        :hidden-groups="hiddenGroups"
        @update:collapsed="onLegendCollapsed"
        @update:hidden-groups="onHiddenGroupsChanged"
        @chip-click="onToolSelect('filter')"
      />
    </div>

    <!-- ─── Top progress bar ─── -->
    <transition name="bar-fade">
      <div v-if="showLoader || loadingTrackBatches || loadingTracks10m" class="mtl-progress-bar"></div>
    </transition>

    <!-- ─── Admin (managed via NavigationSheet, same as all tools) ─── -->
    <AdminDialog
      ref="adminTool"
      @tool-opened="onToolOpened('adminTool')"
      @tool-closed="onToolClosed"
      @reload-tracks="onAdminReloadTracks"
      @refresh-freshness-data="onAdminRefreshFreshnessData"
    />

    <!-- ─── Offline banner ─── -->
    <transition name="fade">
      <div v-if="isOffline" class="mtl-offline"><i class="bi bi-wifi-off"></i> Offline — displaying cached tracks</div>
    </transition>

    <!-- ─── Map download banner ─── -->
    <transition name="fade">
      <div v-if="mapServerStatus && !mapServerStatus.ready" class="mtl-map-downloading">
        <div class="mtl-map-downloading-header">
          <i :class="mapServerStatus.phase === 'downloading' ? 'bi bi-cloud-download' : 'bi bi-gear'"></i>
          <span>{{
            mapServerStatus.phase === 'downloading'
              ? 'Downloading map tiles…'
              : mapServerStatus.phase === 'extracting'
                ? 'Processing map tiles…'
                : 'Preparing map…'
          }}</span>
        </div>
        <div
          v-if="mapServerStatus.phase === 'downloading' && mapServerStatus.download_total > 0"
          class="mtl-map-downloading-progress"
        >
          <div class="mtl-map-downloading-bar-track">
            <div class="mtl-map-downloading-bar-fill" :style="{ width: mapServerStatus.download_pct + '%' }"></div>
          </div>
          <span class="mtl-map-downloading-pct">{{ mapServerStatus.download_pct }}%</span>
        </div>
        <div v-if="mapServerStatus.message" class="mtl-map-downloading-msg">{{ mapServerStatus.message }}</div>
      </div>
    </transition>

    <!-- ─── Data freshness banner ─── -->
    <transition name="fade">
      <div v-if="showDataFreshnessBanner" class="mtl-data-freshness">
        <div class="mtl-data-freshness__content">
          <i class="bi bi-arrow-repeat"></i>
          <div class="mtl-data-freshness__text">
            <div class="mtl-data-freshness__title">New data available</div>
            <div class="mtl-data-freshness__detail">Tracks, media, or settings changed since this view loaded.</div>
          </div>
        </div>
        <div class="mtl-data-freshness__actions">
          <button
            class="mtl-data-freshness__btn mtl-data-freshness__btn--primary"
            :disabled="freshnessReloading"
            @click="onMapFreshnessBrowserReload"
          >
            <i class="bi bi-arrow-clockwise"></i>
            <span>Reload</span>
          </button>
          <button class="mtl-data-freshness__btn" :disabled="freshnessReloading" @click="onDataFreshnessDismiss">
            Dismiss
          </button>
        </div>
      </div>
    </transition>

    <!-- ─── Track details bottom sheet ─── -->
    <BottomSheet
      v-model="trackDetailsVisible"
      :detents="trackDetailsDetents"
      :initial-detent="trackDetailsInitialDetent"
      :z-index="5300"
      @closed="onTrackDetailsSheetClosed"
    >
      <template #title>
        <div class="td-sheet-header">
          <span class="td-title-label"
            ><i class="bi bi-info-circle"></i><span class="td-title-text">Track Details</span></span
          >
          <span v-if="trackDetailsInfo.id" class="td-sheet-id" @pointerdown.stop @mousedown.stop @touchstart.stop
            >#{{ trackDetailsInfo.id }}</span
          >
          <ActivityTypeBadge
            v-if="trackDetailsInfo.activityType"
            :type="trackDetailsInfo.activityType"
            size="xs"
            colored
            class="td-sheet-activity"
          />
        </div>
      </template>
      <TrackDetails v-if="trackDetailsId != null" :gps-track-id="trackDetailsId" @track-loaded="onTrackDetailsLoaded" />
    </BottomSheet>

    <!-- ─── Media photo bottom sheet ─── -->
    <BottomSheet
      v-model="mediaSheetVisible"
      :detents="[
        { id: 'small', height: '40vh' },
        { id: 'medium', height: '70vh' },
        { id: 'large', height: '92vh' },
      ]"
      initial-detent="large"
      :z-index="5050"
      title="Photo"
      icon="bi bi-image"
      header-mode="compact"
      :no-backdrop="false"
      @closed="closeMediaSheet"
    >
      <MediaPreview
        :media-id="mediaSheetMediaId"
        :can-go-prev="mediaPrevId != null"
        :can-go-next="mediaNextId != null"
        :nav-index="mediaCurrentIndex >= 0 ? mediaCurrentIndex + 1 : 0"
        :nav-total="mediaNavList.length"
        :prefetch-ids="[mediaPrevId, mediaNextId]"
        @prev="navigateMediaTo(mediaPrevId)"
        @next="navigateMediaTo(mediaNextId)"
      />
    </BottomSheet>

    <!-- ─── Track selection bottom sheet ─── -->
    <BottomSheet
      v-model="trackSelectionSheetVisible"
      :detents="[
        { id: 'compact', height: '35vh' },
        { id: 'medium', height: '50vh' },
        { id: 'expanded', height: '65vh' },
      ]"
      initial-detent="compact"
      :z-index="4900"
      :title="selectionPopupTracks.length + ' tracks — select for details'"
      icon="bi bi-card-list"
      header-mode="compact"
      :no-backdrop="false"
      @closed="closeSelectionPopup"
    >
      <div class="track-selection-sheet">
        <div class="track-selection-sheet__scroll">
          <ul class="track-selection-list">
            <li
              v-for="track in selectionPopupTracks"
              :key="track.id"
              class="mtl-track-pick"
              @click="onPopupTrackSelect(track.id)"
            >
              <TrackShapePreview
                :track-id="track.id"
                :width="48"
                :height="32"
                :padding="3"
                class="mtl-track-pick__shape"
              />
              <div class="mtl-track-pick__content">
                <div class="mtl-track-pick__primary">{{ track.displayName }}</div>
                <div class="mtl-track-pick__secondary">
                  <ActivityTypeBadge v-if="track.activityType" :type="track.activityType" size="xs" />
                  <span class="mtl-track-pick__date">{{ track.date }}</span>
                  <span v-if="track.description" class="mtl-track-pick__description">{{ track.description }}</span>
                </div>
              </div>
              <i class="bi bi-chevron-right mtl-track-pick__chevron"></i>
            </li>
          </ul>
        </div>
      </div>
    </BottomSheet>

    <!-- ─── Swiss Mobility route info popup ─── -->
    <div
      v-if="swissMobilityPopup.visible"
      class="swiss-mobility-popup"
      :style="{ left: swissMobilityPopup.pos.x + 'px', top: swissMobilityPopup.pos.y + 'px' }"
    >
      <div class="swiss-mobility-popup-close" @click="closeSwissMobilityPopup">&times;</div>
      <div class="swiss-mobility-popup-header"><i class="bi bi-signpost-split"></i> Nearby Routes</div>
      <ul class="swiss-mobility-route-list">
        <li v-for="(route, i) in swissMobilityPopup.routes" :key="i" class="swiss-mobility-route-item">
          <i :class="route.icon" class="swiss-mobility-route-icon"></i>
          <div class="swiss-mobility-route-info">
            <span class="swiss-mobility-route-type">{{ route.type }}</span>
            <span class="swiss-mobility-route-name">{{ route.name }}</span>
          </div>
          <span v-if="route.number" class="swiss-mobility-route-number">#{{ route.number }}</span>
        </li>
      </ul>
    </div>

    <!-- ─── Geo drawing toolbar ─── -->
    <transition name="fade">
      <div v-if="geoDrawingParamDef" class="geo-draw-toolbar">
        <div class="geo-draw-toolbar__header">
          <i :class="geoDrawToolbarIcon"></i>
          <span>{{ geoDrawToolbarLabel }}</span>
        </div>
        <div class="geo-draw-toolbar__hint">{{ geoDrawToolbarHint }}</div>
        <div class="geo-draw-toolbar__actions">
          <button
            class="geo-draw-toolbar__btn geo-draw-toolbar__btn--undo"
            :disabled="!geoDrawCanUndo"
            @click="onGeoDrawUndo"
          >
            <i class="bi bi-arrow-counterclockwise"></i> Undo
          </button>
          <button
            v-if="geoDrawIsPolygon"
            class="geo-draw-toolbar__btn geo-draw-toolbar__btn--finish"
            :disabled="!geoDrawCanFinish"
            @click="onGeoDrawFinish"
          >
            <i class="bi bi-check-lg"></i> Finish
          </button>
          <button class="geo-draw-toolbar__btn geo-draw-toolbar__btn--cancel" @click="onGeoDrawCancel">
            <i class="bi bi-x-lg"></i> Cancel
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { inject, ref } from 'vue';
import AdminDialog from '@/components/admin/AdminDialog.vue';
import GpsLocate from '@/components/gps/GpsLocate.vue';
import MeasureBetweenPoints from '@/components/measure/MeasureBetweenPoints.vue';
import PlannerTool from '@/planner/components/PlannerTool.vue';
import AnimateMap from '@/components/animate/AnimateMap.vue';
import Filter from '@/components/filter/Filter.vue';
import Statistics from '@/components/statistics/Statistics.vue';
import TrackDetails from '@/components/trackdetails/TrackDetails.vue';
import NavigationSheet from '@/components/ui/NavigationSheet.vue';
import MapSettingsPanel from '@/components/map/MapSettingsPanel.vue';
import MapLegend from '@/components/map/MapLegend.vue';
import LocationSearchSheet from '@/components/map/LocationSearchSheet.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import MediaPreview from '@/components/map/MediaPreview.vue';
import { useMainMapController } from '@/components/map/useMainMapController';

const props = withDefaults(defineProps<{ fromLogin?: boolean }>(), { fromLogin: false });
const emit = defineEmits<{
  'tracks-loaded': [];
  'load-failed': [];
  syncing: [value: boolean];
}>();

type ToastLike = { add: (options: { severity: string; summary: string; detail?: string; life?: number }) => void };
const toast = inject<ToastLike>('toast', { add: () => undefined });

const mapBaseContainer = ref(null);
const mapOverlayContainer = ref(null);
const navSheet = ref(null);
const mapSettingsTool = ref(null);
const animateTool = ref(null);
const measureTool = ref(null);
const plannerTool = ref(null);
const statistics = ref(null);
const gpsLocate = ref(null);
const filterTool = ref(null);
const adminTool = ref(null);

const {
  overlayMap,
  mapServerStatus,
  showLoader,
  loadingTrackBatches,
  loadingTracks10m,
  mapThemes,
  mapThemeSelected,
  visibleTrackCount,
  totalTrackCount,
  filterActive,
  colorPalette,
  legendEntries,
  legendMode,
  legendGradientColors,
  legendGradientBucketCount,
  legendCollapsed,
  hiddenGroups,
  selectedFeature,
  trackSelectionSheetVisible,
  swissMobilityPopup,
  trackDetailsVisible,
  trackDetailsDetents,
  trackDetailsInitialDetent,
  trackDetailsId,
  trackDetailsInfo,
  locationSearchVisible,
  activeToolId,
  toolDefs,
  mediaSheetVisible,
  mediaSheetMediaId,
  mediaNavList,
  isOffline,
  freshnessReloading,
  geoDrawingParamDef,
  selectionPopupTracks,
  baseMapStyle,
  layerStatesForPanel,
  mediaCurrentIndex,
  mediaPrevId,
  mediaNextId,
  showLocationSearchFab,
  locationSearchMapCenter,
  trackBrowserTracks,
  alertToolIds,
  driftedToolIds,
  geoDrawToolbarIcon,
  geoDrawToolbarLabel,
  geoDrawToolbarHint,
  geoDrawIsPolygon,
  geoDrawCanUndo,
  geoDrawCanFinish,
  showDataFreshnessBanner,
  openLocationSearch,
  onLocationSearchSelect,
  onDataFreshnessDismiss,
  onResetMapSettings,
  onToggleLayer,
  onLayerOpacityChange,
  onLocationUpdate,
  onGPSDeviceEnabledDisabled,
  onAnimationStartEvent,
  onAnimationFinishedEvent,
  onAnimationStopEvent,
  onAnimateEvent,
  onMapThemeChangeEvent,
  onMapFreshnessBrowserReload,
  onAdminReloadTracks,
  onAdminRefreshFreshnessData,
  onMeasureShowTrackDetails,
  onMeasureActiveChanged,
  onPlannerActiveChanged,
  onToolSelect,
  onToolOpened,
  onToolClosed,
  closeSwissMobilityPopup,
  closeSelectionPopup,
  navigateMediaTo,
  closeMediaSheet,
  onPopupTrackSelect,
  onTrackDetailsSheetClosed,
  onTrackDetailsLoaded,
  onTrackBrowserSelect,
  onTrackBrowserOpenDetails,
  onLegendCollapsed,
  onHiddenGroupsChanged,
  onStartGeoDrawing,
  onGeoDrawUndo,
  onGeoDrawFinish,
  onGeoDrawCancel,
  onClearGeoShape,
  onFilterApplied,
  onFilterStyleChanged,
} = useMainMapController(props, emit, toast, {
  mapBaseContainer,
  mapOverlayContainer,
  navSheet,
  mapSettingsTool,
  animateTool,
  measureTool,
  plannerTool,
  statistics,
  gpsLocate,
  filterTool,
  adminTool,
});
</script>

<style scoped>
* {
  margin: 0;
  box-sizing: border-box;
}

.container {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  background-color: var(--map-container-bg);
  align-items: stretch;
  position: relative;
  z-index: 0;
}

/* Desktop: offset the whole map container for the nav panel */
@media (min-width: 1024px) {
  .container {
    margin-left: var(--nav-panel-w);
    width: calc(100% - var(--nav-panel-w));
    transition:
      margin-left 0.3s ease,
      width 0.3s ease;
  }
}

.map-base,
.map-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/* Desktop: position overlays relative to map area */

.map-base {
  pointer-events: none;
}

.map-overlay :deep(.maplibregl-canvas) {
  background: transparent !important;
}

.map-base :deep(.maplibregl-control-container) {
  display: none;
}

/* ─── Globe control button ─── */
.map-overlay :deep(.mtl-globe-btn) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 29px;
  height: 29px;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  color: var(--text-secondary);
  font-size: var(--text-base-size);
  transition:
    color 0.15s,
    background 0.15s;
}
.map-overlay :deep(.mtl-globe-btn:hover) {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.map-overlay :deep(.mtl-globe-btn.mtl-globe-active) {
  color: #3b82f6;
}
.map-overlay :deep(.mtl-globe-btn.mtl-globe-active:hover) {
  color: #2563eb;
}

/* ─── Top progress bar ─── */
.mtl-progress-bar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 8px;
  z-index: var(--z-tool-overlay);
  overflow: hidden;
  background: rgba(99, 102, 241, 0.18);
}
.mtl-progress-bar::after {
  content: '';
  position: absolute;
  top: 0;
  left: -55%;
  width: 55%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--accent-muted) 15%,
    var(--accent) 40%,
    var(--accent-text-light) 60%,
    var(--accent-muted) 85%,
    transparent 100%
  );
  animation: progress-shimmer 1.4s ease-in-out infinite;
}
@keyframes progress-shimmer {
  0% {
    left: -60%;
  }
  100% {
    left: 120%;
  }
}
.bar-fade-enter-active,
.bar-fade-leave-active {
  transition: opacity 0.3s ease;
}
.bar-fade-enter-from,
.bar-fade-leave-to {
  opacity: 0;
}

/* ─── Transitions ─── */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.4s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active {
  transition:
    transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1),
    opacity 0.25s ease;
}
.slide-up-leave-active {
  transition:
    transform 0.25s ease,
    opacity 0.2s ease;
}
.slide-up-enter-from {
  transform: translateY(100%);
  opacity: 0;
}
.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* ─── Top-right anchor (chip + legend) ─── */
.mtl-top-right {
  position: fixed;
  z-index: var(--z-map-overlay);
  top: calc(0.6rem + var(--safe-top, 0px));
  right: calc(0.6rem + var(--safe-right, 0px));
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
  pointer-events: none;
}

.mtl-location-search-fab {
  position: fixed;
  z-index: var(--z-map-overlay-raised);
  right: calc(0.85rem + var(--safe-right, 0px));
  bottom: calc(var(--nav-sheet-h, 92px) + 0.95rem + var(--safe-bottom, 0px));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border: 1px solid var(--border-medium);
  border-radius: 50%;
  background: var(--surface-glass-light);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  color: var(--text-primary);
  box-shadow: var(--shadow-lg);
  cursor: pointer;
  font-size: 1.15rem;
  transition:
    transform 0.15s ease,
    background 0.15s ease,
    border-color 0.15s ease;
}

.mtl-location-search-fab:hover {
  transform: translateY(-1px);
  background: var(--surface-glass-heavy);
  border-color: color-mix(in srgb, var(--accent-muted) 60%, var(--border-medium));
}

.mtl-location-search-fab:active {
  transform: translateY(0);
}

@media (min-width: 1024px) {
  .mtl-location-search-fab {
    bottom: calc(1.2rem + var(--safe-bottom, 0px));
  }
}

/* ─── Admin FAB ─── */
.mtl-admin-fab {
  position: fixed;
  z-index: var(--z-map-overlay-raised);
  bottom: calc(var(--nav-sheet-h, 92px) + 0.5rem);
  right: calc(0.75rem + var(--safe-right, 0px));
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 0.75rem;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass-light);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  color: var(--text-muted);
  font-size: var(--text-base-size);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transition: all 0.2s;
}
.mtl-admin-fab:hover {
  background: var(--surface-glass-subtle);
  color: var(--text-secondary);
  transform: scale(1.08);
}

/* ─── Offline banner ─── */
.mtl-offline {
  position: fixed;
  z-index: var(--z-map-overlay);
  left: 50%;
  top: calc(4rem + var(--safe-top, 0px));
  transform: translateX(-50%);
  background: var(--error-heavy);
  backdrop-filter: var(--blur-light);
  -webkit-backdrop-filter: var(--blur-light);
  color: var(--text-primary);
  border-radius: 2rem;
  padding: 0.4rem 1rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.3);
  white-space: nowrap;
}

/* ─── Map download progress banner ─── */
.mtl-map-downloading {
  position: fixed;
  z-index: var(--z-map-overlay);
  left: 50%;
  top: calc(4rem + var(--safe-top, 0px));
  transform: translateX(-50%);
  background: var(--surface-glass);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid var(--border-medium);
  color: var(--text-secondary);
  border-radius: 1rem;
  padding: 0.5rem 1rem 0.6rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  min-width: 220px;
  max-width: 320px;
}
.mtl-map-downloading-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-primary);
}
.mtl-map-downloading-progress {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.mtl-map-downloading-bar-track {
  flex: 1;
  height: 5px;
  border-radius: 3px;
  background: var(--border-medium);
  overflow: hidden;
}
.mtl-map-downloading-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--accent);
  transition: width 0.8s ease;
}
.mtl-map-downloading-pct {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  flex-shrink: 0;
}
.mtl-map-downloading-msg {
  font-size: var(--text-xs-size);
  font-weight: 400;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── Data freshness banner ─── */
.mtl-data-freshness {
  position: fixed;
  z-index: var(--z-map-overlay);
  left: 50%;
  bottom: calc(var(--nav-sheet-h, 92px) + 0.8rem + var(--safe-bottom, 0px));
  transform: translateX(-50%);
  width: min(calc(100vw - 2rem), 760px);
  background: rgba(255, 251, 235, 0.94);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid rgba(245, 158, 11, 0.42);
  border-radius: 0.75rem;
  color: #3f2e08;
  box-shadow: 0 8px 24px rgba(40, 30, 5, 0.2);
  padding: 0.55rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
}
.mtl-data-freshness__content {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
  flex: 1 1 auto;
}
.mtl-data-freshness__content > i {
  color: #b45309;
  font-size: 1rem;
  flex: 0 0 auto;
}
.mtl-data-freshness__text {
  min-width: 0;
  flex: 1 1 auto;
}
.mtl-data-freshness__title {
  font-size: var(--text-sm-size);
  font-weight: 700;
  line-height: 1.2;
}
.mtl-data-freshness__detail {
  font-size: var(--text-xs-size);
  color: #6f520b;
  line-height: 1.25;
  white-space: normal;
}
.mtl-data-freshness__actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex: 0 0 auto;
}
.mtl-data-freshness__btn {
  border: 1px solid rgba(180, 83, 9, 0.28);
  background: rgba(255, 255, 255, 0.72);
  color: #5b3a05;
  border-radius: 0.45rem;
  min-height: 2rem;
  padding: 0 0.6rem;
  font-size: var(--text-xs-size);
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  cursor: pointer;
  white-space: nowrap;
}
.mtl-data-freshness__btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.95);
}
.mtl-data-freshness__btn:disabled {
  opacity: 0.55;
  cursor: default;
}
.mtl-data-freshness__btn--primary {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
}
.mtl-data-freshness__btn--primary:hover:not(:disabled) {
  background: #115e59;
}

@media (max-width: 640px) {
  .mtl-data-freshness {
    align-items: stretch;
    flex-direction: column;
    bottom: calc(var(--nav-sheet-h, 92px) + 0.65rem + var(--safe-bottom, 0px));
    gap: 0.55rem;
  }
  .mtl-data-freshness__detail {
    white-space: normal;
  }
  .mtl-data-freshness__actions {
    justify-content: flex-end;
  }
}

/* ─── Track details sheet header ─── */
.td-sheet-header {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}

/* Title label — same visual style as the BottomSheet .sheet-title */
.td-title-label {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #5f6368;
  white-space: nowrap;
  flex-shrink: 0;
}

.td-sheet-id {
  flex-shrink: 0;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 4px;
  padding: 1px 6px;
  font-family: monospace;
  white-space: nowrap;
  cursor: text;
  -webkit-user-select: text;
  user-select: text;
}

.td-sheet-activity {
  flex: 0 1 auto;
  min-width: 0;
  max-width: min(14rem, 36vw);
}

.td-sheet-activity :deep(.activity-badge__label) {
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 640px) {
  .td-sheet-header {
    gap: 0.45rem;
  }

  .td-sheet-activity {
    max-width: 1.75rem;
  }

  .td-sheet-activity :deep(.activity-badge__label) {
    display: none;
  }
}

/* ─── Track selection sheet ─── */
.track-selection-sheet {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  width: 100%;
}

.track-selection-sheet__scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0 0.9rem 0.85rem;
}

.track-selection-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.mtl-track-pick {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.55rem 0.75rem;
  cursor: pointer;
  border: 1px solid var(--border-subtle);
  border-radius: 0.95rem;
  background: linear-gradient(135deg, var(--surface-glass-heavy), var(--surface-glass-subtle));
  color: var(--text-secondary);
  transition:
    transform 0.15s,
    background 0.12s,
    border-color 0.12s,
    color 0.12s;
}
.mtl-track-pick:hover {
  transform: translateY(-1px);
  background: color-mix(in srgb, var(--accent-bg) 65%, var(--surface-glass-heavy));
  border-color: color-mix(in srgb, var(--accent-muted) 55%, var(--border-default));
  color: var(--text-primary);
}

.mtl-track-pick__content {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}
.mtl-track-pick__shape {
  flex-shrink: 0;
  opacity: 0.7;
}
.mtl-track-pick:hover .mtl-track-pick__shape {
  opacity: 1;
}
.mtl-track-pick__primary {
  min-width: 0;
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mtl-track-pick__secondary {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.mtl-track-pick__date {
  flex: 0 0 auto;
  font-weight: 600;
  white-space: nowrap;
  color: inherit;
}
.mtl-track-pick__description {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mtl-track-pick__description::before {
  content: '•';
  margin-right: 0.35rem;
}

.mtl-track-pick__chevron {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--text-base-size);
}

/* ─── Swiss Mobility route info popup ─── */
.swiss-mobility-popup {
  position: absolute;
  z-index: var(--z-loading);
  background: var(--surface-glass-heavy);
  backdrop-filter: var(--blur-heavy);
  -webkit-backdrop-filter: var(--blur-heavy);
  border: 1px solid var(--border-medium);
  border-radius: 0.75rem;
  box-shadow: var(--shadow-lg);
  max-width: 300px;
  min-width: 180px;
}
.swiss-mobility-popup-close {
  position: absolute;
  top: 4px;
  right: 8px;
  cursor: pointer;
  font-size: var(--text-lg-size);
  color: var(--text-muted);
  z-index: 1;
  transition: color 0.15s;
}
.swiss-mobility-popup-close:hover {
  color: var(--text-primary);
}
.swiss-mobility-popup-header {
  font-size: var(--text-xs-size);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-faint);
  padding: 0.4rem 0.85rem 0.3rem;
  border-bottom: 1px solid var(--border-subtle);
}
.swiss-mobility-route-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.swiss-mobility-route-item {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.45rem 0.85rem;
  border-bottom: 1px solid var(--border-subtle);
}
.swiss-mobility-route-item:last-child {
  border-bottom: none;
}
.swiss-mobility-route-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--text-secondary);
}
.swiss-mobility-route-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.swiss-mobility-route-type {
  font-size: var(--text-2xs-size);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--text-faint);
}
.swiss-mobility-route-name {
  font-size: var(--text-sm-size);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.swiss-mobility-route-number {
  flex-shrink: 0;
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-faint);
  margin-left: auto;
  padding-left: 0.4rem;
}
</style>

<!-- Track point popup styles (unscoped — MapLibre popups live outside component root) -->
<style>
.mtl-location-search-marker {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.1rem;
  height: 2.1rem;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: var(--accent);
  color: #ffffff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.35);
  font-size: 1.1rem;
}

.mtl-location-search-marker__clear {
  position: absolute;
  top: -0.55rem;
  right: -0.55rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.1rem;
  height: 1.1rem;
  border: 1px solid rgba(15, 23, 42, 0.18);
  border-radius: 50%;
  background: #ffffff;
  color: #334155;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.2);
  cursor: pointer;
  font-size: 0.85rem;
  line-height: 1;
  padding: 0;
}

.mtl-location-search-marker__clear:hover {
  background: #f8fafc;
  color: #0f172a;
}

.mtl-location-search-marker::after {
  content: '';
  position: absolute;
  bottom: -0.35rem;
  left: 50%;
  width: 0.65rem;
  height: 0.65rem;
  background: var(--accent);
  border-right: 2px solid #ffffff;
  border-bottom: 2px solid #ffffff;
  transform: translateX(-50%) rotate(45deg);
}

.mtl-point-popup-container .maplibregl-popup-content {
  padding: 0;
  border-radius: 0.6rem;
  background: var(--surface-glass-heavy);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid var(--border-medium);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}
.mtl-point-popup-container .maplibregl-popup-close-button {
  font-size: var(--text-base-size);
  color: var(--text-muted);
  padding: 4px 8px;
  line-height: var(--text-base-lh);
}
.mtl-point-popup-container .maplibregl-popup-close-button:hover {
  color: var(--text-primary);
  background: transparent;
}
.mtl-point-popup-container .maplibregl-popup-tip {
  border-top-color: var(--surface-glass-heavy);
}
.mtl-point-popup {
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  -webkit-user-select: text;
  user-select: text;
}
.mtl-point-popup-header {
  padding: 0.35rem 0.6rem;
  font-weight: 700;
  font-size: var(--text-xs-size);
  color: var(--text-primary);
  background: var(--surface-elevated);
  border-bottom: 1px solid var(--border-subtle);
  cursor: text;
  -webkit-user-select: text;
  user-select: text;
}
.mtl-point-popup-table {
  width: 100%;
  border-collapse: collapse;
}
.mtl-point-popup-table tr:not(:last-child) {
  border-bottom: 1px solid var(--border-subtle);
}
.mtl-point-popup-table td {
  padding: 0.2rem 0.6rem;
  vertical-align: top;
}
.mtl-pp-label {
  color: var(--text-muted);
  white-space: nowrap;
  padding-right: 0.8rem;
  font-size: var(--text-2xs-size);
}
.mtl-pp-value {
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
  font-weight: 500;
}

/* ── Geo drawing toolbar ── */
.geo-draw-toolbar {
  position: fixed;
  top: calc(0.6rem + var(--safe-top, 0px));
  left: 50%;
  transform: translateX(-50%);
  z-index: var(--z-tool-overlay);
  background: var(--surface-glass);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid var(--border-medium);
  border-radius: 12px;
  padding: 0.6rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 200px;
  pointer-events: auto;
}
.geo-draw-toolbar__header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: var(--text-base-size);
  font-weight: 600;
  color: var(--text-primary);
}
.geo-draw-toolbar__header i {
  font-size: var(--text-base-size);
  color: var(--accent);
}
.geo-draw-toolbar__hint {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  text-align: center;
}
.geo-draw-toolbar__actions {
  display: flex;
  gap: 0.4rem;
  margin-top: 0.2rem;
}
.geo-draw-toolbar__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.65rem;
  border: 1px solid var(--border-medium);
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  cursor: pointer;
  transition:
    background 0.15s,
    opacity 0.15s;
}
.geo-draw-toolbar__btn:hover:not(:disabled) {
  background: var(--surface-hover);
}
.geo-draw-toolbar__btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.geo-draw-toolbar__btn--finish {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--text-inverse);
}
.geo-draw-toolbar__btn--finish:hover:not(:disabled) {
  background: var(--accent-text-light);
}
.geo-draw-toolbar__btn--cancel {
  color: var(--error);
  border-color: color-mix(in srgb, var(--error) 32%, transparent);
}
.geo-draw-toolbar__btn--cancel:hover {
  background: var(--error-bg);
}
</style>

<!-- Unscoped: dropdown panels from BottomSheet need higher z-index -->
<style>
.p-dropdown-panel,
.p-overlaypanel,
.p-datepicker,
.p-multiselect-panel,
.p-autocomplete-panel,
.p-tieredmenu,
.p-contextmenu,
.p-tooltip {
  z-index: var(--z-popup-over-bottomsheet) !important;
}
</style>
