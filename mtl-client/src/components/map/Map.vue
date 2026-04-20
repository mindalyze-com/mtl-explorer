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

    <!-- ─── Tool components (hidden triggers, only sheets/logic) ─── -->

    <MapSettingsPanel ref="mapSettingsTool"
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



    <AnimateMap ref="animateTool" :map="overlayMap"
                @animationStart="onAnimationStartEvent"
                @animationFinished="onAnimationFinishedEvent"
                @animationStop="onAnimationStopEvent"
                @animate="onAnimateEvent"
                @tool-opened="onToolOpened('animateTool')"
                @tool-closed="onToolClosed"/>

    <MeasureBetweenPoints ref="measureTool" :map="overlayMap" @active-changed="onMeasureActiveChanged" @tool-opened="onToolOpened('measureTool')" @tool-closed="onToolClosed"/>

    <PlannerTool ref="plannerTool" :map="overlayMap" @active-changed="onPlannerActiveChanged" @tool-opened="onToolOpened('plannerTool')" @tool-closed="onToolClosed"/>

    <Statistics ref="statistics"
                :tracks="trackBrowserTracks"
                :tracks-count="visibleTrackCount"
                :unfiltered-total="totalTrackCount"
                :selected-track-id="selectedFeature?.properties?.id ?? null"
                @select-track="onTrackBrowserSelect"
                @open-details="onTrackBrowserOpenDetails"
                @tool-opened="onToolOpened('statistics')"
                @tool-closed="onToolClosed"/>

    <GpsLocate ref="gpsLocate"
               @locationUpdate="onLocationUpdate"
               @deviceEnabledDisabled="onGPSDeviceEnabledDisabled"/>

    <Filter ref="filterTool"
            :palette="colorPalette"
            :total-track-count="totalTrackCount"
            :visible-track-count="visibleTrackCount"
            @filterAppliedEvent="onFilterApplied"
            @tool-opened="onToolOpened('filterTool')"
            @tool-closed="onToolClosed"
            @start-geo-drawing="onStartGeoDrawing"
            @clear-geo-shape="onClearGeoShape"/>

    <!-- ─── Top-right anchor: unified chip + legend card ─── -->
    <div class="mtl-top-right">
      <MapLegend
        :entries="legendEntries"
        :collapsed="legendCollapsed"
        :visibleTrackCount="visibleTrackCount"
        :totalTrackCount="totalTrackCount"
        :filterActive="filterActive"
        :hiddenGroups="hiddenGroups"
        @update:collapsed="onLegendCollapsed"
        @update:hiddenGroups="onHiddenGroupsChanged"
        @chip-click="onToolSelect('filter')"
      />
    </div>

    <!-- ─── Top progress bar ─── -->
    <transition name="bar-fade">
      <div class="mtl-progress-bar" v-if="showLoader || loadingTracks10m"></div>
    </transition>

    <!-- ─── Admin (managed via NavigationSheet, same as all tools) ─── -->
    <AdminDialog ref="adminTool" @tool-opened="onToolOpened('adminTool')" @tool-closed="onToolClosed" @reload-tracks="onAdminReloadTracks" />

    <!-- ─── Offline banner ─── -->
    <transition name="fade">
      <div v-if="isOffline" class="mtl-offline">
        <i class="bi bi-wifi-off"></i> Offline — displaying cached tracks
      </div>
    </transition>

    <!-- ─── Map download banner ─── -->
    <transition name="fade">
      <div v-if="mapServerStatus && !mapServerStatus.ready" class="mtl-map-downloading">
        <div class="mtl-map-downloading-header">
          <i :class="mapServerStatus.phase === 'downloading' ? 'bi bi-cloud-download' : 'bi bi-gear'"></i>
          <span>{{ mapServerStatus.phase === 'downloading' ? 'Downloading map tiles…' : mapServerStatus.phase === 'extracting' ? 'Processing map tiles…' : 'Preparing map…' }}</span>
        </div>
        <div v-if="mapServerStatus.phase === 'downloading' && mapServerStatus.download_total > 0" class="mtl-map-downloading-progress">
          <div class="mtl-map-downloading-bar-track">
            <div class="mtl-map-downloading-bar-fill" :style="{ width: mapServerStatus.download_pct + '%' }"></div>
          </div>
          <span class="mtl-map-downloading-pct">{{ mapServerStatus.download_pct }}%</span>
        </div>
        <div v-if="mapServerStatus.message" class="mtl-map-downloading-msg">{{ mapServerStatus.message }}</div>
      </div>
    </transition>

    <!-- ─── Track details bottom sheet ─── -->
    <BottomSheet v-model="trackDetailsVisible" :detents="trackDetailsDetents" :initial-detent="trackDetailsInitialDetent" :z-index="5100" @closed="onTrackDetailsSheetClosed">
      <template #title>
        <div class="td-sheet-header">
          <span class="td-title-label"><i class="bi bi-info-circle"></i><span class="td-title-text">Track Details</span></span>
          <span v-if="trackDetailsInfo.id" class="td-sheet-id">#{{ trackDetailsInfo.id }}</span>
        </div>
      </template>
      <TrackDetails v-if="trackDetailsId != null" :gps-track-id="trackDetailsId" @track-loaded="onTrackDetailsLoaded" />
    </BottomSheet>

    <!-- ─── Media photo bottom sheet ─── -->
    <BottomSheet v-model="mediaSheetVisible" :detents="[{ id: 'small', height: '40vh' }, { id: 'medium', height: '70vh' }, { id: 'large', height: '92vh' }]" initial-detent="large" :z-index="5050" title="Photo" icon="bi bi-image" header-mode="compact" :no-backdrop="false" @closed="closeMediaSheet">
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
    <BottomSheet v-model="trackSelectionSheetVisible" :detents="[{ id: 'compact', height: '35vh' }, { id: 'medium', height: '50vh' }, { id: 'expanded', height: '65vh' }]" initial-detent="compact" :z-index="4900" :title="selectionPopupTracks.length + ' tracks — select for details'" icon="bi bi-card-list" header-mode="compact" :no-backdrop="false" @closed="closeSelectionPopup">
      <div class="track-selection-sheet">
        <div class="track-selection-sheet__scroll">
          <ul class="track-selection-list">
            <li v-for="track in selectionPopupTracks" :key="track.id"
                class="mtl-track-pick"
                @click="onPopupTrackSelect(track.id)">
              <TrackShapePreview :trackId="track.id" :width="48" :height="32" :padding="3" class="mtl-track-pick__shape" />
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
    <div v-if="swissMobilityPopup.visible" class="swiss-mobility-popup"
         :style="{ left: swissMobilityPopup.pos.x + 'px', top: swissMobilityPopup.pos.y + 'px' }">
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
          <button class="geo-draw-toolbar__btn geo-draw-toolbar__btn--undo"
                  :disabled="!geoDrawCanUndo"
                  @click="onGeoDrawUndo">
            <i class="bi bi-arrow-counterclockwise"></i> Undo
          </button>
          <button v-if="geoDrawIsPolygon"
                  class="geo-draw-toolbar__btn geo-draw-toolbar__btn--finish"
                  :disabled="!geoDrawCanFinish"
                  @click="onGeoDrawFinish">
            <i class="bi bi-check-lg"></i> Finish
          </button>
          <button class="geo-draw-toolbar__btn geo-draw-toolbar__btn--cancel"
                  @click="onGeoDrawCancel">
            <i class="bi bi-x-lg"></i> Cancel
          </button>
        </div>
      </div>
    </transition>

  </div>
</template>

<script>
import { markRaw, computed } from 'vue';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import maplibregl from 'maplibre-gl';
import { formatDate, formatDateAndTime, formatDateAndTimeWithSeconds } from '@/utils/Utils';
import 'maplibre-gl/dist/maplibre-gl.css';
import { Protocol } from 'pmtiles';
import { createCachingPMTiles } from '@/utils/cachingPmtilesSource';

import AdminDialog from '@/components/admin/AdminDialog.vue';
import GpsLocate from "@/components/gps/GpsLocate.vue";
import MeasureBetweenPoints from "@/components/measure/MeasureBetweenPoints.vue";
import PlannerTool from "@/planner/components/PlannerTool.vue";
import axios from 'axios';
import { apiClient } from '@/utils/apiClient';
import { fetchTrackIdsWithinDistanceOfPoint, fetchTrackDetails, checkServerAuth } from '@/utils/ServiceHelper';
import { clearToken, isAuthError } from '@/utils/auth';
import router from '@/router';
import { MediaOverlay } from "@/layers/MediaOverlay";
import { HeatmapOverlay } from "@/layers/HeatmapOverlay";
import { GeoDrawingOverlay } from "@/layers/GeoDrawingOverlay";
import AnimateMap from "@/components/animate/AnimateMap.vue";
import Filter from "@/components/filter/Filter.vue";
import Statistics from "@/components/statistics/Statistics.vue";

import TrackDetails from "@/components/trackdetails/TrackDetails.vue";
import NavigationSheet from "@/components/ui/NavigationSheet.vue";
import MapSettingsPanel from "@/components/map/MapSettingsPanel.vue";
import MapLegend from "@/components/map/MapLegend.vue";
import BottomSheet from "@/components/ui/BottomSheet.vue";
import TrackShapePreview from "@/components/ui/TrackShapePreview.vue";
import ActivityTypeBadge from "@/components/ui/ActivityTypeBadge.vue";
import MediaPreview from "@/components/map/MediaPreview.vue";
import { FilterService } from "@/components/filter/FilterService";
import { ColorPalette } from "@/components/filter/ColorPalette";
import { trackStore, OVERVIEW_PRECISION } from "@/utils/trackStore";
import { fetchMapConfig, clearMapConfigCache } from "@/utils/mapConfigService";
import { buildLocalVectorStyle, buildRemoteRasterStyle, buildFallbackRasterStyle, SWISSTOPO_STYLE_URL, SWISSTOPO_COLOR_STYLE_URL, MAP_OVERLAYS } from "@/utils/mapStyle";
import { TRACK_COLOR, TRACK_SELECTED_COLOR } from '@/utils/trackColors';
import { GlobeControl, computeGlobeMinZoom } from "@/components/map/GlobeControl";
import { ensureLowZoomCached, loadLowZoomFromCache } from "@/utils/lowZoomCacheService";
import { describeError, startStartupTimer, startupError, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import thumbOsmTopo from '@/assets/map-layer/osm_topo.jpg';
import thumbSwissColor from '@/assets/map-layer/swiss_color_contrast.jpg';
import thumbSwissLight from '@/assets/map-layer/swiss_topo_light.jpg';
import thumbOsmLight from '@/assets/map-layer/osm_light.jpg';
import thumbOsmDark from '@/assets/map-layer/osm_dark.jpg';

/** Detent positions for the track details bottom sheet. */
const TRACK_DETAILS_DETENTS = [
  { id: 'compact', height: '35vh' },
  { id: 'default', height: '75vh' },
  { id: 'expanded', height: '92vh' },
];
/** Compact detent used for map-originated opens so the highlighted track stays visible. */
const TRACK_DETAILS_MAP_DETENT = 'compact';
/** Mid detent used for normal detail opens when map context is less important. */
const TRACK_DETAILS_DEFAULT_DETENT = 'default';
/** Largest detent used for explicit "show me details" actions. */
const TRACK_DETAILS_EXPANDED_DETENT = 'expanded';

/** How much to expand the detail-fetch bounding box beyond the viewport. */
const DETAIL_BOUNDS_PADDING = 2;
const DETAIL_DEBOUNCE_MS = 500;
const DETAIL_MAX_CONCURRENT = 5;
/** 1m full-detail fetches are heavy — limit to 1 at a time to keep the UI responsive. */
const DETAIL_MAX_CONCURRENT_1M = 1;

/** Minimum zoom level to show individual GPS track points with direction arrows. */
const TRACK_POINTS_MIN_ZOOM = 16;

/** Zoom threshold: entering globe — auto-activates when zooming OUT past this level. */
const GLOBE_ENTER_ZOOM = 3;

/**
 * Zoom threshold: leaving globe — auto-deactivates only when zooming IN past this level.
 * Higher than GLOBE_ENTER_ZOOM so there's a hysteresis band (no flicker at the boundary).
 */
const GLOBE_EXIT_ZOOM = 3.8;

/** Minimum zoom allowed in mercator mode — zoom 1 shows the full world. */
const MERCATOR_MIN_ZOOM = 1.0;
const MAP_LOAD_WATCHDOG_MS = 7000;

/** Map zoom level → desired track precision in meters. */
function precisionForZoom(zoom) {
  if (zoom >= 17) return 1;
  if (zoom >= 6) return 10;
  return OVERVIEW_PRECISION;
}

// Non-reactive counter for concurrent detail fetches (not used in template/computed)
let _detailFetchInFlight = 0;

/** Timestamp of the last demo-area-boundary toast. Used for 3-minute cooldown. */
let _lastDemoAreaToastTime = 0;
const DEMO_AREA_TOAST_COOLDOWN_MS = 3 * 60 * 1000; // 3 minutes

/** Haversine distance in meters between two [lat, lng] points. */
function haversineDistance(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Forward bearing (in degrees, 0 = north, clockwise) from [lng1, lat1] to [lng2, lat2].
 */
function bearing(lng1, lat1, lng2, lat2) {
  const toRad = Math.PI / 180;
  const dLng = (lng2 - lng1) * toRad;
  const lat1R = lat1 * toRad;
  const lat2R = lat2 * toRad;
  const y = Math.sin(dLng) * Math.cos(lat2R);
  const x = Math.cos(lat1R) * Math.sin(lat2R) - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLng);
  return ((Math.atan2(y, x) * 180 / Math.PI) + 360) % 360;
}

/**
 * Create a direction-arrow image (canvas-based) for use as a MapLibre icon.
 * The arrow points UP (north) — icon-rotate handles orientation.
 */
function createArrowImage(size = 24, color = '#2563eb', bgColor = '#ffffff') {
  const canvas = document.createElement('canvas');
  const ratio = window.devicePixelRatio || 1;
  const px = size * ratio;
  canvas.width = px;
  canvas.height = px;
  const ctx = canvas.getContext('2d');
  ctx.scale(ratio, ratio);

  const cx = size / 2;
  const cy = size / 2;
  const r = size / 2 - 1;

  // White filled circle background
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fillStyle = bgColor;
  ctx.fill();
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // Arrow pointing up (chevron / arrowhead)
  const arrowH = size * 0.38;
  const arrowW = size * 0.36;
  const top = cy - arrowH * 0.55;

  ctx.beginPath();
  ctx.moveTo(cx, top);                              // tip
  ctx.lineTo(cx + arrowW / 2, top + arrowH);        // bottom-right
  ctx.lineTo(cx, top + arrowH * 0.6);               // inner notch
  ctx.lineTo(cx - arrowW / 2, top + arrowH);        // bottom-left
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();

  const imgData = ctx.getImageData(0, 0, px, px);
  return { width: px, height: px, data: imgData.data };
}

// Register PMTiles protocol once at module level
let pmtilesProtocol = null;
let pmtilesProtocolAdded = false;
function ensurePMTilesProtocol() {
  if (pmtilesProtocolAdded) return;
  pmtilesProtocol = new Protocol();
  maplibregl.addProtocol('pmtiles', pmtilesProtocol.tile);
  pmtilesProtocolAdded = true;
}

export default {
  components: {
    Statistics,
    Filter,
    AnimateMap,
    GpsLocate,
    MeasureBetweenPoints,
    PlannerTool,
    TrackDetails,
    AdminDialog,
    NavigationSheet,
    MapSettingsPanel,
    MapLegend,
    BottomSheet,
    TrackShapePreview,
    ActivityTypeBadge,
    MediaPreview,
  },
  emits: ['tracks-loaded', 'load-failed', 'syncing'],
  setup() {
    const { isIndexing, isJobPending } = useIndexerStatus();
    const isAnyPending = computed(() => isIndexing.value || isJobPending.value);
    return { isIndexing: isAnyPending };
  },
  data() {
    return {
      map: undefined,         // base map (tiles + mobility overlays)
      overlayMap: undefined,  // overlay map (tracks, highlights, media)
      mapConfig: null,
      mapServerStatus: null,
      mapStatusPollTimer: null,
      zoom: 0,
      geojson: undefined,
      globeMode: false,       // true when globe projection is active
      globeUserDisabled: false, // true when user explicitly turned off globe at low zoom
      mapCenter: [8.505778, 47.5605], // [lng, lat] for MapLibre — Glattfelden

      gpsMarker: null,
      gpsLocation: null,           // [lat, lng] — last known GPS position
      gpsDeviceEnabledDisabled: false,
      gpsFollowing: false,          // true = map auto-centres on GPS updates
      showLoader: false,
      loadingTracks10m: false,
      mapThemes: [
        { name: 'OSM Topo', code: 'light-topo', thumbnail: thumbOsmTopo, featured: true },
        { name: 'Swiss Color', code: 'swisstopo-color', thumbnail: thumbSwissColor, featured: true },
        { name: 'Swiss Light', code: 'swisstopo', thumbnail: thumbSwissLight },
        { name: 'OSM Light', code: 'light', thumbnail: thumbOsmLight },
        { name: 'OSM Dark', code: 'dark', thumbnail: thumbOsmDark },
      ],
      mapThemeSelected: localStorage.getItem('mapTheme') ?? 'light-topo',
      visibleTrackCount: 0,
      totalTrackCount: 0,
      basemapEnabled: true,
      tracksEnabled: true,
      layerOpacities: {
        basemap: 100,
        tracks: 100,
        media: 100,
        trackpoints: 100,
        heatmap: 100,
        wanderland: 100,
        veloland: 100,
        mountainbikeland: 100,
        wanderwege: 100,
        'wmt-hiking': 100,
        'wmt-cycling': 100,
        'wmt-mtb': 100,
      },
      wasAutoDimmed: false,
      filterActive: false,
      colorPalette: markRaw(new ColorPalette()),
      legendEntries: [],
      legendCollapsed: localStorage.getItem('mtl-legendCollapsed') === 'true',
      hiddenGroups: new Set(),
      gpsTracksById: markRaw(new Map()),
      gpsTrackIdToFeature: markRaw(new Map()),
      selectedTrackId: null,
      selectedFeature: null,
      trackSelectionSheetVisible: false,
      selectionPopupTrackIds: [],
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
      proximityAbortController: null,
      trackDetailsVisible: false,
      trackDetailsDetents: TRACK_DETAILS_DETENTS,
      trackDetailsInitialDetent: TRACK_DETAILS_DEFAULT_DETENT,
      trackDetailsId: null,
      trackDetailsInfo: { id: null, name: '', description: '' },
      measureToolActive: false,
      plannerToolActive: false,
      activeToolId: null,
      toolDefs: [
        // Row 1: primary (shown at peek)
        { id: 'stats', icon: 'bi bi-graph-up', label: 'Stats' },
        { id: 'filter', icon: 'bi bi-funnel', alertIcon: 'bi bi-funnel-fill', label: 'Filter' },
        // Row 2: secondary (shown when expanded)
        { id: 'map', icon: 'bi bi-map', label: 'Map' },
        { id: 'animate', icon: 'bi bi-play-circle', label: 'Animate' },
        { id: 'measure', icon: 'bi bi-stopwatch', label: 'Sectors' },
        { id: 'gps', icon: 'bi bi-geo-fill', alertIcon: 'bi bi-geo-alt-fill', driftedIcon: 'bi bi-geo-alt-fill', label: 'GPS' },
        { id: 'admin', icon: 'bi bi-gear', label: 'Admin' },
      ],
      mediaOverlay: null,
      mediaVisible: false,
      mediaBusy: false,
      mediaSheetVisible: false,
      mediaSheetMediaId: null,
      mediaLoadedPoints: [],
      mediaNavList: [],
      heatmapOverlay: null,
      heatmapVisible: false,
      isOffline: false,
      cachedTracksLoaded: false,
      initialLoadDone: false,
      retryTimeoutId: null,
      retryCount: 0,
      bulk10mController: null,
      trackPrecisions: markRaw(new Map()),
      detailAbortController: null,
      detailDebounceTimer: null,
      activeOverlays: [],
      _syncingView: false,  // guard to prevent recursive view-sync loops
      trackPointsVisible: true,             // toggle for direction-arrow point markers
      trackPointsDetailsCache: markRaw(new Map()), // trackId → GpsTrackDataPoint[]
      trackPointsPopup: null,             // active MapLibre popup for a clicked point
      // Geo drawing
      geoDrawingOverlay: null,
      geoDrawingParamDef: null,
      geoDrawPointCount: 0,
      // Map-ready gate: resolved when initMap() finishes (maps created + loaded).
      // fetchTracksAndFallback runs in parallel and awaits this before addTracksToMap.
      _mapReadyResolve: null,
      _mapReadyPromise: null,
    };
  },
  async mounted() {
    startupLog('map', 'Map component mounted');
    const saved = localStorage.getItem('mtl-activeOverlays');
    if (saved) {
      try { this.activeOverlays = JSON.parse(saved); } catch { this.activeOverlays = []; }
    }
    // Load layer opacities
    const savedOpacities = localStorage.getItem('mtl-layerOpacities');
    if (savedOpacities) {
      try { Object.assign(this.layerOpacities, JSON.parse(savedOpacities)); } catch { /* keep defaults */ }
    }
    const savedBasemap = localStorage.getItem('mtl-basemapEnabled');
    if (savedBasemap !== null) this.basemapEnabled = savedBasemap !== 'false';
    const savedTracks = localStorage.getItem('mtl-tracksEnabled');
    if (savedTracks !== null) this.tracksEnabled = savedTracks !== 'false';
    const savedAutoDim = localStorage.getItem('mtl-wasAutoDimmed');
    if (savedAutoDim !== null) this.wasAutoDimmed = savedAutoDim === 'true';
    // Migrate from old system (mapEnhanced + dimOpacity + grayscaleAmount) — always start fresh at 100%
    const oldMapEnhanced = localStorage.getItem('mtl-mapEnhanced');
    if (oldMapEnhanced !== null) {
      this.layerOpacities.basemap = 100; // Fresh start — do not carry over old dim value
      localStorage.removeItem('mtl-mapEnhanced');
      localStorage.removeItem('mtl-dimOpacity');
      localStorage.removeItem('mtl-grayscaleAmount');
      localStorage.setItem('mtl-layerOpacities', JSON.stringify(this.layerOpacities));
    }
    const savedTrackPoints = localStorage.getItem('mtl-trackPointsVisible');
    if (savedTrackPoints !== null) this.trackPointsVisible = savedTrackPoints !== 'false';
    const savedHeatmap = localStorage.getItem('mtl-heatmapVisible');
    if (savedHeatmap !== null) this.heatmapVisible = savedHeatmap === 'true';
    try {
      await this.reloadMap(true);
      startupLog('map', 'Initial map reload completed');
    } catch (error) {
      startupError('map', 'Initial map reload failed', describeError(error));
      throw error;
    }
    this._onOnline = () => this.onBrowserOnline();
    window.addEventListener('online', this._onOnline);
  },
  beforeUnmount() {
    this.stopMapStatusPolling();
    if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
    if (this._onOnline) window.removeEventListener('online', this._onOnline);
    if (this.detailDebounceTimer) clearTimeout(this.detailDebounceTimer);
    if (this.detailAbortController) this.detailAbortController.abort();
    if (this.bulk10mController) this.bulk10mController.abort();
    if (this.trackPointsPopup) { this.trackPointsPopup.remove(); this.trackPointsPopup = null; }
    if (this._resizeObserver) this._resizeObserver.disconnect();
    if (this.heatmapOverlay) { this.heatmapOverlay.destroy(); this.heatmapOverlay = null; }
    if (this.geoDrawingOverlay) { this.geoDrawingOverlay.destroy(); this.geoDrawingOverlay = null; }
    if (this.overlayMap) {
      this.overlayMap.remove();
      this.overlayMap = undefined;
    }
    if (this.map) {
      this.map.remove();
      this.map = undefined;
    }
  },
  computed: {
    selectionPopupTracks() {
      return this.selectionPopupTrackIds.map(id => this.getTrackPopupMeta(id));
    },
    baseMapStyle() {
      // Basemap slider: combines desaturation, brightening, and opacity fade.
      //   slider 100 → normal map
      //   slider   0 → fully invisible
      if (!this.basemapEnabled) return { opacity: 0.08 };
      const pct = this.layerOpacities.basemap;         // 0‒100
      if (pct >= 100) return {};
      const dim = (100 - pct) / 100;                   // 0‒1  (0 = normal, 1 = max dim)
      const brightness = 1 + 0.4 * dim;
      const opacity = pct / 100;                        // 1 at 100%, 0 at 0%
      return {
        filter: `grayscale(${dim}) brightness(${brightness})`,
        opacity,
      };
    },
    layerStatesForPanel() {
      return {
        basemap:          { enabled: this.basemapEnabled, opacity: this.layerOpacities.basemap },
        tracks:           { enabled: this.tracksEnabled, opacity: this.layerOpacities.tracks },
        media:            { enabled: this.mediaVisible, opacity: this.layerOpacities.media },
        trackpoints:      { enabled: this.trackPointsVisible, opacity: this.layerOpacities.trackpoints },
        heatmap:          { enabled: this.heatmapVisible, opacity: this.layerOpacities.heatmap },
        wanderland:       { enabled: this.activeOverlays.includes('wanderland'), opacity: this.layerOpacities.wanderland },
        veloland:         { enabled: this.activeOverlays.includes('veloland'), opacity: this.layerOpacities.veloland },
        mountainbikeland: { enabled: this.activeOverlays.includes('mountainbikeland'), opacity: this.layerOpacities.mountainbikeland },
        wanderwege:       { enabled: this.activeOverlays.includes('wanderwege'), opacity: this.layerOpacities.wanderwege },
        'wmt-hiking':     { enabled: this.activeOverlays.includes('wmt-hiking'), opacity: this.layerOpacities['wmt-hiking'] },
        'wmt-cycling':    { enabled: this.activeOverlays.includes('wmt-cycling'), opacity: this.layerOpacities['wmt-cycling'] },
        'wmt-mtb':        { enabled: this.activeOverlays.includes('wmt-mtb'), opacity: this.layerOpacities['wmt-mtb'] },
      };
    },
    isMediaVisible() {
      return this.mediaVisible;
    },
    mediaSheetInitialDetent() {
      return 'large'; // always open at largest detent
    },
    mediaCurrentIndex() {
      if (!this.mediaSheetMediaId || !this.mediaNavList.length) return -1;
      return this.mediaNavList.findIndex(p => p.id === this.mediaSheetMediaId);
    },
    mediaPrevId() {
      const i = this.mediaCurrentIndex;
      if (i <= 0) return null;
      return this.mediaNavList[i - 1].id;
    },
    mediaNextId() {
      const i = this.mediaCurrentIndex;
      if (i < 0 || i >= this.mediaNavList.length - 1) return null;
      return this.mediaNavList[i + 1].id;
    },
    trackBrowserTracks() {
      return markRaw(Array.from(this.gpsTracksById.values()));
    },
    alertToolIds() {
      const ids = [];
      if (this.filterActive) ids.push('filter');
      // GPS following: show as alert (blue pulse). Drifted state is handled via driftedToolIds.
      if (this.gpsDeviceEnabledDisabled && this.gpsFollowing) ids.push('gps');
      if (this.isIndexing) ids.push('admin');
      return ids;
    },
    driftedToolIds() {
      const ids = [];
      // GPS on but map not following user position
      if (this.gpsDeviceEnabledDisabled && !this.gpsFollowing) ids.push('gps');
      return ids;
    },
    geoDrawToolbarIcon() {
      const t = this.geoDrawingParamDef?.type;
      if (t === 'GEO_CIRCLE') return 'bi bi-circle';
      if (t === 'GEO_RECTANGLE') return 'bi bi-bounding-box';
      return 'bi bi-pentagon';
    },
    geoDrawToolbarLabel() {
      const t = this.geoDrawingParamDef?.type;
      if (t === 'GEO_CIRCLE') return 'Draw Circle';
      if (t === 'GEO_RECTANGLE') return 'Draw Rectangle';
      return 'Draw Polygon';
    },
    geoDrawToolbarHint() {
      if (!this.geoDrawingOverlay) return '';
      const t = this.geoDrawingParamDef?.type;
      const pts = this.geoDrawPointCount;
      if (t === 'GEO_CIRCLE') return pts === 0 ? 'Click to place center' : 'Click to set radius';
      if (t === 'GEO_RECTANGLE') return pts === 0 ? 'Click to place first corner' : 'Click to place opposite corner';
      if (pts === 0) return 'Click to place first point';
      if (pts < 3) return `${pts} point${pts > 1 ? 's' : ''} — need at least 3`;
      return `${pts} points — double-click or press Finish`;
    },
    geoDrawIsPolygon() {
      return this.geoDrawingParamDef?.type === 'GEO_POLYGON';
    },
    geoDrawCanUndo() {
      return this.geoDrawingOverlay?.canUndo() ?? false;
    },
    geoDrawCanFinish() {
      return this.geoDrawingOverlay?.canFinish() ?? false;
    },
  },
  methods: {

    /**
     * Compute raster paint properties for a route overlay at a given slider value.
     * Mirrors the basemap's smart-fade approach:
     *   slider 100 → full color, full opacity
     *   slider  50 → desaturated, slightly washed-out, still fully opaque
     *   slider   0 → fully grey, brightened, nearly invisible
     */
    _overlayPaintForSlider(slider, hueRotate) {
      const dim = Math.max(0, Math.min(1, (100 - slider) / 100));
      return {
        'raster-opacity': 1 - dim * 0.85,        // 1.0 → 0.15
        'raster-saturation': -dim,                // 0   → -1 (grayscale)
        'raster-brightness-max': 1 - dim * 0.25,  // 1.0 → 0.75
        ...(hueRotate !== undefined ? { 'raster-hue-rotate': hueRotate } : {}),
      };
    },

    /**
     * Return the id of the first existing overlay layer that route overlays
     * should render below, to keep them at the bottom of the overlay stack
     * (above transparent background, below heatmap/tracks/media).
     */
    _overlayBeforeId() {
      if (!this.overlayMap) return undefined;
      for (const id of ['heatmap-layer', 'tracks-layer', 'tracks-overview-dots', 'media-clusters', 'media-unclustered']) {
        if (this.overlayMap.getLayer(id)) return id;
      }
      return undefined;
    },

    /** Add all currently-active overlay layers to the overlay map. */
    applyActiveOverlays() {
      if (!this.overlayMap) return;
      const beforeId = this._overlayBeforeId();
      for (const overlay of MAP_OVERLAYS) {
        if (!this.activeOverlays.includes(overlay.id)) continue;
        if (!this.overlayMap.getSource(overlay.id)) {
          this.overlayMap.addSource(overlay.id, {
            type: 'raster',
            tiles: [overlay.url],
            tileSize: 256,
            attribution: overlay.attribution,
          });
        }
        if (!this.overlayMap.getLayer(`${overlay.id}-overlay`)) {
          this.overlayMap.addLayer({
            id: `${overlay.id}-overlay`,
            type: 'raster',
            source: overlay.id,
            minzoom: 0,
            maxzoom: 22,
            paint: this._overlayPaintForSlider(this.layerOpacities[overlay.id] ?? 100, overlay.hueRotate),
          }, beforeId);
        }
      }
    },

    /** Remove all overlay layers and sources from the overlay map. */
    removeAllOverlays() {
      if (!this.overlayMap) return;
      for (const overlay of MAP_OVERLAYS) {
        if (this.overlayMap.getLayer(`${overlay.id}-overlay`)) this.overlayMap.removeLayer(`${overlay.id}-overlay`);
        if (this.overlayMap.getSource(overlay.id)) this.overlayMap.removeSource(overlay.id);
      }
    },

    /** Reset all map settings to their defaults. */
    onResetMapSettings() {
      // Theme
      this.onMapThemeChangeEvent('light-topo');
      // Basemap
      if (!this.basemapEnabled) {
        this.basemapEnabled = true;
      }
      // Tracks
      this.tracksEnabled = true;
      this.applyTracksVisibility();
      // Media
      if (this.mediaVisible) this.onToggleMediaLayer();
      // Heatmap
      if (this.heatmapVisible) this.onToggleHeatmapLayer();
      // Track points
      if (!this.trackPointsVisible) {
        this.trackPointsVisible = true;
        this.updateTrackPointsSource();
      }
      // Swiss Mobility overlays
      for (const id of [...this.activeOverlays]) {
        this.onToggleOverlay(id);
      }
      // Opacities
      this.layerOpacities = { basemap: 100, tracks: 100, media: 100, trackpoints: 100, heatmap: 100, wanderland: 100, veloland: 100, mountainbikeland: 100, wanderwege: 100, 'wmt-hiking': 100, 'wmt-cycling': 100, 'wmt-mtb': 100 };
      this.applyAllLayerOpacities();
      this.persistLayerStates();
    },

    /** Persist all layer opacities and toggle states to localStorage. */
    persistLayerStates() {
      localStorage.setItem('mtl-layerOpacities', JSON.stringify(this.layerOpacities));
      localStorage.setItem('mtl-basemapEnabled', String(this.basemapEnabled));
      localStorage.setItem('mtl-tracksEnabled', String(this.tracksEnabled));
      localStorage.setItem('mtl-trackPointsVisible', String(this.trackPointsVisible));
      localStorage.setItem('mtl-heatmapVisible', String(this.heatmapVisible));
      localStorage.setItem('mtl-activeOverlays', JSON.stringify(this.activeOverlays));
      localStorage.setItem('mtl-wasAutoDimmed', String(this.wasAutoDimmed));
    },

    /** Unified handler for toggling any layer on/off. */
    onToggleLayer(layerId) {
      switch (layerId) {
        case 'basemap':
          this.basemapEnabled = !this.basemapEnabled;
          this.applyLayerOpacity('basemap');
          break;
        case 'tracks':
          this.tracksEnabled = !this.tracksEnabled;
          this.applyTracksVisibility();
          break;
        case 'media':
          this.onToggleMediaLayer();
          break;
        case 'trackpoints':
          this.onToggleTrackPoints();
          break;
        case 'heatmap':
          this.onToggleHeatmapLayer();
          break;
        case 'wanderland':
        case 'veloland':
        case 'mountainbikeland':
        case 'wanderwege':
        case 'wmt-hiking':
        case 'wmt-cycling':
        case 'wmt-mtb':
          this.onToggleOverlay(layerId);
          break;
      }
      this.persistLayerStates();
    },

    /** Unified handler for changing layer opacity. */
    onLayerOpacityChange(layerId, value) {
      this.layerOpacities[layerId] = value;
      this.applyLayerOpacity(layerId);
      this.persistLayerStates();
    },

    /** Apply opacity for a specific layer to the map. */
    applyLayerOpacity(layerId) {
      const opacity = this.layerOpacities[layerId] / 100;
      switch (layerId) {
        case 'basemap':
          // CSS filter handles visual dimming via `baseMapStyle` computed.
          // Hillshade also depends on basemap dim → update it.
          this._applyHillshade();
          break;
        case 'tracks':
          if (this.overlayMap) {
            if (this.overlayMap.getLayer('tracks-layer'))
              this.overlayMap.setPaintProperty('tracks-layer', 'line-opacity', opacity);
            if (this.overlayMap.getLayer('tracks-dot-layer')) {
              this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-opacity', opacity);
              this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-stroke-opacity', opacity);
            }
            if (this.overlayMap.getLayer('tracks-overview-dots')) {
              this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-opacity', opacity);
              this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-stroke-opacity', opacity);
            }
          }
          // Hillshade also depends on tracks opacity → update it.
          this._applyHillshade();
          break;
        case 'media':
          if (this.overlayMap) {
            if (this.overlayMap.getLayer('media-clusters'))
              this.overlayMap.setPaintProperty('media-clusters', 'circle-opacity', opacity);
            if (this.overlayMap.getLayer('media-cluster-count'))
              this.overlayMap.setPaintProperty('media-cluster-count', 'text-opacity', opacity);
            if (this.overlayMap.getLayer('media-unclustered'))
              this.overlayMap.setPaintProperty('media-unclustered', 'circle-opacity', opacity);
          }
          break;
        case 'trackpoints':
          if (this.overlayMap && this.overlayMap.getLayer('track-points-layer'))
            this.overlayMap.setPaintProperty('track-points-layer', 'icon-opacity', opacity);
          break;
        case 'heatmap':
          if (this.overlayMap && this.overlayMap.getLayer('heatmap-layer'))
            this.overlayMap.setPaintProperty('heatmap-layer', 'heatmap-opacity', opacity * 0.92);
          break;
        case 'wanderland':
        case 'veloland':
        case 'mountainbikeland':
        case 'wanderwege':
        case 'wmt-hiking':
        case 'wmt-cycling':
        case 'wmt-mtb': {
          const layerName = `${layerId}-overlay`;
          if (this.overlayMap && this.overlayMap.getLayer(layerName)) {
            const overlay = MAP_OVERLAYS.find(o => o.id === layerId);
            const paint = this._overlayPaintForSlider(this.layerOpacities[layerId], overlay?.hueRotate);
            for (const [prop, val] of Object.entries(paint)) {
              this.overlayMap.setPaintProperty(layerName, prop, val);
            }
          }
          break;
        }
      }
    },

    /** Apply all current layer opacities after map init or reload. */
    applyAllLayerOpacities() {
      for (const layerId of Object.keys(this.layerOpacities)) {
        this.applyLayerOpacity(layerId);
      }
    },

    /**
     * Update terrain hillshade-exaggeration based on the basemap and tracks sliders.
     *
     * Hillshade is part of the basemap visual layer, so it should fade together
     * with the basemap slider. We also let the tracks slider dampen it slightly
     * so the terrain is less prominent when tracks are hidden.
     *
     *   basemap 100 % → 1.00 × base        (full relief)
     *   basemap   0 % → 0.00 × base        (no relief)
     *   tracks  100 % → ×  1.00            (no damping)
     *   tracks    0 % → ×  0.60            (extra soft)
     *
     * Base exaggeration is 0.55 at slider 100 %.
     */
    _applyHillshade() {
      if (!this.map || !this.map.getLayer('terrain-hillshade')) return;

      const basemapNorm = this.basemapEnabled
        ? Math.max(0, Math.min(1, this.layerOpacities.basemap / 100))
        : 0;
      const tracksNorm = Math.max(0, Math.min(1, this.layerOpacities.tracks / 100));
      const tracksFactor = 0.6 + 0.4 * tracksNorm; // 0.6 … 1.0

      const exaggeration = 0.55 * basemapNorm * tracksFactor;

      this.map.setPaintProperty('terrain-hillshade', 'hillshade-exaggeration', exaggeration);
    },

    /** Show / hide all track-related layers on the overlay map. */
    applyTracksVisibility() {
      if (!this.overlayMap) return;
      const vis = this.tracksEnabled ? 'visible' : 'none';
      for (const id of ['tracks-layer', 'tracks-dot-layer', 'tracks-overview-dots']) {
        if (this.overlayMap.getLayer(id))
          this.overlayMap.setLayoutProperty(id, 'visibility', vis);
      }
    },

    /** Toggle a Swiss Mobility overlay on/off without reinitialising the map. */
    onToggleOverlay(overlayId) {
      const idx = this.activeOverlays.indexOf(overlayId);
      if (idx === -1) {
        this.activeOverlays.push(overlayId);
        const overlay = MAP_OVERLAYS.find(o => o.id === overlayId);
        if (overlay && this.overlayMap) {
          if (!this.overlayMap.getSource(overlay.id)) {
            this.overlayMap.addSource(overlay.id, {
              type: 'raster',
              tiles: [overlay.url],
              tileSize: 256,
              attribution: overlay.attribution,
            });
          }
          if (!this.overlayMap.getLayer(`${overlay.id}-overlay`)) {
            this.overlayMap.addLayer({
              id: `${overlay.id}-overlay`,
              type: 'raster',
              source: overlay.id,
              minzoom: 0,
              maxzoom: 22,
              paint: this._overlayPaintForSlider(this.layerOpacities[overlayId], overlay.hueRotate),
            }, this._overlayBeforeId());
          }
        }
      } else {
        this.activeOverlays.splice(idx, 1);
        if (this.overlayMap) {
          if (this.overlayMap.getLayer(`${overlayId}-overlay`)) this.overlayMap.removeLayer(`${overlayId}-overlay`);
          if (this.overlayMap.getSource(overlayId)) this.overlayMap.removeSource(overlayId);
        }
      }
      localStorage.setItem('mtl-activeOverlays', JSON.stringify(this.activeOverlays));
    },

    startMapStatusPolling() {
      this.stopMapStatusPolling();
      const poll = async () => {
        try {
          const resp = await apiClient.get(`api/map/status`, {
            timeout: 8000,
          });
          const wasReady = this.mapServerStatus?.ready;
          this.mapServerStatus = resp.data;
          if (this.mapServerStatus?.ready) {
            this.stopMapStatusPolling();
            // Planet file just became ready — reload to switch OSM fallback → vector tiles
            if (!wasReady) {
              this.reloadMap(false);
            }
          }
        } catch {
          // ignore polling errors silently
        }
      };
      poll();
      this.mapStatusPollTimer = setInterval(poll, 5000);
    },

    stopMapStatusPolling() {
      if (this.mapStatusPollTimer) {
        clearInterval(this.mapStatusPollTimer);
        this.mapStatusPollTimer = null;
      }
    },

    async reloadMap(loadMedia) {
      const reloadTimer = startStartupTimer('reload', 'Reloading map state', { loadMedia });
      this.showLoader = true;

      // Reset state
      this.selectedTrackId = null;
      this.selectedFeature = null;
      this.closeSelectionPopup();
      this.closeSwissMobilityPopup();
      if (this.trackPointsPopup) { this.trackPointsPopup.remove(); this.trackPointsPopup = null; }
      this.trackPointsDetailsCache.clear();
      if (this.detailDebounceTimer) clearTimeout(this.detailDebounceTimer);
      if (this.detailAbortController) this.detailAbortController.abort();
      if (this.bulk10mController) { this.bulk10mController.abort(); this.bulk10mController = null; }
      this.trackPrecisions = new Map();

      // Clean up media overlay
      if (this.mediaOverlay && typeof this.mediaOverlay.destroy === 'function') {
        this.mediaOverlay.destroy();
      }
      this.mediaOverlay = null;
      this.mediaVisible = false;

      // Clean up heatmap overlay (keep heatmapVisible so it restores on next load)
      if (this.heatmapOverlay && typeof this.heatmapOverlay.destroy === 'function') {
        this.heatmapOverlay.destroy();
      }
      this.heatmapOverlay = null;

      // Clean up geo drawing overlay (it holds a reference to the old overlayMap)
      if (this.geoDrawingOverlay && typeof this.geoDrawingOverlay.destroy === 'function') {
        this.geoDrawingOverlay.destroy();
      }
      this.geoDrawingOverlay = null;

      // Clean up GPS marker (lives on overlay map which will be destroyed)
      if (this.gpsMarker) { this.gpsMarker.remove(); this.gpsMarker = null; }

      try {
        // Phase 3: Start track fetch in parallel with map tile loading.
        // initMap() creates MapLibre instances and waits for their 'load' events.
        // fetchTracksAndFallback() hits cache/network for track data.
        // The actual addTracksToMap() (inside loadMapData) needs the map to be loaded,
        // so loadMapData awaits _mapReadyPromise before touching map sources.
        this._mapReadyPromise = new Promise(resolve => { this._mapReadyResolve = resolve; });
        const trackDataPromise = this.fetchTracksAndFallback();
        await this.initMap();
        this._mapReadyResolve();
        // Map is ready — now wait for track data (may already be resolved from cache)
        await trackDataPromise;
        reloadTimer.success('Map reload completed');
      } catch (error) {
        reloadTimer.error('Map reload failed', describeError(error));
        throw error;
      }
    },

    async initMap() {
      const initTimer = startStartupTimer('mapinit', 'Initializing map');
      // Fetch map config from server (cached after first call)
      this.mapConfig = await fetchMapConfig();
      if (this.mapConfig.plannerEnabled && !this.toolDefs.some(t => t.id === 'planner')) {
        const adminIdx = this.toolDefs.findIndex(t => t.id === 'admin');
        const insertAt = adminIdx >= 0 ? adminIdx : this.toolDefs.length;
        this.toolDefs.splice(insertAt, 0, { id: 'planner', icon: 'bi bi-signpost-split', label: 'Planner' });
      }
      startupLog('mapinit', 'Map config resolved', {
        tileMode: this.mapConfig.tileMode,
        offline: this.mapConfig.offline ?? false,
        tileBaseUrl: this.mapConfig.tileBaseUrl,
      });

      // Preserve current viewport so theme switches don't jump the map position.
      // On first load, fall back to server-provided initial center (e.g. Porto in demo mode).
      let initialCenter = this.mapConfig.initialCenterLng != null
        ? [this.mapConfig.initialCenterLng, this.mapConfig.initialCenterLat]
        : this.mapCenter;
      let initialZoom = this.mapConfig.initialZoom ?? 10;
      if (this.overlayMap) {
        initialCenter = [this.overlayMap.getCenter().lng, this.overlayMap.getCenter().lat];
        initialZoom = this.overlayMap.getZoom();
      }

      // Tear down previous maps
      if (this.overlayMap) { this.overlayMap.remove(); this.overlayMap = undefined; }
      if (this.map) { this.map.remove(); this.map = undefined; }

      ensurePMTilesProtocol();
      // Pre-register PMTiles instances with force-cache fetch so Chrome serves
      // cached 206 responses from disk instead of revalidating every range request.
      if (pmtilesProtocol && this.mapConfig.tileBaseUrl && this.mapConfig.tilesetName) {
        const tileUrl = `${this.mapConfig.tileBaseUrl}/${this.mapConfig.tilesetName}.pmtiles`;
        pmtilesProtocol.add(createCachingPMTiles(tileUrl));
      }
      startupLog('mapinit', 'PMTiles protocol ready');

      // When offline, skip the map-status probe and planet-file check entirely.
      // When local mode online, check once whether the planet file is already ready.
      if (!this.mapConfig.offline && this.mapConfig.tileMode === 'local' && !this.mapServerStatus) {
        const statusTimer = startStartupTimer('mapstatus', 'Probing map server status');
        try {
          const resp = await apiClient.get(`api/map/status`, {
            timeout: 5000,
          });
          this.mapServerStatus = resp.data;
          statusTimer.success('Map server status received', {
            phase: resp.data?.phase,
            ready: resp.data?.ready,
          });
        } catch (error) {
          statusTimer.warn('Map server status probe failed', describeError(error));
          // Unreachable → treat as not ready, fall back to OSM
          this.mapServerStatus = { phase: 'unreachable', ready: false };
        }
      }

      // Build style based on config
      // If offline, use a lightweight raster fallback so the map 'load' event fires reliably.
      // If local but planet file not yet ready, use OSM raster temporarily.
      let style;
      let styleMode = 'unknown';
      if (this.mapConfig.offline) {
        // Try to use cached low-zoom PMTiles for a proper vector map background
        const lowzoom = await loadLowZoomFromCache(
          this.mapConfig.tileBaseUrl,
          this.mapConfig.lowzoomTilesetName,
        );
        if (lowzoom && pmtilesProtocol) {
          pmtilesProtocol.add(lowzoom);
          style = buildLocalVectorStyle(
            this.mapConfig.tileBaseUrl,
            this.mapConfig.lowzoomTilesetName,
            this.mapThemeSelected,
          );
          styleMode = 'offline-lowzoom-vector';
          console.log('Offline: using cached low-zoom vector tiles as base map');
        } else {
          style = buildFallbackRasterStyle();
          styleMode = 'offline-raster-fallback';
          console.log('Offline: no cached low-zoom tiles, using OSM raster fallback');
        }
      } else if (this.mapThemeSelected === 'swisstopo') {
        style = SWISSTOPO_STYLE_URL;
        styleMode = 'swisstopo';
      } else if (this.mapThemeSelected === 'swisstopo-color') {
        style = SWISSTOPO_COLOR_STYLE_URL;
        styleMode = 'swisstopo-color';
      } else if (this.mapConfig.tileMode !== 'local' || this.mapServerStatus?.ready) {
        style = buildLocalVectorStyle(
          this.mapConfig.tileBaseUrl,
          this.mapConfig.tilesetName,
          this.mapThemeSelected,
        );
        styleMode = 'local-vector';
      } else {
        style = buildRemoteRasterStyle(this.mapConfig.remoteTileUrl);
        styleMode = 'remote-raster';
      }
      startupLog('mapinit', 'Selected base-map style', {
        styleMode,
        tileMode: this.mapConfig.tileMode,
        offline: this.mapConfig.offline ?? false,
        mapServerReady: this.mapServerStatus?.ready ?? null,
      });

      // ── Base map: tiles, Swiss Mobility overlays, dim layer — passive ──
      startupLog('mapload', 'Creating base map instance', { styleMode });
      this.map = markRaw(new maplibregl.Map({
        container: this.$refs.mapBaseContainer,
        style,
        center: initialCenter,
        zoom: initialZoom,
        minZoom: MERCATOR_MIN_ZOOM,
        attributionControl: false,
        interactive: false, // base map just renders — overlay drives interaction
      }));

      this.map.on('styleimagemissing', (e) => {
        if (!this.map.hasImage(e.id)) {
          this.map.addImage(e.id, { width: 1, height: 1, data: new Uint8ClampedArray(4) });
        }
      });
      this.map.once('load', () => {
        startupLog('mapload', 'Base map load event received', { styleMode });
      });

      // Detect authentication failures from PMTiles / tile fetches and redirect to login.
      // PMTiles 401 errors bypass the axios interceptor, so this is the only recovery path.
      // All other map errors (blocked CDNs, CORS, DNS failures) are also logged for diagnostics.
      this.map.on('error', (e) => {
        const msg = e.error?.message || '';
        const tileId = e?.tile?.tileID ?? null;
        if (msg.includes('401')) {
          startupWarn('mapload', 'Map tile auth failure; redirecting to login', { message: msg });
          console.warn('Map tile auth failure (401) — redirecting to login');
          clearToken();
          this.$router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
        } else {
          startupWarn('mapload', 'MapLibre reported a base-map error', {
            message: msg,
            sourceId: e.sourceId ?? null,
            tile: tileId,
          });
          console.warn('[MTL] MapLibre error:', msg, '| source:', e.sourceId ?? '(unknown)', '| tile:', tileId ?? '', e.error);
        }
      });

      // ── Overlay map: tracks, highlights, media — handles ALL user interaction ──
      startupLog('mapload', 'Creating overlay map instance');
      this.overlayMap = markRaw(new maplibregl.Map({
        container: this.$refs.mapOverlayContainer,
        style: { version: 8, sources: {}, layers: [] },
        center: initialCenter,
        zoom: initialZoom,
        minZoom: MERCATOR_MIN_ZOOM,
        attributionControl: false,
      }));
      this.overlayMap.once('load', () => {
        startupLog('mapload', 'Overlay map load event received');
      });

      // Controls live on overlay map (HTML elements — always clickable on top)
      this.overlayMap.addControl(new maplibregl.NavigationControl(), 'top-left');
      this._globeControl = new GlobeControl(() => this.toggleGlobeMode());
      this.overlayMap.addControl(this._globeControl, 'top-left');
      this.overlayMap.addControl(new maplibregl.ScaleControl({ maxWidth: 100 }), 'bottom-left');
      this.overlayMap.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');

      // Initialize media overlay on the colourful overlay map
      this.mediaOverlay = markRaw(new MediaOverlay(
        this.overlayMap,
        (mediaId) => {
          this.mediaSheetMediaId = mediaId;
          this.mediaSheetVisible = true;
          this._buildMediaNavList(mediaId);
        },
        (points) => {
          this.mediaLoadedPoints = points;
        },
      ));

      // Initialize heatmap overlay
      this.heatmapOverlay = markRaw(new HeatmapOverlay(this.overlayMap));

      // Resize maps when container size changes (e.g. nav panel expand/collapse)
      this._resizeObserver = new ResizeObserver(() => {
        this.map?.resize();
        this.overlayMap?.resize();
        // Recompute globe minZoom whenever the viewport size changes (phone vs desktop)
        if (this.globeMode) {
          const minZoom = computeGlobeMinZoom(this.$refs.mapOverlayContainer);
          this.map?.setMinZoom(minZoom);
          this.overlayMap?.setMinZoom(minZoom);
        }
      });
      this._resizeObserver.observe(this.$refs.mapOverlayContainer);

      // Wait for both maps to be ready
      const waitForMapLoad = (mapInstance, label) => new Promise((resolve) => {
        if (mapInstance.loaded()) {
          startupLog('mapload', `${label} already loaded`);
          resolve(true);
          return;
        }
        mapInstance.once('load', () => resolve(true));
      });

      const mapLoadWatchdog = window.setTimeout(() => {
        startupWarn('mapload', 'Map load watchdog exceeded', {
          styleMode,
          baseLoaded: this.map?.loaded() ?? false,
          overlayLoaded: this.overlayMap?.loaded() ?? false,
          tileMode: this.mapConfig?.tileMode,
          offline: this.mapConfig?.offline ?? false,
          mapServerReady: this.mapServerStatus?.ready ?? null,
        });
      }, MAP_LOAD_WATCHDOG_MS);

      try {
        await Promise.all([
          waitForMapLoad(this.map, 'Base map'),
          waitForMapLoad(this.overlayMap, 'Overlay map'),
        ]);
      } finally {
        clearTimeout(mapLoadWatchdog);
      }
      startupLog('mapload', 'Both map instances finished loading', { styleMode });
      initTimer.success('Map initialization completed', { styleMode });

      // ── View sync: overlay map drives the base map ──
      const syncBase = () => {
        if (this._syncingView) return;
        this._syncingView = true;
        this.map.jumpTo({
          center: this.overlayMap.getCenter(),
          zoom: this.overlayMap.getZoom(),
          bearing: this.overlayMap.getBearing(),
          pitch: this.overlayMap.getPitch(),
        });
        this._syncingView = false;
      };
      this.overlayMap.on('move', syncBase);

      // Break GPS follow mode when the user manually pans the map
      this.overlayMap.on('dragstart', () => {
        if (this.gpsFollowing) this.gpsFollowing = false;
      });

      // Apply any active Swiss Mobility overlays on the overlay map
      this.applyActiveOverlays();

      // Apply initial globe projection based on current zoom
      this.zoom = this.overlayMap.getZoom();
      this.updateGlobeState();

      // Click handler for track selection (on overlay map — it receives all interaction)
      this.overlayMap.on('click', (e) => {
        if (this.measureToolActive || this.plannerToolActive || this.geoDrawingParamDef) return;

        // Dismiss any open popups on every map click
        this.closeSelectionPopup();
        this.closeSwissMobilityPopup();

        // Skip if user clicked on an individual track point (handled by its own listener)
        const pointFeatures = this.overlayMap.queryRenderedFeatures(e.point, { layers: ['track-points-layer'] });
        if (pointFeatures && pointFeatures.length > 0) return;

        // Skip if user clicked on a media cluster or single media point (handled by MediaOverlay)
        const mediaLayers = ['media-clusters', 'media-unclustered'].filter(layerId => this.overlayMap.getLayer(layerId));
        if (mediaLayers.length > 0) {
          const mediaFeatures = this.overlayMap.queryRenderedFeatures(e.point, { layers: mediaLayers });
          if (mediaFeatures && mediaFeatures.length > 0) return;
        }

        const lngLat = e.lngLat;

        // Identify Swiss Mobility routes at the clicked point (fire-and-forget, shown if overlay active)
        if (this.activeOverlays.length > 0) {
          this.identifySwissMobilityRoutes(lngLat, e.point);
        }

        const isTouchDevice = 'ontouchstart' in window;
        const tapRadiusPx = isTouchDevice ? 24 : 16;
        const point2 = this.overlayMap.unproject([e.point.x + tapRadiusPx, e.point.y]);
        const distanceInMeters = haversineDistance(lngLat.lat, lngLat.lng, point2.lat, point2.lng);

        if (this.proximityAbortController) this.proximityAbortController.abort();
        this.proximityAbortController = new AbortController();
        const signal = this.proximityAbortController.signal;

        fetchTrackIdsWithinDistanceOfPoint(lngLat.lng, lngLat.lat, distanceInMeters, signal)
          .then(gpsTrackIds => {
            if (!gpsTrackIds || gpsTrackIds.length === 0) {
              this.deselectTrack();
              return;
            }
            if (gpsTrackIds.length === 1) {
              this.selectTrackById(gpsTrackIds[0]);
              this.openTrackDetails(gpsTrackIds[0], TRACK_DETAILS_MAP_DETENT);
            } else {
              this.showTrackSelectionPopup(e.point, gpsTrackIds);
            }
          })
          .catch(err => {
            if (err.name === 'AbortError' || axios.isCancel(err)) return;
            console.error('Track proximity query failed:', err);
          });
      });

      this.overlayMap.on('zoomend', () => {
        this.zoom = this.overlayMap.getZoom();
        this.updateGlobeState();
        this.updateTrackLineWidth();
        this.scheduleDetailCheck();
        this.checkDemoAreaBounds();
        console.log(`[zoom] ${this.zoom.toFixed(3)} | ${this.globeMode ? 'globe' : 'mercator'}`);
      });

      this.overlayMap.on('moveend', () => {
        this.scheduleDetailCheck();
        this.checkDemoAreaBounds();
      });

      // Background: cache the low-zoom PMTiles for offline use (only once ready, skip when already offline)
      if (!this.mapConfig.offline && this.mapConfig.tileMode === 'local') {
        if (this.mapServerStatus?.ready) {
          ensureLowZoomCached(this.mapConfig.tileBaseUrl, this.mapConfig.lowzoomTilesetName).catch(e => {
            startupWarn('mapcache', 'Low-zoom cache warmup failed', describeError(e));
            console.warn('Low-zoom cache failed:', e);
          });
        } else {
          startupLog('mapstatus', 'Starting map-status polling until local tiles are ready');
          // Planet file not yet ready — poll and auto-switch to vector when complete
          this.startMapStatusPolling();
        }
      }
    },

    /** Update globe projection state based on current zoom level (hysteresis). */
    updateGlobeState() {
      if (this.globeMode && this.zoom > GLOBE_EXIT_ZOOM) {
        // Zoomed in past the exit threshold — leave globe and reset user-override
        this.globeMode = false;
        this.globeUserDisabled = false;
        this.applyGlobeProjection();
      } else if (!this.globeMode && !this.globeUserDisabled && this.zoom < GLOBE_ENTER_ZOOM) {
        // Zoomed out past the enter threshold — auto-activate globe
        this.globeMode = true;
        this.applyGlobeProjection();
      }
      // Show the toggle button across the whole hysteresis band
      const inGlobeZone = this.zoom < GLOBE_EXIT_ZOOM;
      this._globeControl?.setVisible(inGlobeZone);
      this._globeControl?.setActive(this.globeMode);
    },

    /** Set MapLibre projection on both maps and let MapLibre handle the morph. */
    applyGlobeProjection() {
      const proj = this.globeMode ? { type: 'globe' } : { type: 'mercator' };

      // Clamp minZoom: dynamically fitted to viewport for globe, fixed floor for mercator
      const minZoom = this.globeMode
        ? computeGlobeMinZoom(this.$refs.mapOverlayContainer)
        : MERCATOR_MIN_ZOOM;
      this.map?.setMinZoom(minZoom);
      this.overlayMap?.setMinZoom(minZoom);

      // Apply in the same frame so both maps morph together without exposing the black background.
      this.map?.setProjection(proj);
      this.overlayMap?.setProjection(proj);
    },

    /** Toggle globe mode on/off (user action via the control button). */
    toggleGlobeMode() {
      if (this.globeMode) {
        this.globeUserDisabled = true;
        this.globeMode = false;
      } else {
        this.globeUserDisabled = false;
        this.globeMode = true;
      }
      this.applyGlobeProjection();
      this._globeControl?.setActive(this.globeMode);
    },

    /**
     * In demo mode, check whether the map viewport extends beyond the demo tile area.
     * Shows an info toast with a 3-minute cooldown to avoid spamming the user.
     */
    checkDemoAreaBounds() {
      const bbox = this.mapConfig?.demoAreaBbox;
      if (!bbox || bbox.length !== 4) return; // not demo mode or no bounds configured

      const bounds = this.overlayMap.getBounds();
      const [west, south, east, north] = bbox;

      // Viewport is inside the demo area — nothing to warn about
      if (bounds.getWest() >= west && bounds.getSouth() >= south &&
          bounds.getEast() <= east && bounds.getNorth() <= north) {
        return;
      }

      // Cooldown: don't show again within 3 minutes
      const now = Date.now();
      if (now - _lastDemoAreaToastTime < DEMO_AREA_TOAST_COOLDOWN_MS) return;
      _lastDemoAreaToastTime = now;

      this.$toast.add({
        severity: 'info',
        summary: 'Demo Mode',
        detail: 'Map tiles are only available for the Porto area. Zooming or panning further may show grey tiles.',
        life: 8000,
      });
    },

    /** Update the line width of highlight and dot layers based on current zoom. */
    updateTrackLineWidth() {
      if (!this.overlayMap) return;
      const zoom = this.overlayMap.getZoom();
      let lineWeight = 4;
      let correction = 14 - (zoom * 1.3);
      if (correction < 0) correction = 0;
      lineWeight = Math.round(lineWeight + correction);

      // tracks-layer uses a GPU-native interpolate expression — no JS update needed.
      if (this.overlayMap.getLayer('tracks-highlight-layer')) {
        this.overlayMap.setPaintProperty('tracks-highlight-layer', 'line-width', lineWeight * 1.5);
      }
      if (this.overlayMap.getLayer('tracks-highlight-dash-layer')) {
        this.overlayMap.setPaintProperty('tracks-highlight-dash-layer', 'line-width', lineWeight * 1.5);
      }
      if (this.overlayMap.getLayer('tracks-dot-layer')) {
        this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-radius', Math.round(lineWeight / 2));
      }
      if (this.overlayMap.getLayer('tracks-highlight-circle-layer')) {
        this.overlayMap.setPaintProperty('tracks-highlight-circle-layer', 'circle-radius', Math.round(lineWeight / 2 * 1.5));
      }
    },

    onLocationUpdate(geolocationPosition) {
      const latitude = geolocationPosition.coords.latitude;
      const longitude = geolocationPosition.coords.longitude;
      this.mapCenter = [longitude, latitude];
      this.gpsLocation = [latitude, longitude];

      // Only auto-centre when in follow mode — user panning breaks this (see dragstart listener)
      if (this.gpsFollowing) {
        this.overlayMap.flyTo({ center: [longitude, latitude], zoom: this.overlayMap.getZoom() });
      }

      // Always update (or create) the GPS marker regardless of follow state
      if (this.gpsMarker) {
        this.gpsMarker.setLngLat([longitude, latitude]);
      } else {
        const el = document.createElement('div');
        el.style.cssText = 'width:16px;height:16px;border-radius:50%;background:red;border:2px solid white;box-shadow:0 0 4px rgba(0,0,0,0.4);';
        this.gpsMarker = markRaw(new maplibregl.Marker({ element: el })
          .setLngLat([longitude, latitude])
          .addTo(this.overlayMap));
      }
    },

    onGPSDeviceEnabledDisabled(deviceEnabled) {
      this.gpsDeviceEnabledDisabled = deviceEnabled;
      if (deviceEnabled) {
        this.gpsFollowing = true; // always start in follow mode when GPS is turned on
      } else {
        this.gpsFollowing = false;
        if (this.gpsMarker) {
          this.gpsMarker.remove();
          this.gpsMarker = null;
        }
      }
    },

    onAnimationStartEvent() {
      // Timeline panel now self-manages its UI
    },

    onAnimationFinishedEvent() {
      console.log("map: onAnimationFinishedEvent");
    },

    onAnimationStopEvent() {
      // Timeline panel now self-manages its UI
    },

    onAnimateEvent(event) {
      // available if needed for external integrations
    },

    async onMapThemeChangeEvent(themeCode) {
      if (themeCode && typeof themeCode === 'string') {
        this.mapThemeSelected = themeCode;
        localStorage.setItem('mapTheme', themeCode);
      }
      this.showLoader = true;
      await this.initMap();
      await this.addTracksToMap();
      this.showLoader = false;
    },

    async loadMapData(fetchResult) {
      // Wait for map to be ready (needed when track fetch runs in parallel with initMap)
      if (this._mapReadyPromise) await this._mapReadyPromise;
      this.geojson = markRaw(fetchResult.geojson);
      this.gpsTracksById = markRaw(fetchResult.gpsTracksById);
      this.gpsTrackIdToFeature = markRaw(fetchResult.gpsTrackIdToFeature);
      if (fetchResult.trackPrecisions) {
        this.trackPrecisions = markRaw(fetchResult.trackPrecisions);
      }
      await this.addTracksToMap();
    },

    /** Fit the overlay map viewport to the bounds of the given GeoJSON FeatureCollection. */
    fitToTrackBounds(geojson) {
      if (!this.overlayMap || !geojson?.features?.length) return;
      const bounds = new maplibregl.LngLatBounds();
      for (const f of geojson.features) {
        if (!f.geometry) continue;
        if (f.geometry.type === 'Point') {
          bounds.extend(f.geometry.coordinates);
        } else if (f.geometry.type === 'LineString') {
          for (const coord of f.geometry.coordinates) {
            bounds.extend(coord);
          }
        }
      }
      if (!bounds.isEmpty()) {
        this.overlayMap.fitBounds(bounds, { padding: 40, maxZoom: 14 });
      }
    },

    async onAdminReloadTracks(done) {
      this.cachedTracksLoaded = false;
      try {
        await this.fetchTracksAndFallback();
      } finally {
        done?.();
      }
    },

    async fetchTracksAndFallback() {
      const timer = startStartupTimer('tracks', 'Resolving startup tracks and fallbacks');
      this.cachedTracksLoaded = false;
      this.initialLoadDone = false;

      const authStatus = await checkServerAuth();
      startupLog('tracks', 'Auth status checked before track load', { authStatus });
      if (authStatus === 'auth-error') {
        timer.warn('Auth check failed; redirecting to login');
        this.showLoader = false;
        clearToken();
        router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
        return;
      }

      // ── Phase 2: Cache-first — show cached tracks instantly, then sync ──
      const cachePopulated = await trackStore.isCachePopulated();
      if (cachePopulated) {
        startupLog('tracks', 'Cache populated — loading cached tracks immediately');
        const cached = await trackStore.loadFromCache();
        if (cached && cached.geojson?.features?.length > 0) {
          await this.loadMapData(cached);
          this.cachedTracksLoaded = true;
          this.showLoader = false;
          this.$emit('tracks-loaded');
          startupLog('tracks', 'tracks-loaded emitted from instant cache path', {
            featureCount: cached.geojson.features.length,
          });
          timer.success('Instant cache load completed', { featureCount: cached.geojson.features.length });

          // Background sync: fetch fresh data from server and update in-place
          this.$emit('syncing', true);
          this._backgroundSync(timer);
          return;
        }
      }

      // ── No cache — original flow with reduced fallback timeout ──
      const CACHE_FALLBACK_TIMEOUT_MS = 3000;

      let fallbackTimer = setTimeout(async () => {
        if (!this.cachedTracksLoaded) {
          startupWarn('tracks', 'Server tracks still pending; attempting IndexedDB fallback', {
            fallbackAfterMs: CACHE_FALLBACK_TIMEOUT_MS,
          });
          let cached = await trackStore.loadFromCache();
          if (cached && !this.cachedTracksLoaded) {
            startupLog('tracks', 'Using cached tracks after startup fallback timeout', {
              featureCount: cached.geojson?.features?.length ?? 0,
            });
            await this.loadMapData(cached);
            this.isOffline = true;
            this.cachedTracksLoaded = true;
            this.showLoader = false;
            this.$emit('tracks-loaded');
            startupLog('tracks', 'tracks-loaded emitted from cached fallback');
            this.fitToTrackBounds(cached.geojson);
          }
        }
      }, CACHE_FALLBACK_TIMEOUT_MS);

      try {
        const prefetched = trackStore.consumePrefetch(OVERVIEW_PRECISION);
        startupLog('tracks', prefetched ? 'Using prefetched overview tracks' : 'No prefetched overview tracks; fetching live');
        let serverData = await (prefetched ?? trackStore.fetchAllTracks(OVERVIEW_PRECISION));
        clearTimeout(fallbackTimer);
        this.totalTrackCount = serverData.standardFilterCount;
        startupLog('tracks', 'Server tracks ready for startup render', {
          featureCount: serverData.geojson?.features?.length ?? 0,
          standardFilterCount: serverData.standardFilterCount,
        });

        if (this.cachedTracksLoaded) {
          timer.success('Server tracks arrived after cached fallback was already shown');
          this.isOffline = false;
          this.$toast.add({ severity: 'success', summary: 'Online', detail: 'Back online — tracks reloaded.', life: 3000 });
          this.loadAllTracksAt10m(serverData.filterResult);
        } else {
          this.cachedTracksLoaded = true;
          await this.loadMapData(serverData);
          this.isOffline = false;
          this.showLoader = false;
          this.$emit('tracks-loaded');
          startupLog('tracks', 'tracks-loaded emitted from server startup fetch');
          timer.success('Startup tracks loaded from server');
          this.loadAllTracksAt10m(serverData.filterResult);
        }
        this.initialLoadDone = true;
      } catch (e) {
        clearTimeout(fallbackTimer);
        timer.error('Startup track resolution failed', describeError(e));
        if (isAuthError(e)) {
          this.showLoader = false;
          clearToken();
          router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
          return;
        }
        if (!this.cachedTracksLoaded) {
          let cached = await trackStore.loadFromCache();
          if (cached) {
            startupLog('tracks', 'Recovered from startup failure using cached tracks', {
              featureCount: cached.geojson?.features?.length ?? 0,
            });
            await this.loadMapData(cached);
            this.isOffline = true;
            this.cachedTracksLoaded = true;
            this.showLoader = false;
            this.$emit('tracks-loaded');
            startupLog('tracks', 'tracks-loaded emitted from cached recovery');
            this.fitToTrackBounds(cached.geojson);
          } else {
            this.showLoader = false;
            startupWarn('tracks', 'No cached tracks available; emitting load-failed');
            this.$emit('load-failed');
          }
        } else {
          this.isOffline = true;
        }
        this.scheduleRetry();
        this.initialLoadDone = true;
      }
    },

    /**
     * Background sync after showing cached tracks.
     * Fetches fresh data from server, updates the map seamlessly, then loads 10m.
     */
    async _backgroundSync(timer) {
      try {
        const prefetched = trackStore.consumePrefetch(OVERVIEW_PRECISION);
        startupLog('sync', 'Background sync: fetching server data', { hasPrefetch: !!prefetched });
        const serverData = await (prefetched ?? trackStore.fetchAllTracks(OVERVIEW_PRECISION));
        this.totalTrackCount = serverData.standardFilterCount;

        // Replace map data with fresh server data
        await this.loadMapData(serverData);
        this.isOffline = false;
        startupLog('sync', 'Background sync: map updated with server data', {
          featureCount: serverData.geojson?.features?.length ?? 0,
        });
        timer.success('Background sync completed');

        this.$emit('syncing', false);
        this.loadAllTracksAt10m(serverData.filterResult);
        this.initialLoadDone = true;
      } catch (e) {
        startupWarn('sync', 'Background sync failed — using cached data', describeError(e));
        if (isAuthError(e)) {
          clearToken();
          router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
          return;
        }
        this.isOffline = true;
        this.$emit('syncing', false);
        this.scheduleRetry();
        this.initialLoadDone = true;
      }
    },

    async loadAllTracksAt10m(filterResult) {
      if (this.bulk10mController) this.bulk10mController.abort();
      this.bulk10mController = new AbortController();
      const signal = this.bulk10mController.signal;
      this.loadingTracks10m = true;

      try {
        console.log('Background: loading all tracks at 10m…');
        const data10m = await (trackStore.consumePrefetch(10) ?? trackStore.fetchAllTracks(10, signal, filterResult));
        if (signal.aborted) return;

        for (const [trackId, feature] of data10m.gpsTrackIdToFeature) {
          if (signal.aborted) return;
          const numId = Number(trackId);
          const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
          if (currentPrecision <= 10) continue;

          // Update the in-memory feature coordinates
          const existingFeature = this.gpsTrackIdToFeature.get(numId);
          if (existingFeature?.geometry) {
            const newCoords = feature.geometry.coordinates;
            existingFeature.geometry.coordinates = newCoords;
            // Restore geometry type: features may be Point (degenerate/empty at overview
            // precision) but now have real LineString coords at higher precision.
            if (existingFeature.geometry.type === 'Point' && Array.isArray(newCoords) && newCoords.length > 1) {
              existingFeature.geometry.type = 'LineString';
            }
          }
          this.trackPrecisions.set(numId, 10);
          const gpsTrack = data10m.gpsTracksById.get(trackId);
          if (gpsTrack) this.gpsTracksById.set(numId, gpsTrack);
        }

        // Update the source data to reflect new coordinates
        this.updateTracksSource();
        console.log('Background 10m load complete');
      } catch (e) {
        if (signal.aborted || axios.isCancel(e)) {
          this.loadingTracks10m = false;
          return;
        }
        console.warn('Background 10m load failed:', e);
      } finally {
        this.loadingTracks10m = false;
      }
    },

    /** Update the MapLibre 'tracks' source with the current geojson data. */
    updateTracksSource() {
      if (!this.overlayMap || !this.geojson) return;
      const source = this.overlayMap.getSource('tracks');
      if (source) {
        source.setData(this.geojson);
      }
      // Keep heatmap density in sync as track precision improves
      if (this.heatmapOverlay) {
        this.heatmapOverlay.updateData(this.geojson);
      }
    },

    onBrowserOnline() {
      if (!this.isOffline) return;
      console.log('Browser online event detected — retrying immediately');
      if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
      this.retryCount = 0;
      this.performBackgroundRetry();
    },

    scheduleRetry() {
      const MAX_RETRIES = 10;
      if (this.retryCount >= MAX_RETRIES) return;
      const backoffMs = Math.min(5_000 * Math.pow(1.5, this.retryCount), 60_000);
      if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
      this.retryTimeoutId = setTimeout(() => this.performBackgroundRetry(), backoffMs);
    },

    async performBackgroundRetry() {
      this.retryCount++;
      try {
        let serverData = await trackStore.fetchAllTracks(OVERVIEW_PRECISION);
        await this.loadMapData(serverData);
        this.isOffline = false;
        this.retryCount = 0;
        this.loadAllTracksAt10m(serverData.filterResult);
        this.scheduleDetailCheck();
        // If the initial map style was the offline raster fallback (e.g. the
        // /api/map/config call timed out on first login), rebuild the style
        // now that connectivity is back — no page reload needed.
        if (this.mapConfig?.offline) {
          try {
            console.log('Recovered from offline fallback — rebuilding map style with real config');
            clearMapConfigCache();
            await this.initMap();
          } catch (rebuildErr) {
            console.warn('Failed to rebuild map style after recovery:', rebuildErr);
          }
        }
        this.$toast.add({ severity: 'success', summary: 'Online', detail: 'Back online — tracks reloaded.', life: 3000 });
      } catch (e) {
        if (isAuthError(e)) {
          clearToken();
          router.push({ path: '/login', query: { reason: 'expired' } }).catch(() => {});
          return;
        }
        this.scheduleRetry();
      }
    },

    onMeasureActiveChanged(isActive) {
      this.measureToolActive = isActive;
    },

    onPlannerActiveChanged(isActive) {
      this.plannerToolActive = isActive;
    },

    closeAllToolsExcept(skipRefName) {
      const toolRefs = ['infoTool', 'animateTool', 'measureTool', 'plannerTool', 'statistics', 'filterTool', 'mapSettingsTool', 'gpsLocate', 'adminTool'];
      for (const name of toolRefs) {
        if (name !== skipRefName && this.$refs[name]?.close) {
          this.$refs[name].close();
        }
      }
    },

    onToolSelect(toolId) {
      const toolMap = {
        animate: 'animateTool',
        measure: 'measureTool',
        planner: 'plannerTool',
        stats: 'statistics',
        filter: 'filterTool',
        map: 'mapSettingsTool',
        gps: 'gpsLocate',
        admin: 'adminTool',
      };

      const refName = toolMap[toolId];
      if (!refName) return;

      // ── GPS: 3-state cycle ──────────────────────────────────────────────────
      // OFF  → tap → ON + following (blue dot, map tracks position)
      // ON + following → drag map → drifted (amber dot, marker still visible)
      // ON + drifted  → tap → re-centre + re-engage following (blue dot again)
      // ON + following → tap → OFF (same as: turn off GPS)
      if (toolId === 'gps') {
        if (this.gpsDeviceEnabledDisabled && !this.gpsFollowing) {
          // Re-centre and re-engage following without toggling GPS off
          this.gpsFollowing = true;
          if (this.gpsLocation) {
            this.overlayMap.flyTo({ center: [this.gpsLocation[1], this.gpsLocation[0]], zoom: this.overlayMap.getZoom() });
          }
          this.activeToolId = null;
          return;
        }
        // Otherwise: toggle GPS on/off (GpsLocate handles the watchPosition lifecycle)
        this.closeAllToolsExcept(refName);
        this.$refs[refName]?.toggle();
        this.activeToolId = null;
        return;
      }
      // ── All other tools ─────────────────────────────────────────────────────

      const isTogglingOff = this.activeToolId === toolId;

      this.closeAllToolsExcept(refName);
      const ref = this.$refs[refName];
      if (ref?.toggle) {
        ref.toggle();
      }
      if (toolId === 'gps') {
        this.activeToolId = null;
      } else {
        this.activeToolId = isTogglingOff ? null : toolId;
      }
    },

    onToolOpened(refName) {
      this.closeAllToolsExcept(refName);
      const idMap = { animateTool: 'animate', measureTool: 'measure', plannerTool: 'planner', statistics: 'stats', filterTool: 'filter', mapSettingsTool: 'map', gpsLocate: 'gps', adminTool: 'admin' };
      this.activeToolId = idMap[refName] || null;
      // Show existing geo shapes when the filter panel opens
      if (refName === 'filterTool') {
        this.$nextTick(() => {
          if (!this.geoDrawingOverlay && this.overlayMap) {
            this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
          }
          this.renderExistingGeoShapes();
        });
      }
    },

    onToolClosed() {
      this.activeToolId = null;
      // Clear geo shape overlays when filter sheet is closed (unless actively drawing)
      if (!this.geoDrawingParamDef && this.geoDrawingOverlay) {
        this.geoDrawingOverlay.clearAll();
      }
    },

    // ─── Progressive detail loading ──────────────────────────────────

    scheduleDetailCheck() {
      if (this.detailDebounceTimer) clearTimeout(this.detailDebounceTimer);
      this.detailDebounceTimer = setTimeout(() => this.checkViewportPrecision(), DETAIL_DEBOUNCE_MS);
    },

    checkViewportPrecision() {
      if (!this.overlayMap || !this.geojson || this.isOffline) return;

      const zoom = this.overlayMap.getZoom();

      // Update track points layer only when zoom is high enough to show them
      if (this.trackPointsVisible && zoom >= TRACK_POINTS_MIN_ZOOM) {
        this.updateTrackPointsSource();
      }

      const targetPrecision = precisionForZoom(zoom);

      const needsViewportFilter = (targetPrecision === 1);
      let bounds;
      if (needsViewportFilter) {
        const mapBounds = this.overlayMap.getBounds();
        const sw = mapBounds.getSouthWest();
        const ne = mapBounds.getNorthEast();
        const latPad = (ne.lat - sw.lat) * DETAIL_BOUNDS_PADDING;
        const lngPad = (ne.lng - sw.lng) * DETAIL_BOUNDS_PADDING;
        bounds = {
          minLat: sw.lat - latPad, minLng: sw.lng - lngPad,
          maxLat: ne.lat + latPad, maxLng: ne.lng + lngPad,
        };
      }

      const tracksToAdjust = [];
      for (const [trackId] of this.gpsTrackIdToFeature) {
        const numId = Number(trackId);
        const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
        if (currentPrecision === targetPrecision) continue;

        if (needsViewportFilter) {
          const track = this.gpsTracksById.get(numId);
          if (track?.bboxMinLat != null) {
            if (track.bboxMaxLat < bounds.minLat || track.bboxMinLat > bounds.maxLat ||
                track.bboxMaxLng < bounds.minLng || track.bboxMinLng > bounds.maxLng) continue;
          }
        }
        tracksToAdjust.push(numId);
      }

      if (tracksToAdjust.length === 0) return;

      if (this.detailAbortController) this.detailAbortController.abort();
      this.detailAbortController = new AbortController();

      const center = this.overlayMap.getCenter();
      tracksToAdjust.sort((a, b) => {
        const aT = this.gpsTracksById.get(a);
        const bT = this.gpsTracksById.get(b);
        const aD = (aT?.centerLat != null) ? haversineDistance(center.lat, center.lng, aT.centerLat, aT.centerLng) : Infinity;
        const bD = (bT?.centerLat != null) ? haversineDistance(center.lat, center.lng, bT.centerLat, bT.centerLng) : Infinity;
        return aD - bD;
      });

      console.log(`Adjusting ${tracksToAdjust.length} tracks to ${targetPrecision}m (zoom ${zoom})`);
      this.processDetailQueue(tracksToAdjust, targetPrecision, this.detailAbortController.signal);
    },

    async processDetailQueue(trackIds, targetPrecision, signal) {
      const queue = [...trackIds];
      _detailFetchInFlight = 0;
      let changed = false;

      // At 1m precision each upgrade meaningfully changes what track-points
      // should be rendered on the map. Rather than waiting for the whole
      // queue to drain, flush the map sources progressively so the user
      // sees individual GPS points as they come in (otherwise tracks keep
      // showing coarse 10m spacing until every track is done).
      const progressive = targetPrecision === 1;
      let pendingFlush = false;
      const flush = () => {
        if (!pendingFlush || signal.aborted) return;
        pendingFlush = false;
        this.updateTracksSource();
        this.updateTrackPointsSource();
      };

      const fetchNext = async () => {
        while (queue.length > 0 && !signal.aborted) {
          const trackId = queue.shift();
          const currentPrecision = this.trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
          if (currentPrecision === targetPrecision) continue;

          _detailFetchInFlight++;
          try {
            const { coordinates, gpsTrack } = await trackStore.requestTrackAtPrecision(trackId, targetPrecision, signal);
            if (signal.aborted) return;

            // Update in-memory feature
            const feature = this.gpsTrackIdToFeature.get(trackId);
            if (feature?.geometry) {
              feature.geometry.coordinates = coordinates;
              // Restore geometry type: features may be Point (degenerate/empty at overview
              // precision) but now have real LineString coords at higher precision.
              if (feature.geometry.type === 'Point' && Array.isArray(coordinates) && coordinates.length > 1) {
                feature.geometry.type = 'LineString';
              }
              changed = true;
              pendingFlush = true;
            }
            this.trackPrecisions.set(trackId, targetPrecision);
            this.gpsTracksById.set(trackId, gpsTrack);

            if (progressive) flush();
          } catch (e) {
            if (axios.isCancel(e) || signal.aborted) return;
            console.warn(`Detail fetch failed for track ${trackId}:`, e);
          } finally {
            _detailFetchInFlight--;
          }
        }
      };

      const workers = [];
      const concurrency = targetPrecision === 1 ? DETAIL_MAX_CONCURRENT_1M : DETAIL_MAX_CONCURRENT;
      for (let i = 0; i < Math.min(concurrency, queue.length); i++) {
        workers.push(fetchNext());
      }
      await Promise.allSettled(workers);

      // Final flush (covers non-progressive path, and any trailing update
      // missed because progressive flush was skipped due to abort).
      if (changed && !signal.aborted) {
        this.updateTracksSource();
        this.updateTrackPointsSource();
      }
    },

    async onToggleMediaLayer() {
      if (!this.mediaOverlay || this.mediaBusy) return;
      this.mediaBusy = true;
      try {
        if (this.mediaOverlay.isVisible()) {
          this.mediaOverlay.hide();
          this.mediaVisible = false;
        } else {
          await this.mediaOverlay.show();
          this.mediaVisible = true;
        }
      } finally {
        this.mediaBusy = false;
      }
    },

    onToggleHeatmapLayer() {
      if (!this.heatmapOverlay || !this.geojson) return;
      if (this.heatmapOverlay.isVisible()) {
        this.heatmapOverlay.hide();
        this.heatmapVisible = false;
        localStorage.setItem('mtl-heatmapVisible', 'false');
      } else {
        this.heatmapOverlay.show(this.geojson);
        this.heatmapVisible = true;
        localStorage.setItem('mtl-heatmapVisible', 'true');
      }
    },

    async addTracksToMap() {
      if (!this.overlayMap || !this.geojson) return;
      console.log("add tracks to map: START");
      this.visibleTrackCount = 0;
      // Reset group visibility when tracks are reloaded
      this.hiddenGroups = new Set();

      let clientFilterConfig = await FilterService.loadClientFilterConfig();
      let palette = clientFilterConfig?.palette;
      let hasPalette = palette && !palette.isEmptyColorPalette();
      palette.reset();
      if (hasPalette) {
        this.filterActive = true;
        // Auto-dim basemap if it's at full opacity and not already auto-dimmed
        if (this.layerOpacities.basemap >= 80 && !this.wasAutoDimmed) {
          this.layerOpacities.basemap = 60;
          this.wasAutoDimmed = true;
          this.persistLayerStates();
        }
      } else {
        this.filterActive = !FilterService.isStandardFilterWithStandardParams(clientFilterConfig);
        // Undo auto-dim if filter removed
        if (this.wasAutoDimmed) {
          this.layerOpacities.basemap = 100;
          this.wasAutoDimmed = false;
          this.persistLayerStates();
          this.$toast.add({
            severity: 'info',
            summary: 'Map restored',
            detail: 'Background opacity restored to full.',
            life: 3000,
          });
        }
      }

      const defaultColor = TRACK_COLOR;
      let lineColor;

      if (hasPalette) {
        const matchExpr = ['match', ['get', 'filterGroup']];
        const colorMap = new Map();
        for (const feature of this.geojson.features) {
          const group = feature.properties.filterGroup;
          if (group && !colorMap.has(group)) {
            colorMap.set(group, palette.getColorForGroup(group, true));
          }
        }
        // Assign palette AFTER it is fully populated so any reactive
        // consumers (e.g. the legend) read the complete color map.
        this.colorPalette = palette;
        for (const [group, color] of colorMap) {
          matchExpr.push(group, color);
        }
        matchExpr.push(defaultColor);
        lineColor = colorMap.size > 0 ? matchExpr : defaultColor;

        // Build legend entries: count tracks per group
        const groupCounts = new Map();
        for (const feature of this.geojson.features) {
          const g = feature.properties.filterGroup;
          if (g) groupCounts.set(g, (groupCounts.get(g) || 0) + 1);
        }
        this.legendEntries = Array.from(colorMap.entries()).map(([group, color]) => ({
          group, color, count: groupCounts.get(group) ?? 0
        }));
      } else {
        lineColor = defaultColor;
        this.legendEntries = [];
      }

      this.visibleTrackCount = this.geojson.features.length;

      // Remove old layers and source on the overlay map
      for (const layerId of ['track-points-layer', 'tracks-highlight-circle-layer', 'tracks-dot-layer', 'tracks-overview-dots', 'tracks-highlight-dash-layer', 'tracks-highlight-layer', 'tracks-layer']) {
        if (this.overlayMap.getLayer(layerId)) this.overlayMap.removeLayer(layerId);
      }
      for (const sourceId of ['track-points', 'tracks-overview', 'tracks']) {
        if (this.overlayMap.getSource(sourceId)) this.overlayMap.removeSource(sourceId);
      }

      // Add GeoJSON source on overlay map
      this.overlayMap.addSource('tracks', {
        type: 'geojson',
        data: this.geojson,
      });

      // Main tracks layer
      this.overlayMap.addLayer({
        id: 'tracks-layer',
        type: 'line',
        source: 'tracks',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: {
          'line-color': lineColor,
          'line-width': ['interpolate', ['linear'], ['zoom'], 0, 5, 7, 4, 14, 3.5, 22, 2],
          'line-opacity': 1,
        },
      });

      // Apply all layer opacities
      this.applyAllLayerOpacities();

      // Highlight layer (solid amber, only shows selected track)
      this.overlayMap.addLayer({
        id: 'tracks-highlight-layer',
        type: 'line',
        source: 'tracks',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': TRACK_SELECTED_COLOR, 'line-width': 6, 'line-opacity': 1 },
        filter: ['==', ['get', 'id'], -1],
      });

      // Highlight dash layer (white dashed overlay on selected)
      this.overlayMap.addLayer({
        id: 'tracks-highlight-dash-layer',
        type: 'line',
        source: 'tracks',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': TRACK_COLOR, 'line-width': 6, 'line-opacity': 1, 'line-dasharray': [2, 3] },
        filter: ['==', ['get', 'id'], -1],
      });

      // Circle layer for degenerate tracks (trackLengthInMeter < 50 m) stored as Point features
      this.overlayMap.addLayer({
        id: 'tracks-dot-layer',
        type: 'circle',
        source: 'tracks',
        filter: ['==', ['geometry-type'], 'Point'],
        paint: { 'circle-color': lineColor, 'circle-radius': 6, 'circle-opacity': 1, 'circle-stroke-color': this.mapThemeSelected === 'dark' ? '#ffffff' : '#1a1a1a', 'circle-stroke-width': 1.5 },
      });

      // Highlight circle for a selected degenerate track
      this.overlayMap.addLayer({
        id: 'tracks-highlight-circle-layer',
        type: 'circle',
        source: 'tracks',
        filter: ['all', ['==', ['geometry-type'], 'Point'], ['==', ['get', 'id'], -1]],
        paint: { 'circle-color': '#FFB300', 'circle-radius': 9, 'circle-opacity': 1 },
      });

      // ── Overview dots: one circle per track centre, visible at low zoom ──────
      // At world view, short/medium tracks are sub-pixel as lines. This dedicated
      // Point source shows a dot so every track is visible at any zoom level.
      // Long tracks remain visible as lines; the dot sits beneath them harmlessly.
      const overviewFeatures = this.geojson.features
        .filter(f => {
          if (!f.geometry) return false;
          if (f.geometry.type === 'LineString') return f.geometry.coordinates.length > 0;
          return true; // Point (degenerate track)
        })
        .map(f => {
          const startCoord = f.geometry.type === 'LineString'
            ? f.geometry.coordinates[0]
            : f.geometry.coordinates;
          return {
            type: 'Feature',
            geometry: { type: 'Point', coordinates: startCoord },
            properties: { id: f.properties.id, filterGroup: f.properties.filterGroup },
          };
        });
      this.overlayMap.addSource('tracks-overview', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: overviewFeatures },
      });
      this.overlayMap.addLayer({
        id: 'tracks-overview-dots',
        type: 'circle',
        source: 'tracks-overview',
        maxzoom: 10,
        paint: { 'circle-color': lineColor, 'circle-radius': 5, 'circle-opacity': 0.85, 'circle-stroke-color': this.mapThemeSelected === 'dark' ? '#ffffff' : '#1a1a1a', 'circle-stroke-width': 1 },
      });

      // ── Individual GPS track points with direction arrows (visible at zoom 18+) ──
      if (!this.overlayMap.hasImage('track-point-arrow')) {
        const arrow = createArrowImage(24, '#2563eb', '#ffffff');
        this.overlayMap.addImage('track-point-arrow', arrow, { pixelRatio: window.devicePixelRatio || 1 });
      }
      this.overlayMap.addSource('track-points', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
      });
      this.overlayMap.addLayer({
        id: 'track-points-layer',
        type: 'symbol',
        source: 'track-points',
        minzoom: TRACK_POINTS_MIN_ZOOM,
        layout: {
          'icon-image': 'track-point-arrow',
          'icon-size': 1,
          'icon-rotate': ['get', 'bearing'],
          'icon-rotation-alignment': 'map',
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
        paint: {
          'icon-opacity': 0.9,
        },
      });

      // Click handler for individual track points
      this.overlayMap.on('click', 'track-points-layer', (e) => {
        if (!e.features || e.features.length === 0) return;
        e.originalEvent.stopPropagation();
        const f = e.features[0];
        const trackId = f.properties.trackId;
        const pointIndex = f.properties.pointIndex;
        this.showTrackPointPopup(e.lngLat, trackId, pointIndex);
      });

      // Change cursor on hover over track points
      this.overlayMap.on('mouseenter', 'track-points-layer', () => {
        this.overlayMap.getCanvas().style.cursor = 'pointer';
      });
      this.overlayMap.on('mouseleave', 'track-points-layer', () => {
        this.overlayMap.getCanvas().style.cursor = '';
      });

      // Re-apply heatmap if it was active (persists across theme changes and reloads)
      if (this.heatmapVisible && this.heatmapOverlay && this.geojson) {
        this.heatmapOverlay.show(this.geojson);
      }

      console.log("add tracks to map: DONE");
      this.updateTrackLineWidth();
      this.applyTracksVisibility();
      this.scheduleDetailCheck();
    },

    // ─── Individual GPS track points ─────────────────────────────────

    /** Rebuild the track-points GeoJSON source from visible track coordinates. */
    updateTrackPointsSource() {
      // Extract reactive refs to local vars once to avoid repeated Vue proxy access
      const overlayMap = this.overlayMap;
      const geojson = this.geojson;
      if (!overlayMap || !geojson) return;
      const zoom = overlayMap.getZoom();

      // Clear if disabled or below minimum zoom
      if (!this.trackPointsVisible || zoom < TRACK_POINTS_MIN_ZOOM) {
        const src = overlayMap.getSource('track-points');
        if (src) src.setData({ type: 'FeatureCollection', features: [] });
        return;
      }

      const trackPrecisions = this.trackPrecisions;
      const gpsTracksById = this.gpsTracksById;

      // Build viewport bounds for filtering
      const mapBounds = overlayMap.getBounds();
      const sw = mapBounds.getSouthWest();
      const ne = mapBounds.getNorthEast();
      const latPad = (ne.lat - sw.lat) * 0.1;
      const lngPad = (ne.lng - sw.lng) * 0.1;
      const minLat = sw.lat - latPad, maxLat = ne.lat + latPad;
      const minLng = sw.lng - lngPad, maxLng = ne.lng + lngPad;

      const features = geojson.features;
      const pointFeatures = [];
      for (let f = 0; f < features.length; f++) {
        const feature = features[f];
        if (feature.geometry.type !== 'LineString') continue;
        const trackId = feature.properties.id;

        // Include tracks at 10m or better precision (arrows are useful at any detail level)
        const precision = trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
        if (precision > 10) continue;

        // Track-level bbox check: skip tracks entirely outside the viewport
        const track = gpsTracksById.get(trackId);
        if (track?.bboxMinLat != null) {
          if (track.bboxMaxLat < minLat || track.bboxMinLat > maxLat ||
              track.bboxMaxLng < minLng || track.bboxMinLng > maxLng) continue;
        }

        const coords = feature.geometry.coordinates;
        for (let i = 0; i < coords.length; i++) {
          const [lng, lat] = coords[i];
          // Viewport filter
          if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) continue;

          // Compute bearing from this point toward the next (last point uses incoming bearing)
          let deg = 0;
          if (i < coords.length - 1) {
            deg = bearing(lng, lat, coords[i + 1][0], coords[i + 1][1]);
          } else if (i > 0) {
            deg = bearing(coords[i - 1][0], coords[i - 1][1], lng, lat);
          }

          pointFeatures.push({
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [lng, lat] },
            properties: { trackId, pointIndex: i, bearing: Math.round(deg) },
          });
        }
      }

      const src = overlayMap.getSource('track-points');
      if (src) src.setData({ type: 'FeatureCollection', features: pointFeatures });
    },

    /** Show a popup with full data for a specific track point. */
    async showTrackPointPopup(lngLat, trackId, pointIndex) {
      // Close any existing popup
      if (this.trackPointsPopup) { this.trackPointsPopup.remove(); this.trackPointsPopup = null; }

      // Fetch or use cached details
      let details = this.trackPointsDetailsCache.get(trackId);
      if (!details) {
        try {
          details = await fetchTrackDetails(trackId);
          this.trackPointsDetailsCache.set(trackId, details);
        } catch (e) {
          console.warn('Failed to fetch point details for track', trackId, e);
          return;
        }
      }

      // Find the matching point by index
      const point = details.find(p => p.pointIndex === pointIndex);
      if (!point) {
        console.warn(`Point index ${pointIndex} not found in details for track ${trackId}`);
        return;
      }

      // Build popup HTML
      const fmt = (v, decimals = 1) => v != null ? Number(v).toFixed(decimals) : '—';
      const fmtTime = (v) => {
        if (!v) return '—';
        const d = new Date(v);
        return formatDateAndTimeWithSeconds(d);
      };
      const fmtDuration = (secs) => {
        if (secs == null) return '—';
        const h = Math.floor(secs / 3600);
        const m = Math.floor((secs % 3600) / 60);
        const s = Math.round(secs % 60);
        return h > 0 ? `${h}h ${m}m ${s}s` : m > 0 ? `${m}m ${s}s` : `${s}s`;
      };

      // Coordinates from the point geometry
      const lat = point.pointLongLat?.coordinates?.[1] ?? lngLat.lat;
      const lng = point.pointLongLat?.coordinates?.[0] ?? lngLat.lng;

      const rows = [
        ['Point', `${point.pointIndex + 1} / ${(point.pointIndexMax ?? 0) + 1}`],
        ['Time', fmtTime(point.pointTimestamp)],
        ['Lat / Lng', `${fmt(lat, 6)} / ${fmt(lng, 6)}`],
        ['Altitude', `${fmt(point.pointAltitude, 1)} m`],
        ['Speed', `${fmt(point.speedInKmhMovingWindow, 1)} km/h`],
        ['Dist from start', `${fmt((point.distanceInMeterSinceStart ?? 0) / 1000, 2)} km`],
        ['Dist prev point', `${fmt(point.distanceInMeterBetweenPoints, 1)} m`],
        ['Time prev point', `${fmt(point.durationBetweenPointsInSec, 1)} s`],
        ['Duration', fmtDuration(point.durationSinceStart)],
        ['Ascent', `${fmt(point.ascentInMeterSinceStart, 0)} m`],
        ['Descent', `${fmt(point.descentInMeterSinceStart, 0)} m`],
        ['Slope', `${fmt(point.slopePercentageInMovingWindow, 1)} %`],
        ['Elev gain/h', `${fmt(point.elevationGainPerHourMovingWindow, 0)} m/h`],
        ['Elev loss/h', `${fmt(point.elevationLossPerHourMovingWindow, 0)} m/h`],
      ];

      // Add energy fields if available
      if (point.energyTotalWh != null) {
        rows.push(['Energy (seg)', `${fmt(point.energyTotalWh, 2)} Wh`]);
        rows.push(['Energy (cum)', `${fmt(point.energyCumulativeWh, 1)} Wh`]);
        rows.push(['Power', `${fmt(point.powerWatts, 0)} W`]);
      }

      const html = `
        <div class="mtl-point-popup">
          <div class="mtl-point-popup-header">Track #${trackId}</div>
          <table class="mtl-point-popup-table">
            ${rows.map(([label, val]) => `<tr><td class="mtl-pp-label">${label}</td><td class="mtl-pp-value">${val}</td></tr>`).join('')}
          </table>
        </div>`;

      this.trackPointsPopup = new maplibregl.Popup({ closeButton: true, maxWidth: '280px', className: 'mtl-point-popup-container' })
        .setLngLat(lngLat)
        .setHTML(html)
        .addTo(this.overlayMap);
    },

    // --- Track highlight helpers ---

    selectTrackById(trackId) {
      const feature = this.gpsTrackIdToFeature.get(trackId);
      if (!feature) return;
      this.selectTrack(trackId, feature);
    },

    selectTrack(trackId, feature) {
      if (this.selectedTrackId === trackId) {
        this.deselectTrack();
        return;
      }
      this.deselectTrack();
      this.selectedTrackId = trackId;
      this.selectedFeature = feature;

      if (this.overlayMap?.getLayer('tracks-highlight-layer')) {
        this.overlayMap.setFilter('tracks-highlight-layer', ['==', ['get', 'id'], trackId]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-dash-layer')) {
        this.overlayMap.setFilter('tracks-highlight-dash-layer', ['==', ['get', 'id'], trackId]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-circle-layer')) {
        this.overlayMap.setFilter('tracks-highlight-circle-layer',
          ['all', ['==', ['geometry-type'], 'Point'], ['==', ['get', 'id'], trackId]]);
      }
    },

    deselectTrack() {
      this.selectedTrackId = null;
      this.selectedFeature = null;

      if (this.overlayMap?.getLayer('tracks-highlight-layer')) {
        this.overlayMap.setFilter('tracks-highlight-layer', ['==', ['get', 'id'], -1]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-dash-layer')) {
        this.overlayMap.setFilter('tracks-highlight-dash-layer', ['==', ['get', 'id'], -1]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-circle-layer')) {
        this.overlayMap.setFilter('tracks-highlight-circle-layer',
          ['all', ['==', ['geometry-type'], 'Point'], ['==', ['get', 'id'], -1]]);
      }
    },

    getTrackPopupMeta(id) {
      const track = this.gpsTracksById.get(id);
      const rawName = track?.trackName?.trim();
      const rawDescription = track?.trackDescription?.trim();
      const name = rawName || rawDescription || `Track ${id}`;
      const date = track?.startDate ? formatDate(new Date(track.startDate)) : '';
      const description = rawName && rawDescription && rawDescription !== rawName ? rawDescription : '';
      return {
        id,
        displayName: name,
        description,
        date: date || 'No date',
        activityType: track?.activityType || '',
      };
    },

    closeSwissMobilityPopup() {
      this.swissMobilityPopup = { visible: false, pos: { x: 0, y: 0 }, routes: [] };
    },

    async identifySwissMobilityRoutes(lngLat, point) {
      const OVERLAY_LAYER_MAP = {
        'wanderland':       { bodId: 'ch.astra.wanderland',       type: 'Hiking',       icon: 'bi bi-signpost-2' },
        'veloland':         { bodId: 'ch.astra.veloland',         type: 'Bike',         icon: 'bi bi-bicycle' },
        'mountainbikeland': { bodId: 'ch.astra.mountainbikeland', type: 'Mountainbike', icon: 'bi bi-bicycle' },
        'wanderwege':       { bodId: 'ch.swisstopo.swisstlm3d-wanderwege', type: 'Trail', icon: 'bi bi-signpost' },
      };
      const activeLayers = this.activeOverlays
        .filter(id => OVERLAY_LAYER_MAP[id])
        .map(id => OVERLAY_LAYER_MAP[id]);
      if (!activeLayers.length) return;

      const layerParam = activeLayers.map(l => l.bodId).join(',');
      const bounds = this.overlayMap.getBounds();
      const canvas = this.overlayMap.getCanvas();
      const mapExtent = `${bounds.getWest()},${bounds.getSouth()},${bounds.getEast()},${bounds.getNorth()}`;
      const imageDisplay = `${canvas.width},${canvas.height},96`;
      const url = `https://api3.geo.admin.ch/rest/services/api/MapServer/identify` +
        `?geometry=${lngLat.lng},${lngLat.lat}` +
        `&geometryType=esriGeometryPoint` +
        `&layers=all:${layerParam}` +
        `&mapExtent=${mapExtent}` +
        `&imageDisplay=${imageDisplay}` +
        `&tolerance=10&lang=en&returnGeometry=false&sr=4326`;

      try {
        const response = await fetch(url);
        if (!response.ok) return;
        const data = await response.json();
        if (!data.results || data.results.length === 0) return;
        const routes = data.results.map(r => {
          const layerInfo = activeLayers.find(l => l.bodId === r.layerBodId) || {};
          return {
            type: layerInfo.type || r.layerName,
            icon: layerInfo.icon || 'bi bi-map',
            name: r.attributes.chmobil_title || r.attributes.label || '—',
            number: r.attributes.chmobil_route_number,
          };
        });
        this.swissMobilityPopup = { visible: true, pos: { x: point.x + 12, y: point.y - 10 }, routes };
      } catch {
        // silently ignore — best-effort enrichment
      }
    },

    showTrackSelectionPopup(point, trackIds) {
      this.closeSelectionPopup();
      this.selectionPopupTrackIds = trackIds;
      this.trackSelectionSheetVisible = true;
    },

    closeSelectionPopup() {
      this.trackSelectionSheetVisible = false;
      this.selectionPopupTrackIds = [];
    },

    navigateMediaTo(id) {
      if (id != null) this.mediaSheetMediaId = id;
    },

    closeMediaSheet() {
      this.mediaSheetMediaId = null;
      this.mediaNavList = [];
    },

    _buildMediaNavList(originId) {
      if (!this.mediaLoadedPoints.length) { this.mediaNavList = []; return; }
      // Limit navigation to photos within the current visible viewport
      const viewBounds = this.overlayMap?.getBounds();
      const visiblePoints = viewBounds
        ? this.mediaLoadedPoints.filter(p =>
            p.lat >= viewBounds.getSouth() && p.lat <= viewBounds.getNorth() &&
            p.lng >= viewBounds.getWest()  && p.lng <= viewBounds.getEast())
        : [...this.mediaLoadedPoints];
      const origin = visiblePoints.find(p => p.id === originId) ?? visiblePoints[0];
      if (!origin) { this.mediaNavList = visiblePoints; return; }
      const dist = (p) => {
        const dlat = p.lat - origin.lat, dlng = p.lng - origin.lng;
        return dlat * dlat + dlng * dlng;
      };
      this.mediaNavList = visiblePoints.sort((a, b) => dist(a) - dist(b));
    },

    onPopupTrackSelect(id) {
      this.closeSelectionPopup();
      this.selectTrackById(id);
      this.openTrackDetails(id, TRACK_DETAILS_MAP_DETENT);
    },

    openTrackDetails(trackId = this.selectedTrackId, initialDetent = TRACK_DETAILS_DEFAULT_DETENT) {
      if (trackId == null) return;

      const feature = this.gpsTrackIdToFeature.get(trackId) || this.selectedFeature;
      const p = feature?.properties;
      this.trackDetailsId = trackId;
      this.trackDetailsInfo = {
        id: trackId,
        name: p?.trackName || p?.trackDescription || '',
        description: '',
      };
      this.trackDetailsInitialDetent = initialDetent;
      this.trackDetailsVisible = true;
    },

    onTrackDetailsSheetClosed() {
      this.deselectTrack();
    },

    onTrackDetailsLoaded({ id, name, description }) {
      this.trackDetailsInfo = { id, name, description };
    },

    onTrackBrowserSelect(trackId) {
      this.selectTrackById(trackId);

      // Fit to track bounds
      const feature = this.gpsTrackIdToFeature.get(trackId);
      if (feature?.geometry?.coordinates?.length > 0) {
        const coords = feature.geometry.coordinates;
        const bounds = coords.reduce((b, c) => b.extend(c), new maplibregl.LngLatBounds(coords[0], coords[0]));
        this.overlayMap.fitBounds(bounds, { padding: 32, maxZoom: Math.max(this.overlayMap.getZoom(), 15) });
      }
    },

    onTrackBrowserOpenDetails(trackId) {
      this.selectTrackById(trackId);
      this.openTrackDetails(trackId, TRACK_DETAILS_EXPANDED_DETENT);
    },

    onToggleTrackPoints() {
      this.trackPointsVisible = !this.trackPointsVisible;
      localStorage.setItem('mtl-trackPointsVisible', String(this.trackPointsVisible));
      this.updateTrackPointsSource();
    },

    onLegendCollapsed(val) {
      this.legendCollapsed = val;
      localStorage.setItem('mtl-legendCollapsed', String(val));
    },

    onHiddenGroupsChanged(groups) {
      this.hiddenGroups = groups;
      this.applyGroupFilter();
    },

    /** Apply a Mapbox filter to hide deactivated legend groups on all track layers. */
    applyGroupFilter() {
      if (!this.overlayMap) return;
      const hidden = this.hiddenGroups;
      let filter = null;
      if (hidden.size > 0) {
        // Exclude features whose filterGroup is in the hidden set
        filter = ['!', ['in', ['get', 'filterGroup'], ['literal', [...hidden]]]];
      }
      for (const layerId of ['tracks-layer', 'tracks-dot-layer', 'tracks-overview-dots']) {
        if (this.overlayMap.getLayer(layerId)) {
          if (layerId === 'tracks-dot-layer') {
            // Dot layer already has a geometry-type filter; combine with group filter
            const baseFilter = ['==', ['geometry-type'], 'Point'];
            this.overlayMap.setFilter(layerId, filter ? ['all', baseFilter, filter] : baseFilter);
          } else {
            this.overlayMap.setFilter(layerId, filter);
          }
        }
      }
      // Update visible track count to reflect hidden groups
      if (this.geojson) {
        const total = this.geojson.features.length;
        if (hidden.size > 0) {
          this.visibleTrackCount = this.geojson.features.filter(
            f => !hidden.has(f.properties.filterGroup)
          ).length;
        } else {
          this.visibleTrackCount = total;
        }
      }
    },

    // ── Geo drawing ──
    onStartGeoDrawing(paramDef) {
      if (!this.overlayMap) return;
      // Create overlay if needed
      if (!this.geoDrawingOverlay) {
        this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
      }
      this.geoDrawingParamDef = paramDef;
      this.geoDrawPointCount = 0;

      // Render any existing shapes from the filter so the user sees them while drawing
      this.renderExistingGeoShapes();

      // Close the filter sheet so the user can interact with the map
      if (this.$refs.filterTool?.close) this.$refs.filterTool.close();

      // Map from param type to drawing mode
      const modeMap = {
        'GEO_CIRCLE': 'circle',
        'GEO_RECTANGLE': 'rectangle',
        'GEO_POLYGON': 'polygon',
      };
      const mode = modeMap[paramDef.type ?? ''];
      if (!mode) return;

      const onDrawingComplete = (shape) => {
        // Forward to FilterTool → CustomFilter (store first so renderExisting picks it up)
        const filterTool = this.$refs.filterTool;
        if (filterTool) filterTool.onGeoDrawingComplete(paramDef, shape);
        this.geoDrawingParamDef = null;
        this.geoDrawPointCount = 0;
        // Re-render all shapes (including the newly drawn one)
        this.renderExistingGeoShapes();
        // Reopen filter sheet to show the result
        if (filterTool?.toggle) filterTool.toggle();
      };

      const onStateChange = () => {
        this.geoDrawPointCount = this.geoDrawingOverlay?.getPointCount() ?? 0;
      };

      this.geoDrawingOverlay.startDrawing(mode, onDrawingComplete, onStateChange);
    },

    onGeoDrawUndo() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.undoLastPoint();
      }
    },

    onGeoDrawFinish() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.finishPolygon();
      }
    },

    onGeoDrawCancel() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.cancelDrawing();
      }
      this.geoDrawingParamDef = null;
      this.geoDrawPointCount = 0;
    },

    onClearGeoShape(paramDef) {
      // Clear drawn shapes from the map overlay
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.clearAll();
      }
    },

    /** Render all existing geo shapes from the filter so they're visible on the map. */
    renderExistingGeoShapes() {
      if (!this.geoDrawingOverlay) return;
      this.geoDrawingOverlay.clearAll();
      const filterTool = this.$refs.filterTool;
      if (!filterTool?.getGeoShapes) return;
      const shapes = filterTool.getGeoShapes();
      const labels = shapes.labels ?? {};
      for (const [key, circle] of Object.entries(shapes.circles)) {
        if (circle) this.geoDrawingOverlay.renderCircle(circle, undefined, labels[key]);
      }
      for (const [key, rect] of Object.entries(shapes.rectangles)) {
        if (rect) this.geoDrawingOverlay.renderRectangle(rect, undefined, labels[key]);
      }
      for (const [key, polygon] of Object.entries(shapes.polygons)) {
        if (polygon) this.geoDrawingOverlay.renderPolygon(polygon, undefined, labels[key]);
      }
    },

    async onFilterApplied() {
      if (!this.initialLoadDone) {
        console.log('[Map] Suppressing filter event during initial load — fetchAllTracks already applies the active filter');
        return;
      }
      console.log("map got filter applied — using IDs-only fast path");
      this.showLoader = true;

      try {
        // Fast path: fetch only matching IDs from server, resolve data from local cache
        const result = await trackStore.applyFilter();

        // Update in-memory state
        this.geojson = markRaw(result.geojson);
        this.gpsTracksById = markRaw(result.gpsTracksById);
        this.gpsTrackIdToFeature = markRaw(result.gpsTrackIdToFeature);
        if (result.trackPrecisions) {
          this.trackPrecisions = markRaw(result.trackPrecisions);
        }
        this.totalTrackCount = result.standardFilterCount;
        this.visibleTrackCount = result.gpsTracksById.size;

        // Clear selection state
        this.selectedTrackId = null;
        this.selectedFeature = null;
        this.closeSelectionPopup();

        // Re-render tracks on the map without destroying/recreating the map
        await this.addTracksToMap();
        this.updateTracksSource();
      } catch (e) {
        // Fallback to full reload if IDs-only path fails (e.g. cache miss)
        console.warn('IDs-only filter failed, falling back to full reload:', e);
        await this.reloadMap(false);
      } finally {
        this.showLoader = false;
      }

      if (this.$refs.statistics?.active) this.$refs.statistics.fetchStatistics();
      // Re-render geo shapes after map reload only if filter panel is still open
      if (this.activeToolId === 'filter') {
        this.$nextTick(() => {
          if (this.overlayMap) {
            if (!this.geoDrawingOverlay) {
              this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
            }
            this.renderExistingGeoShapes();
          }
        });
      }
    }
  },
};
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
  background-color: #f0f0f0;
  align-items: stretch;
  position: relative;
  z-index: 0;
}

/* Desktop: offset the whole map container for the nav panel */
@media (min-width: 1024px) {
  .container {
    margin-left: var(--nav-panel-w);
    width: calc(100% - var(--nav-panel-w));
    transition: margin-left 0.3s ease, width 0.3s ease;
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
  color: #333;
  font-size: 1rem;
  transition: color 0.15s, background 0.15s;
}
.map-overlay :deep(.mtl-globe-btn:hover) {
  background: rgba(0, 0, 0, 0.05);
  color: #111;
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
  height: 4px;
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
    rgba(99, 102, 241, 0.55) 15%,
    var(--accent, #6366f1) 40%,
    var(--accent-text-light, #818cf8) 60%,
    rgba(99, 102, 241, 0.55) 85%,
    transparent 100%
  );
  animation: progress-shimmer 1.4s ease-in-out infinite;
}
@keyframes progress-shimmer {
  0%   { left: -60%; }
  100% { left: 120%; }
}
.bar-fade-enter-active, .bar-fade-leave-active {
  transition: opacity 0.3s ease;
}
.bar-fade-enter-from, .bar-fade-leave-to {
  opacity: 0;
}

/* ─── Transitions ─── */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.4s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active {
  transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.25s ease;
}
.slide-up-leave-active {
  transition: transform 0.25s ease, opacity 0.2s ease;
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
  font-size: 0.9rem;
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
  font-size: 0.78rem;
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
  font-size: 0.78rem;
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
  background: var(--accent, #3b82f6);
  transition: width 0.8s ease;
}
.mtl-map-downloading-pct {
  font-size: 0.72rem;
  color: var(--text-muted);
  flex-shrink: 0;
}
.mtl-map-downloading-msg {
  font-size: 0.7rem;
  font-weight: 400;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #5f6368;
  white-space: nowrap;
  flex-shrink: 0;
}

.td-sheet-id {
  flex-shrink: 0;
  font-size: 0.7rem;
  color: var(--text-muted);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 4px;
  padding: 1px 6px;
  font-family: monospace;
  white-space: nowrap;
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
  transition: transform 0.15s, background 0.12s, border-color 0.12s, color 0.12s;
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
  font-size: 0.86rem;
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
  font-size: 0.74rem;
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
  font-size: 0.9rem;
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
  font-size: 1.2em;
  color: var(--text-muted);
  z-index: 1;
  transition: color 0.15s;
}
.swiss-mobility-popup-close:hover {
  color: var(--text-primary);
}
.swiss-mobility-popup-header {
  font-size: 0.7rem;
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
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--text-faint);
}
.swiss-mobility-route-name {
  font-size: 0.82rem;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.swiss-mobility-route-number {
  flex-shrink: 0;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--text-faint);
  margin-left: auto;
  padding-left: 0.4rem;
}
</style>

<!-- Track point popup styles (unscoped — MapLibre popups live outside component root) -->
<style>
.mtl-point-popup-container .maplibregl-popup-content {
  padding: 0;
  border-radius: 0.6rem;
  background: var(--surface-glass-heavy, rgba(30, 30, 40, 0.92));
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid var(--border-medium, rgba(255, 255, 255, 0.12));
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}
.mtl-point-popup-container .maplibregl-popup-close-button {
  font-size: 1rem;
  color: var(--text-muted, #aaa);
  padding: 4px 8px;
  line-height: 1;
}
.mtl-point-popup-container .maplibregl-popup-close-button:hover {
  color: var(--text-primary, #fff);
  background: transparent;
}
.mtl-point-popup-container .maplibregl-popup-tip {
  border-top-color: var(--surface-glass-heavy, rgba(30, 30, 40, 0.92));
}
.mtl-point-popup {
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 0.72rem;
  line-height: 1.35;
  color: var(--text-secondary, #ccc);
}
.mtl-point-popup-header {
  padding: 0.35rem 0.6rem;
  font-weight: 700;
  font-size: 0.75rem;
  color: var(--text-primary, #fff);
  background: var(--surface-elevated, rgba(255, 255, 255, 0.06));
  border-bottom: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.08));
}
.mtl-point-popup-table {
  width: 100%;
  border-collapse: collapse;
}
.mtl-point-popup-table tr:not(:last-child) {
  border-bottom: 1px solid var(--border-subtle, rgba(255, 255, 255, 0.05));
}
.mtl-point-popup-table td {
  padding: 0.2rem 0.6rem;
  vertical-align: top;
}
.mtl-pp-label {
  color: var(--text-muted, #888);
  white-space: nowrap;
  padding-right: 0.8rem;
  font-size: 0.68rem;
}
.mtl-pp-value {
  color: var(--text-primary, #eee);
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
  background: var(--surface-glass, rgba(255, 255, 255, 0.88));
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid var(--border-medium, rgba(0, 0, 0, 0.1));
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
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}
.geo-draw-toolbar__header i {
  font-size: 1rem;
  color: #6366f1;
}
.geo-draw-toolbar__hint {
  font-size: 0.78rem;
  color: var(--text-muted, #64748b);
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
  border: 1px solid var(--border-medium, rgba(0, 0, 0, 0.1));
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary, #1e293b);
  font-size: 0.8rem;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}
.geo-draw-toolbar__btn:hover:not(:disabled) {
  background: var(--surface-hover, rgba(0, 0, 0, 0.05));
}
.geo-draw-toolbar__btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.geo-draw-toolbar__btn--finish {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}
.geo-draw-toolbar__btn--finish:hover:not(:disabled) {
  background: #818cf8;
}
.geo-draw-toolbar__btn--cancel {
  color: #f87171;
  border-color: rgba(248, 113, 113, 0.3);
}
.geo-draw-toolbar__btn--cancel:hover {
  background: rgba(248, 113, 113, 0.12);
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
