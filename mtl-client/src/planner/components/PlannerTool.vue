<template>
  <BottomSheet
    v-model="active"
    icon="bi bi-signpost-split"
    :detents="[{ height: '40vh' }, { height: '70vh' }, { height: '95vh' }]"
    :no-backdrop="true"
    @closed="onSheetClosed"
  >
    <template #title>
      <div class="planner-header-nav">
        <i class="bi bi-signpost-split planner-sheet-icon"></i>
        <div class="planner-header-tabs">
          <button class="planner-header-tab" :class="{ 'planner-header-tab--active': activeTab === 'draw' }" @pointerdown.stop @click="activeTab = 'draw'">Drawing</button>
          <button class="planner-header-tab" :class="{ 'planner-header-tab--active': activeTab === 'load' }" @pointerdown.stop @click="switchToLoad">Load</button>
        </div>
      </div>
    </template>
    <div class="planner-root">
      <!-- ── Drawing tab ─────────────────────────────────────────── -->
      <div v-if="activeTab === 'draw'" class="planner-panel planner-panel--draw">
        <p class="planner-subtitle">Tap the map to add waypoints. Drag to adjust.</p>

        <div class="planner-controls-row">
          <PlannerToolbar
            :profiles="profiles"
            :profile="planner.profile.value"
            @profile-changed="planner.setProfile"
          />

          <div class="planner-actions" role="group" aria-label="Edit history">
            <button type="button" class="planner-action-btn" :disabled="!planner.canUndo.value" title="Undo" aria-label="Undo" @click="planner.undo">
              <i class="bi bi-arrow-counterclockwise" />
            </button>
            <button type="button" class="planner-action-btn" :disabled="!planner.canRedo.value" title="Redo" aria-label="Redo" @click="planner.redo">
              <i class="bi bi-arrow-clockwise" />
            </button>
            <button type="button" class="planner-action-btn" :disabled="!planner.waypoints.value.length" title="Clear route" aria-label="Clear route" @click="planner.clearAll">
              <i class="bi bi-trash" />
            </button>
            <button
              type="button"
              class="planner-action-btn"
              :disabled="planner.routeCoordinates.value.length < 2"
              title="Save route"
              aria-label="Save route"
              @click="openSaveDialog"
            >
              <i class="bi bi-floppy" />
            </button>
          </div>

          <div class="planner-controls-right">
            <div v-if="status" class="brouter-pill-wrap">
              <button
                type="button"
                class="brouter-pill"
                :aria-expanded="brouterExpanded"
                aria-label="BRouter status details"
                @click="brouterExpanded = !brouterExpanded"
              >
                <span :class="['brouter-dot', brouterDotClass]"></span>
                <span class="brouter-label">BRouter</span>
                <span v-if="(status.segmentsQueued ?? 0) > 0" class="brouter-badge">{{ status.segmentsQueued }}</span>
                <i :class="brouterExpanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" class="brouter-chevron"></i>
              </button>
              <div v-if="brouterExpanded" class="brouter-detail">
                <div v-if="status.available" class="brouter-detail-body">
                  <div class="brouter-detail-row">
                    <span class="brouter-detail-label">Running</span>
                    <span
                      :class="['brouter-detail-val', status.brouterRunning ? 'brouter-val--ok' : 'brouter-val--warn']"
                      >{{ status.brouterRunning ? 'yes' : 'no' }}</span
                    >
                  </div>
                  <div class="brouter-detail-row">
                    <span class="brouter-detail-label">Segments on disk</span>
                    <span class="brouter-detail-val">{{ status.segmentsOnDisk ?? 0 }}</span>
                  </div>
                  <div class="brouter-detail-row">
                    <span class="brouter-detail-label">Queued</span>
                    <span class="brouter-detail-val">{{ status.segmentsQueued ?? 0 }}</span>
                  </div>
                  <div v-if="(status.segmentsInProgress?.length ?? 0) > 0" class="brouter-detail-row">
                    <span class="brouter-detail-label">In progress</span>
                    <span class="brouter-detail-val">{{ status.segmentsInProgress?.join(', ') }}</span>
                  </div>
                  <div
                    v-if="status.segmentsFailed && Object.keys(status.segmentsFailed).length > 0"
                    class="brouter-detail-row"
                  >
                    <span class="brouter-detail-label">Failed</span>
                    <span class="brouter-detail-val brouter-val--warn">{{
                      Object.keys(status.segmentsFailed).join(', ')
                    }}</span>
                  </div>
                </div>
                <div v-else class="brouter-detail-body">
                  <span class="brouter-val--warn">Unavailable: {{ status.reason ?? 'unknown' }}</span>
                </div>
                <button type="button" class="brouter-detail-refresh" @click.stop="refresh">
                  <i class="bi bi-arrow-clockwise"></i>
                  <span>Refresh</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div
          v-if="planner.viewportTooLarge.value || planner.pristineLoaded.value || planner.lastError.value"
          class="planner-notices"
        >
          <div v-if="planner.viewportTooLarge.value" class="planner-notice planner-notice--warn">
            <i class="bi bi-zoom-in"></i>
            <span>Zoom in to start planning. Visible span must be under ~300 km.</span>
          </div>

          <div v-if="planner.pristineLoaded.value" class="planner-notice planner-notice--info">
            <i class="bi bi-info-circle"></i>
            <span>Loaded plan shown as saved. The first edit will re-route all legs.</span>
          </div>

          <div v-if="planner.lastError.value" class="planner-notice planner-notice--error">
            <i class="bi bi-exclamation-circle"></i>
            <span>{{ planner.lastError.value }}</span>
          </div>
        </div>

        <LiveStatsBar :stats="planner.stats.value" :computing="planner.computing.value" />

        <ElevationProfile
          :coordinates="planner.routeCoordinates.value"
          :total-distance-m="planner.stats.value.distanceM"
          :ascent-m="planner.stats.value.ascentM"
          :descent-m="planner.stats.value.descentM"
          @hover="onElevationHover"
        />
      </div>

      <!-- ── Load tab ──────────────────────────────────────────── -->
      <div v-else-if="activeTab === 'load'" class="planner-panel planner-panel--load">
        <div v-if="plansLoading" class="planner-load-spinner">
          <i class="bi bi-arrow-repeat planner-load-spin"></i> Loading saved routes…
        </div>
        <div v-else-if="savedPlans.length === 0" class="planner-load-empty">
          <i class="bi bi-inbox"></i>
          <span>No saved routes yet. Draw a route and save it.</span>
        </div>
        <ul v-else class="planner-plan-list">
          <li
            v-for="plan in savedPlans"
            :key="plan.id"
            class="planner-plan-item"
            @click="selectPlan(plan.id)"
          >
            <div class="planner-plan-body">
              <span class="planner-plan-name">{{ plan.name }}</span>
              <span class="planner-plan-meta">
                <i class="bi bi-rulers"></i> {{ (plan.distanceM / 1000).toFixed(1) }} km
                <span v-if="plan.description" class="planner-plan-desc">· {{ plan.description }}</span>
              </span>
            </div>
            <div class="planner-plan-date">{{ formatDate(plan.createDate) }}</div>
          </li>
        </ul>
      </div>
    </div>

    <!-- Save dialog -->
    <PrimeDialog
      v-model:visible="saveDialogVisible"
      :modal="true"
      header="Save planned route"
      :style="{ width: 'min(440px, 92vw)' }"
      :draggable="false"
      :dismissable-mask="true"
      class="planner-dialog"
    >
      <div class="planner-dialog-body">
        <label class="planner-field">
          <span>Name</span>
          <InputText
            v-model="saveName"
            autofocus
            placeholder="e.g. Saturday loop via Uetliberg"
            @keyup.enter="confirmSave"
          />
        </label>
        <label class="planner-field">
          <span>Description <em>(optional)</em></span>
          <PrimeTextarea v-model="saveDescription" rows="2" auto-resize placeholder="Notes for future you…" />
        </label>
        <div class="planner-dialog-meta">
          <span><i class="bi bi-rulers"></i> {{ (planner.stats.value.distanceM / 1000).toFixed(2) }} km</span>
          <span><i class="bi bi-arrow-up-right"></i> {{ Math.round(planner.stats.value.ascentM) }} m</span>
          <span><i class="bi bi-arrow-down-right"></i> {{ Math.round(planner.stats.value.descentM) }} m</span>
          <span><i class="bi bi-pin-map"></i> {{ planner.waypoints.value.length }} waypoints</span>
        </div>
      </div>
      <template #footer>
        <PrimeButton
          label="Cancel"
          severity="secondary"
          text
          :disabled="saveSubmitting"
          @click="saveDialogVisible = false"
        />
        <PrimeButton
          :label="saveSubmitting ? 'Saving…' : 'Save plan'"
          icon="pi pi-save"
          :disabled="!saveName.trim() || saveSubmitting"
          @click="confirmSave"
        />
      </template>
    </PrimeDialog>
  </BottomSheet>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue';
import maplibregl from 'maplibre-gl';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import PlannerToolbar from '@/planner/components/PlannerToolbar.vue';
import LiveStatsBar from '@/planner/components/LiveStatsBar.vue';
import ElevationProfile from '@/planner/components/ElevationProfile.vue';
import PrimeDialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import PrimeTextarea from 'primevue/textarea';
import PrimeButton from 'primevue/button';
import { usePlannerState } from '@/planner/composables/usePlannerState';
import { useBRouterSegmentStatus } from '@/planner/composables/useBRouterSegmentStatus';
import { fetchPlannerConfig, savePlannedRoute, listPlannedTracks, loadPlannedTrack } from '@/planner/repositories/plannerRepository';
import type { PlannedTrackSummary } from '@/planner/types';
import {
  LONG_PRESS_MS,
  PLANNER_LAYER_ID,
  PLANNER_SOURCE_ID,
  PLANNER_WAYPOINT_LAYER_ID,
  PLANNER_WAYPOINT_SOURCE_ID,
  ROUTE_LINE_COLOR,
  ROUTE_LINE_WIDTH_PX,
  WAYPOINT_FILL_COLOR,
  WAYPOINT_RADIUS_PX,
  WAYPOINT_STROKE_COLOR,
  WAYPOINT_STROKE_WIDTH_PX,
} from '@/planner/constants/PlannerConstants';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type MapLike = any;

export default defineComponent({
  name: 'PlannerTool',
  components: {
    BottomSheet,
    PlannerToolbar,
    LiveStatsBar,
    ElevationProfile,
    PrimeDialog,
    InputText,
    PrimeTextarea,
    PrimeButton,
  },
  props: {
    map: { type: Object as () => MapLike | undefined, default: undefined },
  },
  emits: ['active-changed', 'tool-opened', 'tool-closed'],
  setup() {
    const planner = usePlannerState();
    // `active` lives here so we can gate BRouter status polling on it —
    // no point hitting /api/planner/status while the planner sheet is closed.
    const active = ref(false);
    const { status, refresh } = useBRouterSegmentStatus(active);
    return { planner, status, refresh, active };
  },
  data() {
    return {
      profiles: ['trekking'] as string[],
      activeTab: 'draw' as 'draw' | 'load',
      savedPlans: [] as PlannedTrackSummary[],
      plansLoading: false,
      // dialogs
      saveDialogVisible: false,
      saveName: '',
      saveDescription: '',
      saveSubmitting: false,
      // map-integration state (non-reactive references)
      attachedMap: null as MapLike | null,
      clickHandler: null as ((ev: maplibregl.MapMouseEvent) => void) | null,
      moveHandler: null as (() => void) | null,
      touchStartHandler: null as ((ev: maplibregl.MapTouchEvent) => void) | null,
      touchCancelHandler: null as (() => void) | null,
      dragMarkerMap: new Map<string, unknown>() as Map<string, maplibregl.Marker>,
      hoverMarker: null as maplibregl.Marker | null,
      longPressTimer: null as number | null,
      longPressLngLat: null as maplibregl.LngLat | null,
      unwatchRoute: null as null | (() => void),
      unwatchWaypoints: null as null | (() => void),
      configLoaded: false,
      brouterExpanded: false,
    };
  },
  computed: {
    brouterDotClass(): string {
      const s = this.status;
      if (!s || !s.available) return 'brouter-dot--off';
      if (!s.brouterRunning) return 'brouter-dot--warn';
      if ((s.segmentsQueued ?? 0) > 0 || (s.segmentsInProgress?.length ?? 0) > 0) return 'brouter-dot--warn';
      return 'brouter-dot--ok';
    },
  },
  watch: {
    // Map instance may be swapped out on theme reload — re-attach layers if open.
    map(newMap: unknown, oldMap: unknown) {
      const self = this as unknown as {
        active: boolean;
        attachedMap: unknown;
        detachFromMap: () => void;
        attachToMap: () => void;
      };
      if (self.active && oldMap && self.attachedMap === oldMap) {
        self.detachFromMap();
      }
      if (self.active && newMap) {
        self.attachToMap();
      }
    },
  },
  beforeUnmount() {
    this.detachFromMap();
  },
  methods: {
    isOpen(): boolean {
      return this.active;
    },

    async toggle() {
      this.active = !this.active;
      this.$emit('active-changed', this.active);
      if (this.active) {
        this.$emit('tool-opened');
        if (!this.configLoaded) {
          await this.loadConfig();
          this.configLoaded = true;
        }
        this.attachToMap();
      } else {
        this.detachFromMap();
      }
    },

    close() {
      if (!this.active) return;
      this.active = false;
      this.$emit('active-changed', false);
      this.detachFromMap();
    },

    onSheetClosed() {
      this.$emit('active-changed', false);
      this.$emit('tool-closed');
      this.detachFromMap();
    },

    // ── Config / plans loading ─────────────────────────────────────
    async loadConfig() {
      try {
        const cfg = await fetchPlannerConfig();
        this.profiles = cfg.profiles.length ? cfg.profiles : ['trekking'];
        if (!this.profiles.includes(this.planner.profile.value)) {
          this.planner.setProfile(cfg.defaultProfile);
        }
      } catch (e) {
        console.warn('[planner] failed to load config', e);
      }
    },

    async saveCurrent() {
      // Legacy entry point — kept for any external callers; opens the proper dialog.
      this.openSaveDialog();
    },

    openSaveDialog() {
      if (this.planner.routeCoordinates.value.length < 2) return;
      this.saveName = this.saveName || this.suggestedPlanName();
      this.saveDescription = '';
      this.saveDialogVisible = true;
    },

    suggestedPlanName(): string {
      const km = (this.planner.stats.value.distanceM / 1000).toFixed(1);
      const date = new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
      return `Plan — ${km} km (${date})`;
    },

    async confirmSave() {
      const name = this.saveName.trim();
      if (!name || this.planner.routeCoordinates.value.length < 2) return;
      this.saveSubmitting = true;
      try {
        await savePlannedRoute({
          name,
          description: this.saveDescription.trim() || undefined,
          profile: this.planner.profile.value,
          coordinates: this.planner.routeCoordinates.value,
          waypoints: this.planner.waypoints.value.map((w) => ({ lat: w.lat, lng: w.lng })),
        });
        this.saveDialogVisible = false;
        this.saveName = '';
        this.saveDescription = '';
        // Refresh the saved plans list so next Load tab visit is up-to-date
        this.savedPlans = await listPlannedTracks().catch(() => []);
      } finally {
        this.saveSubmitting = false;
      }
    },

    // ── Load plans ───────────────────────────────────────────────────
    async switchToLoad() {
      this.activeTab = 'load';
      this.plansLoading = true;
      try {
        this.savedPlans = await listPlannedTracks();
      } catch {
        this.savedPlans = [];
      } finally {
        this.plansLoading = false;
      }
    },

    async selectPlan(id: number) {
      try {
        const detail = await loadPlannedTrack(id);
        this.planner.loadPlan(detail);
        this.activeTab = 'draw';
      } catch (e) {
        console.warn('[planner] failed to load plan', id, e);
      }
    },

    profileIconFor(profile: string | null): string {
      const map: Record<string, string> = {
        trekking: 'bi bi-signpost-split',
        fastbike: 'bi bi-bicycle',
        'hiking-mountain': 'bi bi-compass',
        'car-eco': 'bi bi-car-front',
      };
      return (profile && map[profile]) || 'bi bi-signpost-split';
    },

    formatDate(iso: string): string {
      if (!iso) return '';
      return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
    },

    // ── Map integration ─────────────────────────────────────────────
    attachToMap() {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const map: MapLike | undefined = (this as any).map;
      if (!map) return;
      if (this.attachedMap === map) return;
      this.attachedMap = map;

      const addLayers = () => {
        if (!map.getSource(PLANNER_SOURCE_ID)) {
          map.addSource(PLANNER_SOURCE_ID, { type: 'geojson', data: this.routeFeature() as never });
          map.addLayer({
            id: PLANNER_LAYER_ID,
            type: 'line',
            source: PLANNER_SOURCE_ID,
            paint: {
              'line-color': ROUTE_LINE_COLOR,
              'line-width': ROUTE_LINE_WIDTH_PX,
              'line-opacity': 0.95,
            },
          });
        }
        if (!map.getSource(PLANNER_WAYPOINT_SOURCE_ID)) {
          map.addSource(PLANNER_WAYPOINT_SOURCE_ID, { type: 'geojson', data: this.waypointFeature() as never });
          map.addLayer({
            id: PLANNER_WAYPOINT_LAYER_ID,
            type: 'circle',
            source: PLANNER_WAYPOINT_SOURCE_ID,
            paint: {
              'circle-radius': WAYPOINT_RADIUS_PX,
              'circle-color': WAYPOINT_FILL_COLOR,
              'circle-stroke-color': WAYPOINT_STROKE_COLOR,
              'circle-stroke-width': WAYPOINT_STROKE_WIDTH_PX,
            },
          });
        }
        this.syncSources();
        this.emitViewport();
      };

      if (map.isStyleLoaded && map.isStyleLoaded()) {
        addLayers();
      } else {
        map.once('load', addLayers);
      }

      // Click (add / insert waypoint)
      this.clickHandler = (ev) => this.handleMapClick(ev);
      map.on('click', this.clickHandler);

      // Viewport updates
      this.moveHandler = () => this.emitViewport();
      map.on('moveend', this.moveHandler);

      // Long-press to add waypoint on touch devices
      this.touchStartHandler = (ev) => {
        if (!ev.lngLat) return;
        this.longPressLngLat = ev.lngLat;
        this.longPressTimer = window.setTimeout(() => {
          if (this.longPressLngLat) {
            this.planner.addWaypoint(this.longPressLngLat.lat, this.longPressLngLat.lng);
          }
          this.longPressTimer = null;
          this.longPressLngLat = null;
        }, LONG_PRESS_MS);
      };
      this.touchCancelHandler = () => {
        if (this.longPressTimer !== null) window.clearTimeout(this.longPressTimer);
        this.longPressTimer = null;
        this.longPressLngLat = null;
      };
      map.on('touchstart', this.touchStartHandler);
      map.on('touchmove', this.touchCancelHandler);
      map.on('touchend', this.touchCancelHandler);
      map.on('touchcancel', this.touchCancelHandler);

      // Watch planner state to re-sync sources & markers
      this.unwatchRoute = this.$watch(
        () => this.planner.routeCoordinates.value,
        () => this.syncSources(),
        { deep: true }
      );
      this.unwatchWaypoints = this.$watch(
        () => this.planner.waypoints.value,
        () => this.syncSources(),
        { deep: true }
      );
    },

    detachFromMap() {
      const map = this.attachedMap;
      if (this.unwatchRoute) {
        this.unwatchRoute();
        this.unwatchRoute = null;
      }
      if (this.unwatchWaypoints) {
        this.unwatchWaypoints();
        this.unwatchWaypoints = null;
      }
      if (this.longPressTimer !== null) {
        window.clearTimeout(this.longPressTimer);
        this.longPressTimer = null;
      }
      this.longPressLngLat = null;

      for (const marker of this.dragMarkerMap.values()) marker.remove();
      this.dragMarkerMap.clear();
      if (this.hoverMarker) {
        this.hoverMarker.remove();
        this.hoverMarker = null;
      }

      if (!map) {
        this.attachedMap = null;
        return;
      }
      if (this.clickHandler) {
        map.off('click', this.clickHandler);
        this.clickHandler = null;
      }
      if (this.moveHandler) {
        map.off('moveend', this.moveHandler);
        this.moveHandler = null;
      }
      if (this.touchStartHandler) {
        map.off('touchstart', this.touchStartHandler);
        this.touchStartHandler = null;
      }
      if (this.touchCancelHandler) {
        map.off('touchmove', this.touchCancelHandler);
        map.off('touchend', this.touchCancelHandler);
        map.off('touchcancel', this.touchCancelHandler);
        this.touchCancelHandler = null;
      }
      try {
        if (map.getLayer(PLANNER_LAYER_ID)) map.removeLayer(PLANNER_LAYER_ID);
        if (map.getSource(PLANNER_SOURCE_ID)) map.removeSource(PLANNER_SOURCE_ID);
        if (map.getLayer(PLANNER_WAYPOINT_LAYER_ID)) map.removeLayer(PLANNER_WAYPOINT_LAYER_ID);
        if (map.getSource(PLANNER_WAYPOINT_SOURCE_ID)) map.removeSource(PLANNER_WAYPOINT_SOURCE_ID);
      } catch {
        // Map may already be torn down on theme reload — safe to ignore
      }
      this.attachedMap = null;
    },

    routeFeature() {
      const coords = this.planner.routeCoordinates.value;
      return {
        type: 'FeatureCollection',
        features:
          coords.length >= 2
            ? [
                {
                  type: 'Feature',
                  properties: {},
                  geometry: {
                    type: 'LineString',
                    coordinates: coords.map((c) => [c[0], c[1]]),
                  },
                },
              ]
            : [],
      };
    },

    waypointFeature() {
      return {
        type: 'FeatureCollection',
        features: this.planner.waypoints.value.map((w, index) => ({
          type: 'Feature',
          properties: { id: w.id, index },
          geometry: { type: 'Point', coordinates: [w.lng, w.lat] },
        })),
      };
    },

    syncSources() {
      const map = this.attachedMap;
      if (!map) return;
      const routeSrc = map.getSource(PLANNER_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
      if (routeSrc) routeSrc.setData(this.routeFeature() as never);
      const wpSrc = map.getSource(PLANNER_WAYPOINT_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
      if (wpSrc) wpSrc.setData(this.waypointFeature() as never);
      this.syncDragMarkers();
    },

    syncDragMarkers() {
      const map = this.attachedMap;
      if (!map) return;
      const nextIds = new Set(this.planner.waypoints.value.map((w) => w.id));
      for (const [id, marker] of this.dragMarkerMap) {
        if (!nextIds.has(id)) {
          marker.remove();
          this.dragMarkerMap.delete(id);
        }
      }
      for (const wp of this.planner.waypoints.value) {
        const existing = this.dragMarkerMap.get(wp.id);
        if (existing) {
          existing.setLngLat([wp.lng, wp.lat]);
          continue;
        }
        const el = document.createElement('div');
        el.className = 'planner-drag-marker';
        const marker = new maplibregl.Marker({ element: el, draggable: true })
          .setLngLat([wp.lng, wp.lat])
          .addTo(map as unknown as maplibregl.Map);
        marker.on('dragend', () => {
          const ll = marker.getLngLat();
          this.planner.moveWaypoint(wp.id, ll.lat, ll.lng);
        });
        // @ts-expect-error TS2589 - maplibre Map/Marker types too deep combined with Options API this-type
        this.dragMarkerMap.set(wp.id, marker as unknown as maplibregl.Marker);
      }
    },

    emitViewport() {
      const map = this.attachedMap;
      if (!map) return;
      const b = map.getBounds();
      const sw = b.getSouthWest();
      const ne = b.getNorthEast();
      this.planner.setViewport(sw.lat, sw.lng, ne.lat, ne.lng);
    },

    handleMapClick(ev: maplibregl.MapMouseEvent) {
      const map = this.attachedMap;
      if (!map) return;
      if (this.planner.viewportTooLarge.value) return;
      const routeHits = map.queryRenderedFeatures(ev.point, { layers: [PLANNER_LAYER_ID] });
      if (routeHits.length > 0 && this.planner.waypoints.value.length >= 2) {
        const idx = this.nearestWaypointSegmentIndex(ev.lngLat.lng, ev.lngLat.lat);
        this.planner.insertWaypoint(idx, ev.lngLat.lat, ev.lngLat.lng);
        return;
      }
      this.planner.addWaypoint(ev.lngLat.lat, ev.lngLat.lng);
    },

    nearestWaypointSegmentIndex(lng: number, lat: number): number {
      let bestIndex = 0;
      let bestScore = Number.POSITIVE_INFINITY;
      const wps = this.planner.waypoints.value;
      for (let index = 0; index < wps.length - 1; index++) {
        const a = wps[index];
        const b = wps[index + 1];
        const score = this.distanceToSegmentSquared(lng, lat, a.lng, a.lat, b.lng, b.lat);
        if (score < bestScore) {
          bestScore = score;
          bestIndex = index;
        }
      }
      return bestIndex;
    },

    distanceToSegmentSquared(px: number, py: number, ax: number, ay: number, bx: number, by: number): number {
      const abx = bx - ax;
      const aby = by - ay;
      const apx = px - ax;
      const apy = py - ay;
      const ab2 = abx * abx + aby * aby;
      const t = ab2 === 0 ? 0 : Math.max(0, Math.min(1, (apx * abx + apy * aby) / ab2));
      const cx = ax + abx * t;
      const cy = ay + aby * t;
      const dx = px - cx;
      const dy = py - cy;
      return dx * dx + dy * dy;
    },

    onElevationHover(point: { lng: number; lat: number; elevationM: number; distanceM: number } | null) {
      const map = this.attachedMap;
      if (!map) return;
      if (!point) {
        if (this.hoverMarker) {
          this.hoverMarker.remove();
          this.hoverMarker = null;
        }
        return;
      }
      if (!this.hoverMarker) {
        const el = document.createElement('div');
        el.className = 'planner-hover-marker';
        this.hoverMarker = new maplibregl.Marker({ element: el, anchor: 'center' })
          .setLngLat([point.lng, point.lat])
          .addTo(map as unknown as maplibregl.Map);
      } else {
        this.hoverMarker.setLngLat([point.lng, point.lat]);
      }
    },
  },
});
</script>

<style scoped>
.planner-hdr-btn {
  position: relative;
  background: var(--surface-hover);
  border: 1px solid var(--border-medium);
  color: var(--text-muted);
  width: var(--bs-btn-size, 2rem);
  height: var(--bs-btn-size, 2rem);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--bs-btn-fs-icon, 0.85rem);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}
.planner-hdr-btn::after {
  content: '';
  position: absolute;
  inset: -0.55rem;
}
.planner-hdr-btn:hover:not(:disabled) {
  color: var(--text-primary);
  background: var(--surface-active);
}
.planner-hdr-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.planner-hdr-btn--danger:hover:not(:disabled) {
  color: var(--warning-text);
  background: var(--warning-bg);
  border-color: color-mix(in srgb, #f97316 30%, var(--border-medium));
}
.planner-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
/* ── Header tab navigation ─────────────────────────────────────── */
.planner-header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}
.planner-sheet-icon {
  font-size: 1rem;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.planner-header-tabs {
  display: flex;
  gap: 0.15rem;
  min-width: 0;
}
.planner-header-tab {
  padding: 0.25rem 0.7rem;
  border-radius: 1rem;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  line-height: var(--text-sm-lh);
}
.planner-header-tab:not(.planner-header-tab--active):hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.planner-header-tab--active {
  background: var(--accent-subtle);
  color: var(--accent-text);
  font-weight: 600;
}
/* ── Panels ────────────────────────────────────────────────────── */
.planner-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  padding: 0.5rem 1rem 1rem;
  gap: 0.65rem;
}
.planner-panel--draw {
  overflow: hidden;
}
.planner-panel--load {
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}
.planner-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
/* ── Controls row ──────────────────────────────────────────────── */
.planner-controls-row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 0.5rem;
}
.planner-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}
.planner-controls-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.4rem;
}
.planner-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  height: 2.4rem;
  border: 1px solid var(--accent);
  border-radius: 8px;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: var(--text-sm-size);
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
  flex-shrink: 0;
}
.planner-action-btn:hover:not(:disabled) {
  background: var(--accent-subtle);
}
.planner-action-btn:disabled {
  opacity: 0.28;
  cursor: not-allowed;
}
.planner-action-btn--danger {
  border-color: color-mix(in srgb, #f97316 50%, var(--accent));
  color: color-mix(in srgb, #c2410c 60%, var(--accent-text));
}
.planner-action-btn--danger:hover:not(:disabled) {
  color: var(--warning-text);
  background: var(--warning-bg);
  border-color: #f97316;
}
/* ── Load tab ──────────────────────────────────────────────────── */
.planner-load-spinner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  padding: 0.75rem 0;
}
.planner-load-spin {
  animation: planner-spin 1s linear infinite;
}
@keyframes planner-spin {
  to { transform: rotate(360deg); }
}
.planner-load-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  padding: 2rem 1rem;
  text-align: center;
}
.planner-load-empty i {
  font-size: 2rem;
}
.planner-plan-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.planner-plan-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0.8rem;
  border-radius: 10px;
  border: 1px solid var(--border-default);
  background: var(--accent-bg);
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s;
}
.planner-plan-item:hover {
  background: var(--accent-subtle);
  border-color: var(--accent-muted);
}
.planner-plan-body {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  flex: 1 1 auto;
  min-width: 0;
}
.planner-plan-name {
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.planner-plan-meta {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
.planner-plan-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planner-plan-date {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}
.planner-notices {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.planner-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.7rem 0.8rem;
  border-radius: 14px;
  border: 1px solid var(--border-default);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-secondary);
  background: var(--surface-glass-light);
}
.planner-notice i {
  flex-shrink: 0;
  font-size: var(--text-base-size);
}
.planner-notice--warn {
  border-color: color-mix(in srgb, #f97316 30%, var(--border-default));
  color: var(--warning-text);
  background: var(--warning-bg);
}
.planner-notice--info {
  background: var(--surface-glass-heavy);
}
.planner-notice--error {
  border-color: color-mix(in srgb, var(--error) 28%, var(--border-default));
  color: var(--error);
  background: var(--error-bg);
}
.brouter-pill-wrap {
  position: relative;
  flex-shrink: 0;
  justify-self: end;
}
.brouter-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.55rem 0.8rem;
  border-radius: 999px;
  border: 1px solid var(--border-default);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  background: var(--surface-glass-subtle);
  cursor: pointer;
}
.brouter-pill:hover {
  background: var(--surface-glass-heavy);
}
.brouter-chevron {
  font-size: 9px;
  opacity: 0.6;
}
.brouter-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  flex-shrink: 0;
}
.brouter-dot--ok {
  background: var(--success);
}
.brouter-dot--warn {
  background: var(--warning);
}
.brouter-dot--off {
  background: var(--text-muted);
  opacity: 0.5;
}
.brouter-label {
  font-size: var(--text-xs-size);
}
.brouter-badge {
  background: var(--warning);
  color: var(--text-inverse);
  border-radius: 999px;
  padding: 0 4px;
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 700;
}
.brouter-detail {
  position: absolute;
  right: 0;
  top: calc(100% + 5px);
  z-index: 50;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  background: var(--surface-glass-heavy);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  min-width: 220px;
}
.brouter-detail-body {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.brouter-detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}
.brouter-detail-label {
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 500;
}
.brouter-detail-val {
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
}
.brouter-val--ok {
  color: var(--success);
}
.brouter-val--warn {
  color: var(--warning);
}
.brouter-detail-refresh {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.55rem;
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--border-default);
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  cursor: pointer;
}
.brouter-detail-refresh:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

/* Save / delete dialog body styling */
.planner-dialog :deep(.p-dialog-content) {
  padding-top: 1rem;
}
.planner-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.planner-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: var(--text-sm-size);
}
.planner-field span em {
  color: var(--text-muted);
  font-style: normal;
  font-weight: 400;
}
.planner-field :deep(.p-inputtext),
.planner-field :deep(.p-textarea) {
  width: 100%;
}
.planner-dialog-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-secondary);
  padding: 0.3rem 0;
}
.planner-dialog-meta i {
  margin-right: 0.2rem;
}

@media (max-width: 640px) {
  .planner-panel {
    padding-inline: 0.75rem;
    padding-bottom: 0.85rem;
  }
  .planner-controls-row {
    align-items: stretch;
  }
  .brouter-pill-wrap {
    width: 100%;
  }
  .brouter-pill {
    width: 100%;
    justify-content: center;
  }
  .brouter-detail {
    left: 0;
    right: auto;
    width: min(100%, 320px);
  }
}
</style>

<style>
/* Global: drag marker is rendered on body via maplibre — scoped CSS does not apply */
.planner-drag-marker {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  border: 3px solid #ff5722;
  background: var(--slider-handle);
  cursor: grab;
}
.planner-drag-marker:active {
  cursor: grabbing;
}

/* Transient marker tracking the elevation-profile cursor. */
.planner-hover-marker {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: #ff5722;
  border: 2px solid var(--slider-handle);
  outline: 2px solid rgba(255, 87, 34, 0.28);
  pointer-events: none;
}
</style>
