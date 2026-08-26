<template>
  <div class="container">
    <div ref="mapContainer" class="map"></div>

    <!-- ─── Navigation sheet (bottom bar mobile / left panel desktop) ─── -->
    <NavigationSheet
      ref="navSheet"
      :tools="toolDefs"
      :active-tool="activeToolId"
      :primary-ids="['stats', 'filter', 'planner', 'map']"
      :alert-tool-ids="alertToolIds"
      :drifted-tool-ids="driftedToolIds"
      @select="onNavigationToolSelect"
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
      :map-source-mode="mapSourceMode"
      :themes="mapThemesForPanel"
      :layer-states="layerStatesForPanel"
      :terrain-exaggeration="terrainExaggeration"
      @update:model-value="onMapThemeChangeEvent"
      @update:map-source-mode="onMapSourceModeChangeEvent"
      @set-terrain-mode-enabled="onSetTerrainModeEnabled"
      @change-terrain-exaggeration="onTerrainExaggerationChange"
      @toggle-layer="onToggleLayer"
      @change-layer-opacity="onLayerOpacityChange"
      @reset-settings="onResetMapSettings"
      @tool-opened="onToolOpened('mapSettingsTool')"
      @tool-closed="onToolClosed"
    />

    <AnimateMap
      ref="animateTool"
      :map="overlayMap"
      :geojson="geojson"
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
      @open-track-photos="onTrackBrowserOpenPhotos"
      @open-media-on-map="openMediaFromStatisticsOnMainMap"
      @open-filter="onNavigationToolSelect('filter')"
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
      @select-track="onTrackBrowserSelect"
      @open-details="onTrackBrowserOpenDetails"
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
        :active-filter-identity="activeFilterIdentity"
        :hidden-groups="hiddenGroups"
        @update:collapsed="onLegendCollapsed"
        @update:hidden-groups="onHiddenGroupsChanged"
        @chip-click="onNavigationToolSelect('filter')"
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

    <MapConfigFallbackNotice
      v-if="showMapConfigFallbackNotice"
      :retrying="mapConfigRetrying"
      @retry="retryMapConfig"
      @dismiss="mapConfigFallbackDismissed = true"
    />

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
          v-if="mapServerStatus.phase === 'downloading' && (mapServerStatus.downloadTotal ?? 0) > 0"
          class="mtl-map-downloading-progress"
        >
          <div class="mtl-map-downloading-bar-track">
            <div class="mtl-map-downloading-bar-fill" :style="{ width: mapServerStatus.downloadPct + '%' }"></div>
          </div>
          <span class="mtl-map-downloading-pct">{{ mapServerStatus.downloadPct }}%</span>
        </div>
        <div v-if="mapServerStatus.message" class="mtl-map-downloading-msg">{{ mapServerStatus.message }}</div>
      </div>
    </transition>

    <!-- ─── Data freshness banner ─── -->
    <Teleport to="body">
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
            <button
              type="button"
              class="mtl-data-freshness__btn"
              :disabled="freshnessReloading"
              @click="onDataFreshnessDismiss"
            >
              Dismiss
            </button>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- ─── Track details bottom sheet ─── -->
    <BottomSheet
      v-model="trackDetailsVisible"
      :detents="trackDetailsDetents"
      :initial-detent="trackDetailsInitialDetent"
      :selected-detent="trackDetailsSelectedDetent"
      :background-detent="trackDetailsBackgroundDetent"
      :z-index="5300"
      @closed="onTrackDetailsSheetClosedAfterTransition"
    >
      <template #title>
        <div class="td-sheet-header">
          <span class="td-title-label"
            ><i class="bi bi-info-circle"></i><span class="td-title-text">Track Details</span></span
          >
          <span v-if="trackDetailsInfo.id" class="td-sheet-id" title="Select TrackID to copy" @click.stop
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
      <TrackDetails
        v-if="trackDetailsId != null"
        :gps-track-id="trackDetailsId"
        :initial-tab="trackDetailsInitialTab"
        @back="closeTrackDetailsFromPanel"
        @track-loaded="onTrackDetailsLoaded"
        @navigate-track="syncTrackDetailRoute"
        @start-3d-replay="start3dTrackReplay"
        @open-media-on-map="openTrackMediaOnMainMap"
      />
    </BottomSheet>

    <TrackReplayControls
      :active="trackReplayActive"
      :auto-follow="trackReplayAutoFollow"
      :camera-preset="trackReplayCameraPreset"
      :camera-smoothness="trackReplayCameraSmoothness"
      :distance-label="trackReplayDistanceLabel"
      :duration-seconds="trackReplayDurationSeconds"
      :elapsed-label="trackReplayElapsedLabel"
      :loading="trackReplayLoading"
      :playing="trackReplayPlaying"
      :progress="trackReplayProgress"
      :show-context-tracks="trackReplayShowContextTracks"
      :show-telemetry="trackReplayShowTelemetry"
      :speed-factor-label="trackReplaySpeedFactorLabel"
      :total-label="trackReplayTotalLabel"
      @toggle-play="toggle3dTrackReplayPlayback"
      @stop="reset3dTrackReplayPlayback"
      @close="stop3dTrackReplay"
      @seek="seek3dTrackReplay"
      @update-show-context-tracks="set3dTrackReplayShowContextTracks"
      @update-show-telemetry="set3dTrackReplayShowTelemetry"
      @update-duration="set3dTrackReplayDuration"
      @update-camera-preset="set3dTrackReplayCameraPreset"
      @update-camera-smoothness="set3dTrackReplayCameraSmoothness"
      @sheet-layout-change="onTrackReplayControlsLayoutChange"
      @recenter="recenter3dTrackReplayCamera"
    />

    <!-- ─── Media collection chooser ─── -->
    <BottomSheet
      v-model="mediaSelectionSheetVisible"
      :detents="[
        { id: 'compact', height: '300px' },
        { id: 'large', height: '82vh' },
      ]"
      initial-detent="compact"
      fit-content-initial
      :z-index="5100"
      title="Open photos"
      icon="bi bi-images"
      header-mode="compact"
      :no-backdrop="false"
      desktop-width="compact"
      sheet-class="media-collection-sheet"
      @closed="closeMediaSelection"
    >
      <MediaCollectionChooser
        v-if="mediaPendingSelection"
        :selection="mediaPendingSelection"
        :track-count="mediaSelectionTrackOptions.length"
        :tracks-loading="mediaSelectionTracksLoading"
        @choose-primary="chooseMediaCollection('primary')"
        @choose-viewport="chooseMediaCollection('viewport')"
        @open-activities="openMediaSelectionActivities"
      />
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
      :title="mediaViewerScopeLabel"
      icon="bi bi-image"
      header-mode="compact"
      :no-backdrop="false"
      no-scroll-hint
      native-fullscreen
      viewport-centered
      :sheet-class="['media-viewer-sheet', mediaViewerThemeClass]"
      @closed="onMediaSheetClosed"
    >
      <template #title>
        <span class="media-preview-sheet-title">
          <i class="bi bi-image" aria-hidden="true"></i>
          <span>{{ mediaViewerScopeLabel }}</span>
          <span class="media-preview-sheet-title__counter">
            {{ mediaCurrentIndex >= 0 ? mediaNavOffset + mediaCurrentIndex + 1 : 0 }} of {{ mediaNavTotal }}
          </span>
        </span>
      </template>
      <template #header-actions>
        <button
          type="button"
          class="media-preview-details-toggle"
          :aria-pressed="mediaPreviewDetailsVisible"
          @click.stop="mediaPreviewDetailsVisible = !mediaPreviewDetailsVisible"
        >
          <i class="bi bi-info-circle" aria-hidden="true"></i>
          <span>Details</span>
        </button>
        <MediaViewerThemeToggle />
      </template>
      <MediaPreview
        :media-id="mediaSheetMediaId"
        :collection-label="mediaViewerFilmstripLabel"
        :can-go-prev="mediaCanGoPrev"
        :can-go-next="mediaCanGoNext"
        :nav-index="mediaCurrentIndex >= 0 ? mediaNavOffset + mediaCurrentIndex + 1 : 0"
        :nav-total="mediaNavTotal"
        :prefetch-ids="[mediaNextId, mediaPrevId]"
        :media-ids="mediaNavigationIds"
        :media-offset="mediaNavOffset"
        :page-loading="mediaNavLoading"
        :position-lat="selectedMapMediaPoint?.lat"
        :position-lng="selectedMapMediaPoint?.lng"
        :overview-bounds="mediaViewerOverviewBounds"
        :details-visible="mediaPreviewDetailsVisible"
        @prev="navigateMediaRelative(-1)"
        @next="navigateMediaRelative(1)"
        @select="navigateMediaTo"
        @request-page="navigateMediaPage"
        @update:details-visible="mediaPreviewDetailsVisible = $event"
        @open-on-map="openSelectedMapMediaOnMainMap"
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
      :title="trackSelectionSheetTitle"
      icon="bi bi-card-list"
      header-mode="compact"
      :no-backdrop="false"
      @closed="closeSelectionPopup"
    >
      <div class="track-selection-sheet">
        <div class="track-selection-sheet__scroll">
          <template v-if="trackSelectionPurpose === 'photos'">
            <section v-if="selectionPopupTracksWithMedia.length > 0" class="track-selection-group">
              <h3 class="track-selection-group__title">
                <span><i class="bi bi-camera-fill" aria-hidden="true"></i> Activities with photos</span>
                <span>{{ selectionPopupTracksWithMedia.length }}</span>
              </h3>
              <NearbyTrackList :tracks="selectionPopupTracksWithMedia" show-media-status @select="onPopupTrackSelect" />
            </section>
            <section v-if="selectionPopupTracksWithoutMedia.length > 0" class="track-selection-group">
              <h3 class="track-selection-group__title">
                <span>Other nearby activities</span>
                <span>{{ selectionPopupTracksWithoutMedia.length }}</span>
              </h3>
              <NearbyTrackList
                :tracks="selectionPopupTracksWithoutMedia"
                show-media-status
                @select="onPopupTrackSelect"
              />
            </section>
          </template>
          <NearbyTrackList v-else :tracks="selectionPopupTracks" @select="onPopupTrackSelect" />
        </div>
      </div>
    </BottomSheet>

    <!-- ─── Swiss Mobility route info popup ─── -->
    <div
      v-if="swissMobilityPopup.visible"
      class="swiss-mobility-popup"
      :style="{ left: swissMobilityPopup.pos.x + 'px', top: swissMobilityPopup.pos.y + 'px' }"
      @click.stop
    >
      <button
        class="swiss-mobility-popup-close"
        type="button"
        aria-label="Close nearby routes popup"
        title="Close"
        @click.stop="closeSwissMobilityPopup"
      >
        <i class="bi bi-x-lg" aria-hidden="true"></i>
      </button>
      <div class="swiss-mobility-popup-header"><i class="bi bi-signpost-split"></i> Nearby Routes</div>
      <ul class="swiss-mobility-route-list">
        <li v-for="(mobilityRoute, i) in swissMobilityPopup.routes" :key="i" class="swiss-mobility-route-item">
          <i :class="mobilityRoute.icon" class="swiss-mobility-route-icon"></i>
          <div class="swiss-mobility-route-info">
            <span class="swiss-mobility-route-type">{{ mobilityRoute.type }}</span>
            <span class="swiss-mobility-route-name">{{ mobilityRoute.name }}</span>
          </div>
          <span v-if="mobilityRoute.number" class="swiss-mobility-route-number">#{{ mobilityRoute.number }}</span>
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
import { computed, inject, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter, type RouteRecordNameGeneric } from 'vue-router';
import AdminDialog from '@/components/admin/AdminDialog.vue';
import GpsLocate from '@/components/gps/GpsLocate.vue';
import MeasureBetweenPoints from '@/components/measure/MeasureBetweenPoints.vue';
import PlannerTool from '@/planner/components/PlannerTool.vue';
import AnimateMap from '@/components/animate/AnimateMap.vue';
import Filter from '@/components/filter/Filter.vue';
import Statistics from '@/components/statistics/Statistics.vue';
import TrackDetails from '@/components/trackdetails/TrackDetails.vue';
import TrackReplayControls from '@/components/replay/TrackReplayControls.vue';
import NavigationSheet from '@/components/ui/NavigationSheet.vue';
import MapSettingsPanel from '@/components/map/MapSettingsPanel.vue';
import MapLegend from '@/components/map/MapLegend.vue';
import MapConfigFallbackNotice from '@/components/map/MapConfigFallbackNotice.vue';
import LocationSearchSheet from '@/components/map/LocationSearchSheet.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import NearbyTrackList from '@/components/map/NearbyTrackList.vue';
import MediaCollectionChooser from '@/components/map/MediaCollectionChooser.vue';
import MediaPreview from '@/components/map/MediaPreview.vue';
import MediaViewerThemeToggle from '@/components/map/MediaViewerThemeToggle.vue';
import { useMediaViewerTheme } from '@/composables/useMediaViewerTheme';
import { useMainMapController } from '@/components/map/useMainMapController';
import { findMediaPointById } from '@/components/map/composables/useMediaAndHeatmap';
import type { MapControllerEmit, MapControllerProps } from '@/components/map/composables/mapControllerRuntime';
import type { ToastService } from '@/types/ui';
import { clearMapConfigCache, type MapConfig } from '@/utils/mapConfigService';

defineOptions({
  name: 'Map2DRenderer',
});

const props = withDefaults(defineProps<MapControllerProps>(), { fromLogin: false });
const emit = defineEmits<MapControllerEmit>();

const toast = inject<ToastService>('toast', { add: () => undefined });
const route = useRoute();
const router = useRouter();
const { mediaViewerThemeClass } = useMediaViewerTheme();

const mapContainer = ref(null);
const navSheet = ref(null);
const mapSettingsTool = ref(null);
const animateTool = ref(null);
const measureTool = ref(null);
const plannerTool = ref(null);
const statistics = ref<{ close?: () => void } | null>(null);
const gpsLocate = ref(null);
const filterTool = ref(null);
const adminTool = ref(null);
const mapConfigFallbackDismissed = ref(false);
const mapConfigRetrying = ref(false);
const MEDIA_PREVIEW_DESKTOP_MIN_WIDTH = 769;

const {
  overlayMap,
  geojson,
  mapConfig,
  mapServerStatus,
  showLoader,
  loadingTrackBatches,
  loadingTracks10m,
  initialLoadDone,
  mapThemesForPanel,
  mapThemeSelected,
  mapSourceMode,
  terrainExaggeration,
  visibleTrackCount,
  totalTrackCount,
  filterActive,
  activeFilterIdentity,
  colorPalette,
  legendEntries,
  legendMode,
  legendGradientColors,
  legendGradientBucketCount,
  legendCollapsed,
  hiddenGroups,
  selectedFeature,
  trackSelectionSheetVisible,
  trackSelectionPurpose,
  swissMobilityPopup,
  trackDetailsVisible,
  trackDetailsBackgroundDetent,
  trackDetailsDetents,
  trackDetailsInitialDetent,
  trackDetailsInitialTab,
  trackDetailsSelectedDetent,
  trackDetailsId,
  trackDetailsInfo,
  trackReplayActive,
  trackReplayLoading,
  trackReplayPlaying,
  trackReplayAutoFollow,
  trackReplayShowContextTracks,
  trackReplayShowTelemetry,
  trackReplayProgress,
  trackReplayDurationSeconds,
  trackReplaySpeedFactorLabel,
  trackReplayCameraPreset,
  trackReplayCameraSmoothness,
  trackReplayDistanceLabel,
  trackReplayElapsedLabel,
  trackReplayTotalLabel,
  locationSearchVisible,
  activeToolId,
  toolDefs,
  mediaSelectionSheetVisible,
  mediaPendingSelection,
  mediaSelectionTrackOptions,
  mediaSelectionTracksLoading,
  mediaSheetVisible,
  mediaSheetMediaId,
  mediaNavList,
  mediaNavTotal,
  mediaNavOffset,
  mediaNavLoading,
  mediaNavScope,
  isOffline,
  freshnessReloading,
  geoDrawingParamDef,
  selectionPopupTracks,
  layerStatesForPanel,
  mediaCurrentIndex,
  mediaCanGoPrev,
  mediaCanGoNext,
  mediaPrevId,
  mediaNextId,
  mediaNavigationIds,
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
  onSetTerrainModeEnabled,
  onTerrainExaggerationChange,
  onToggleLayer,
  onLayerOpacityChange,
  onLocationUpdate,
  onGPSDeviceEnabledDisabled,
  onAnimationStartEvent,
  onAnimationFinishedEvent,
  onAnimationStopEvent,
  onAnimateEvent,
  onMapThemeChangeEvent,
  onMapSourceModeChangeEvent,
  onMapFreshnessBrowserReload,
  onAdminReloadTracks,
  onAdminRefreshFreshnessData,
  onMeasureShowTrackDetails,
  onMeasureActiveChanged,
  onPlannerActiveChanged,
  onToolSelect,
  syncToolToRoute,
  syncTrackDetailRoute,
  onToolOpened,
  onToolClosed,
  closeSwissMobilityPopup,
  closeSelectionPopup,
  chooseMediaCollection,
  openMediaSelectionActivities,
  closeMediaSelection,
  navigateMediaTo,
  navigateMediaRelative,
  navigateMediaPage,
  closeMediaSheet: closeControllerMediaSheet,
  focusMediaOnMainMap,
  onPopupTrackSelect,
  onTrackDetailsSheetClosed: closeControllerTrackDetailsSheet,
  onTrackDetailsLoaded,
  start3dTrackReplay,
  toggle3dTrackReplayPlayback,
  reset3dTrackReplayPlayback,
  stop3dTrackReplay,
  seek3dTrackReplay,
  set3dTrackReplayShowContextTracks,
  set3dTrackReplayShowTelemetry,
  set3dTrackReplayDuration,
  set3dTrackReplayCameraPreset,
  set3dTrackReplayCameraSmoothness,
  onTrackReplayControlsLayoutChange,
  recenter3dTrackReplayCamera,
  onTrackBrowserSelect,
  onTrackBrowserOpenDetails,
  onTrackBrowserOpenPhotos,
  onLegendCollapsed,
  onHiddenGroupsChanged,
  onStartGeoDrawing,
  onGeoDrawUndo,
  onGeoDrawFinish,
  onGeoDrawCancel,
  onClearGeoShape,
  onFilterApplied,
  onFilterStyleChanged,
  reloadMap,
} = useMainMapController(props, emit, toast, {
  mapContainer,
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

const mediaPreviewDetailsVisible = ref(window.innerWidth >= MEDIA_PREVIEW_DESKTOP_MIN_WIDTH);
const mediaViewerOverviewBounds = ref<[[number, number], [number, number]] | null>(null);
const selectedMapMediaPoint = computed(() => findMediaPointById(mediaNavList.value, mediaSheetMediaId.value));
const trackSelectionSheetTitle = computed(() =>
  trackSelectionPurpose.value === 'photos'
    ? `${selectionPopupTracks.value.length} nearby activities`
    : `${selectionPopupTracks.value.length} tracks — select for details`
);
const selectionPopupTracksWithMedia = computed(() =>
  selectionPopupTracks.value.filter((track) => (track.matchedMediaCount ?? 0) > 0)
);
const selectionPopupTracksWithoutMedia = computed(() =>
  selectionPopupTracks.value.filter((track) => (track.matchedMediaCount ?? 0) === 0)
);
const mediaViewerScopeLabel = computed(() => {
  if (mediaNavScope.value === 'cluster') return 'This cluster';
  if (mediaNavScope.value === 'location') return 'This location';
  if (mediaNavScope.value === 'viewport') return 'Current map view';
  return 'This photo';
});
const mediaViewerFilmstripLabel = computed(() => {
  if (mediaNavScope.value === 'cluster') return 'In this cluster';
  if (mediaNavScope.value === 'location') return 'At this location';
  if (mediaNavScope.value === 'viewport') return 'On this map';
  return 'This photo';
});
type MainMapMediaTarget = { id?: number | null; lat: number; lng: number };
const pendingMainMapMediaTarget = ref<MainMapMediaTarget | null>(null);

function validMainMapMediaTarget(target: MainMapMediaTarget | null | undefined): target is MainMapMediaTarget {
  return Boolean(
    target &&
    Number.isFinite(target.lat) &&
    Number.isFinite(target.lng) &&
    Math.abs(target.lat) <= 90 &&
    Math.abs(target.lng) <= 180
  );
}

function setPendingMainMapMediaTarget(target: MainMapMediaTarget | null | undefined): boolean {
  if (!validMainMapMediaTarget(target)) return false;
  pendingMainMapMediaTarget.value = { id: target.id, lat: target.lat, lng: target.lng };
  return true;
}

function focusPendingMainMapMediaTarget(): void {
  const target = pendingMainMapMediaTarget.value;
  pendingMainMapMediaTarget.value = null;
  if (target) focusMediaOnMainMap(target);
}

function openSelectedMapMediaOnMainMap(): void {
  if (!setPendingMainMapMediaTarget(selectedMapMediaPoint.value)) return;
  mediaSheetVisible.value = false;
}

function onMediaSheetClosed(): void {
  closeControllerMediaSheet();
  mediaViewerOverviewBounds.value = null;
  focusPendingMainMapMediaTarget();
}

function openTrackMediaOnMainMap(target: MainMapMediaTarget): void {
  if (!setPendingMainMapMediaTarget(target)) return;
  trackDetailsMainMapExitPending = true;
  trackDetailsVisible.value = false;
  closeControllerTrackDetailsSheet();
  focusPendingMainMapMediaTarget();
}

function onTrackDetailsSheetClosedAfterTransition(): void {
  closeControllerTrackDetailsSheet();
  focusPendingMainMapMediaTarget();
}

async function openMediaFromStatisticsOnMainMap(target: MainMapMediaTarget): Promise<void> {
  if (!setPendingMainMapMediaTarget(target)) return;
  statistics.value?.close?.();
  await nextTick();
  window.requestAnimationFrame(focusPendingMainMapMediaTarget);
}

watch(mediaSheetVisible, (visible) => {
  if (!visible) return;
  mediaPreviewDetailsVisible.value = window.innerWidth >= MEDIA_PREVIEW_DESKTOP_MIN_WIDTH;
  const bounds = overlayMap.value?.getBounds?.();
  if (!bounds) return;
  const southWest = bounds.getSouthWest();
  const northEast = bounds.getNorthEast();
  mediaViewerOverviewBounds.value = [
    [southWest.lng, southWest.lat],
    [northEast.lng, northEast.lat],
  ];
});

const showMapConfigFallbackNotice = computed(
  () => Boolean((mapConfig.value as MapConfig | null)?.configLoadFailed) && !mapConfigFallbackDismissed.value
);

async function retryMapConfig() {
  if (mapConfigRetrying.value) return;
  mapConfigRetrying.value = true;
  mapConfigFallbackDismissed.value = false;
  clearMapConfigCache();
  try {
    await reloadMap();
  } finally {
    mapConfigRetrying.value = false;
  }
}

let tracksReadyEmitted = false;

function emitTracksReadyOnce() {
  if (tracksReadyEmitted) return;
  tracksReadyEmitted = true;
  emit('tracks-loaded');
}

watch(
  [initialLoadDone, visibleTrackCount, totalTrackCount],
  ([_isInitialLoadDone, visibleCount, totalCount]) => {
    if (visibleCount > 0 || totalCount > 0) {
      emitTracksReadyOnce();
    }
  },
  { immediate: true }
);

const TOOL_ROUTE_NAMES: Record<string, string> = {
  stats: 'stats',
  filter: 'filter',
  planner: 'planner',
  map: 'map-settings',
  animate: 'animate',
  measure: 'segments',
  admin: 'admin',
};

const ROUTE_TOOL_IDS: Record<string, string> = Object.entries(TOOL_ROUTE_NAMES).reduce(
  (acc, [toolId, routeName]) => {
    acc[routeName] = toolId;
    return acc;
  },
  {} as Record<string, string>
);
const TRACK_DETAILS_ROUTE_NAME = 'track-detail';
const TRACK_DETAILS_RETURN_ROUTE_NAMES = new Set([
  'home',
  TRACK_DETAILS_ROUTE_NAME,
  ...Object.values(TOOL_ROUTE_NAMES),
]);

let syncingFromRoute = false;
let trackDetailsRouteExitPending = false;
let trackDetailsMainMapExitPending = false;

function closeTrackDetailsFromPanel() {
  trackDetailsVisible.value = false;
}

function toolIdForRoute(name: RouteRecordNameGeneric | null | undefined): string | null {
  return typeof name === 'string' ? (ROUTE_TOOL_IDS[name] ?? null) : null;
}

function updateRouteForTool(toolId: string | null | undefined) {
  if (syncingFromRoute) return;
  const currentTool = toolIdForRoute(route.name);
  if (!toolId) {
    if (currentTool != null) {
      router.push({ name: 'home' }).catch(() => undefined);
    }
    return;
  }
  const routeName = TOOL_ROUTE_NAMES[toolId];
  if (routeName && route.name !== routeName) {
    router.push({ name: routeName }).catch(() => undefined);
  }
}

function onNavigationToolSelect(toolId: string) {
  onToolSelect(toolId);
  updateRouteForTool(activeToolId.value);
}

function canReturnFromTrackDetails(previousRoute: unknown): previousRoute is string {
  if (typeof previousRoute !== 'string' || previousRoute.length === 0) return false;
  const resolvedRouteName = router.resolve(previousRoute).name;
  return typeof resolvedRouteName === 'string' && TRACK_DETAILS_RETURN_ROUTE_NAMES.has(resolvedRouteName);
}

function leaveTrackDetailsRoute() {
  if (trackDetailsRouteExitPending || route.name !== TRACK_DETAILS_ROUTE_NAME) return;
  trackDetailsRouteExitPending = true;
  if (trackDetailsMainMapExitPending) {
    trackDetailsMainMapExitPending = false;
    router.replace({ name: 'home' }).catch(() => {
      trackDetailsRouteExitPending = false;
    });
    return;
  }
  const previousRoute = (window.history.state as { back?: unknown } | null)?.back;
  if (canReturnFromTrackDetails(previousRoute)) {
    router.back();
    return;
  }
  router.replace({ name: 'home' }).catch(() => {
    trackDetailsRouteExitPending = false;
  });
}

watch(
  () => [route.name, route.params.id],
  async () => {
    trackDetailsRouteExitPending = false;
    syncingFromRoute = true;
    await nextTick();
    if (route.name === TRACK_DETAILS_ROUTE_NAME) {
      const trackId = Number(route.params.id);
      if (Number.isFinite(trackId)) {
        syncTrackDetailRoute(trackId);
      }
    } else {
      syncToolToRoute(toolIdForRoute(route.name));
    }
    await nextTick();
    syncingFromRoute = false;
  },
  { immediate: true }
);

watch(activeToolId, (toolId) => {
  updateRouteForTool(toolId);
});

watch([trackDetailsVisible, trackDetailsId], ([visible, id]) => {
  if (syncingFromRoute) return;
  if (visible && id != null) {
    if (route.name !== TRACK_DETAILS_ROUTE_NAME || Number(route.params.id) !== Number(id)) {
      router.push({ name: TRACK_DETAILS_ROUTE_NAME, params: { id } }).catch(() => undefined);
    }
  } else if (route.name === TRACK_DETAILS_ROUTE_NAME) {
    leaveTrackDetailsRoute();
  }
});
</script>

<style scoped>
* {
  margin: 0;
  box-sizing: border-box;
}

.container {
  --mtl-location-search-fab-size: 3rem;
  --mtl-map-attribution-strip-h: 0.85rem;
  --mtl-map-attribution-bottom-gap: 0px;
  --mtl-location-search-bottom-gap: 0.4rem;

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

.map {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.map :deep(.maplibregl-ctrl-bottom-right) {
  right: 0;
  bottom: calc(var(--nav-sheet-h, 0px) + var(--safe-bottom, 0px));
}

.map :deep(.maplibregl-ctrl-bottom-right .maplibregl-ctrl) {
  margin: 0 var(--mtl-map-attribution-bottom-gap) var(--mtl-map-attribution-bottom-gap) 0;
}

.map :deep(.maplibregl-ctrl-attrib) {
  max-width: min(80vw, 34rem);
  min-height: 0;
  overflow: hidden;
  padding: 0 0.32rem;
  border-radius: 2px;
  background: rgba(15, 23, 42, 0.48);
  box-shadow: none;
  color: rgba(248, 250, 252, 0.68);
  font-size: 0.58rem;
  font-weight: 400;
  line-height: var(--mtl-map-attribution-strip-h);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map :deep(.maplibregl-ctrl-attrib a) {
  color: inherit;
  text-decoration: none;
}

.map :deep(.maplibregl-ctrl-attrib a:hover) {
  color: rgba(248, 250, 252, 0.88);
  text-decoration: underline;
}

/* ─── Custom map control buttons ─── */
.map :deep(.mtl-globe-btn),
.map :deep(.mtl-terrain-btn) {
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
.map :deep(.mtl-globe-btn:hover),
.map :deep(.mtl-terrain-btn:hover) {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.map :deep(.mtl-globe-btn.mtl-globe-active),
.map :deep(.mtl-terrain-btn.mtl-terrain-active) {
  color: var(--info);
}
.map :deep(.mtl-terrain-btn.mtl-terrain-active) {
  background: var(--accent) !important;
  color: var(--accent-contrast) !important;
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--accent-contrast) 18%, transparent),
    0 0 0 2px var(--accent-subtle);
}
.map :deep(.mtl-globe-btn.mtl-globe-active:hover),
.map :deep(.mtl-terrain-btn.mtl-terrain-active:hover) {
  color: var(--viz-blue);
}
.map :deep(.mtl-terrain-btn.mtl-terrain-active:hover) {
  background: var(--accent-hover) !important;
  color: var(--accent-contrast) !important;
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
  bottom: calc(
    var(--nav-sheet-h, 92px) + var(--safe-bottom, 0px) + var(--mtl-map-attribution-strip-h) +
      var(--mtl-map-attribution-bottom-gap) + var(--mtl-location-search-bottom-gap)
  );
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--mtl-location-search-fab-size);
  height: var(--mtl-location-search-fab-size);
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
  .container {
    --mtl-location-search-bottom-gap: 0.55rem;
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
  z-index: var(--z-freshness-banner);
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
  color: var(--accent-contrast);
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

.track-selection-group {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.track-selection-group + .track-selection-group {
  margin-top: 1.1rem;
}

.track-selection-group__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0 0.2rem;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
  letter-spacing: 0.01em;
}

.track-selection-group__title > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
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
  width: min(300px, calc(100% - 24px));
  min-width: min(180px, calc(100% - 24px));
  max-height: min(28rem, calc(100% - 24px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.swiss-mobility-popup-close {
  position: absolute;
  top: 0.25rem;
  right: 0.35rem;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  z-index: 1;
  transition:
    background 0.15s,
    color 0.15s;
}
.swiss-mobility-popup-close:hover,
.swiss-mobility-popup-close:focus-visible {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.swiss-mobility-popup-header {
  font-size: var(--text-xs-size);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-faint);
  padding: 0.4rem 2.6rem 0.3rem 0.85rem;
  border-bottom: 1px solid var(--border-subtle);
}
.swiss-mobility-route-list {
  list-style: none;
  margin: 0;
  padding: 0;
  min-height: 0;
  overflow-y: auto;
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

<!-- Unscoped map overlay styles -->
<style>
.mtl-focused-media-marker {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: 2.125rem;
  height: 2.125rem;
  border: 3px solid #ffffff;
  border-radius: 50%;
  outline: 4px solid color-mix(in srgb, var(--accent) 62%, transparent);
  outline-offset: 3px;
  background: #ff0033;
  color: #ffffff;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.45);
  font-size: 0.9rem;
  pointer-events: none;
}

.mtl-focused-media-marker::after {
  content: '';
  position: absolute;
  inset: -0.45rem;
  border: 3px solid var(--accent);
  border-radius: 50%;
  animation: mtl-focused-media-marker-pulse 760ms ease-out 3;
  pointer-events: none;
}

@keyframes mtl-focused-media-marker-pulse {
  from {
    opacity: 0.85;
    transform: scale(0.72);
  }
  to {
    opacity: 0;
    transform: scale(1.85);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mtl-focused-media-marker::after {
    animation: none;
    opacity: 0.7;
  }
}

.mtl-location-search-marker {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.1rem;
  height: 2.1rem;
  border: 2px solid var(--accent-contrast);
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-contrast);
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
  background: var(--accent-contrast);
  color: var(--text-secondary);
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
  border-right: 2px solid var(--accent-contrast);
  border-bottom: 2px solid var(--accent-contrast);
  transform: translateX(-50%) rotate(45deg);
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
