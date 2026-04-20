<template>
  <BottomSheet
    v-model="active"
    :detents="[{ height: '40vh' }, { height: '70vh' }, { height: '95vh' }]"
    title="Route Planner"
    icon="bi bi-signpost-split"
    header-mode="compact"
    :no-backdrop="true"
    @closed="onSheetClosed"
  >

    <div class="planner-sheet">
      <p class="planner-intro">
        Tap the map to add waypoints. Tap the orange route to insert a point. Drag markers to reshape. On touch devices, long-press adds a waypoint.
      </p>

      <div v-if="planner.viewportTooLarge.value" class="planner-zoom-hint">
        <i class="bi bi-zoom-in"></i>
        Zoom in to start planning. Visible span must be under ~300 km.
      </div>

      <div v-if="planner.pristineLoaded.value" class="planner-pristine-hint">
        <i class="bi bi-info-circle"></i>
        Loaded plan shown as saved. The first edit will re-route all legs.
      </div>

      <PlannerToolbar
        :profiles="profiles"
        :profile="planner.profile.value"
        :can-undo="planner.canUndo.value"
        :can-redo="planner.canRedo.value"
        :has-waypoints="planner.waypoints.value.length > 0"
        :has-route="planner.routeCoordinates.value.length > 1"
        @profile-changed="planner.setProfile"
        @undo="planner.undo"
        @redo="planner.redo"
        @clear="planner.clearAll"
        @save="openSaveDialog"
      />

      <LiveStatsBar :stats="planner.stats.value" :computing="planner.computing.value" />

      <ElevationProfile
        :coordinates="planner.routeCoordinates.value"
        @hover="onElevationHover"
      />

      <div v-if="planner.lastError.value" class="planner-error">{{ planner.lastError.value }}</div>

      <div v-if="status" class="planner-sidecar">
        <div class="planner-sidecar-head">
          <strong>BRouter</strong>
          <button class="planner-btn" @click="refresh">Refresh</button>
        </div>
        <div v-if="status.available" class="planner-sidecar-body">
          <span>running: {{ status.brouterRunning ? 'yes' : 'no' }}</span>
          <span>segments on disk: {{ status.segmentsOnDisk ?? 0 }}</span>
          <span>queued: {{ status.segmentsQueued ?? 0 }}</span>
          <span v-if="(status.segmentsInProgress?.length ?? 0) > 0">
            in progress: {{ status.segmentsInProgress?.join(', ') }}
          </span>
        </div>
        <div v-else class="planner-sidecar-body">unavailable: {{ status.reason ?? 'unknown' }}</div>
      </div>

      <section class="planner-plans">
        <div class="planner-plans-head">
          <h2>Saved routes</h2>
          <button class="planner-btn" @click="loadPlans" :disabled="plansLoading">
            <i class="bi bi-arrow-clockwise" /> Reload
          </button>
        </div>
        <div v-if="plansLoading" class="planner-plans-loading">loading…</div>
        <ul v-else class="planner-plans-list">
          <li v-for="plan in plans" :key="plan.id">
            <button
              class="planner-plan-info"
              type="button"
              :disabled="loadingPlanId === plan.id"
              :title="`Load ${plan.name} into the editor`"
              @click="loadPlan(plan.id)"
            >
              <strong>{{ plan.name }}</strong>
              <div class="planner-plan-meta">{{ (plan.distanceM / 1000).toFixed(2) }} km</div>
            </button>
            <div class="planner-plan-actions">
              <button
                class="planner-btn planner-btn--icon"
                @click="loadPlan(plan.id)"
                :disabled="loadingPlanId === plan.id"
                :aria-label="`Load ${plan.name}`"
                title="Load into editor"
              >
                <i :class="loadingPlanId === plan.id ? 'bi bi-hourglass-split' : 'bi bi-pencil-square'" />
              </button>
              <button
                class="planner-btn planner-btn--icon"
                @click="downloadPlannedTrackGpx(plan.id, plan.name)"
                :aria-label="`Download GPX for ${plan.name}`"
                title="Download GPX"
              >
                <i class="bi bi-download" />
              </button>
              <button
                class="planner-btn planner-btn--icon planner-btn--danger"
                @click="openDeleteDialog(plan)"
                :aria-label="`Delete ${plan.name}`"
                title="Delete plan"
              >
                <i class="bi bi-trash" />
              </button>
            </div>
          </li>
          <li v-if="plans.length === 0" class="planner-plan-empty">No saved routes yet.</li>
        </ul>
      </section>
    </div>

    <!-- Save dialog -->
    <Dialog
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
          <Textarea v-model="saveDescription" rows="2" autoResize placeholder="Notes for future you…" />
        </label>
        <div class="planner-dialog-meta">
          <span><i class="bi bi-rulers"></i> {{ (planner.stats.value.distanceM / 1000).toFixed(2) }} km</span>
          <span><i class="bi bi-arrow-up-right"></i> {{ Math.round(planner.stats.value.ascentM) }} m</span>
          <span><i class="bi bi-arrow-down-right"></i> {{ Math.round(planner.stats.value.descentM) }} m</span>
          <span><i class="bi bi-pin-map"></i> {{ planner.waypoints.value.length }} waypoints</span>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" text @click="saveDialogVisible = false" :disabled="saveSubmitting" />
        <Button
          :label="saveSubmitting ? 'Saving…' : 'Save plan'"
          icon="pi pi-save"
          :disabled="!saveName.trim() || saveSubmitting"
          @click="confirmSave"
        />
      </template>
    </Dialog>

    <!-- Delete confirm dialog -->
    <Dialog
      v-model:visible="deleteDialogVisible"
      :modal="true"
      header="Delete planned route?"
      :style="{ width: 'min(420px, 92vw)' }"
      :draggable="false"
      :dismissable-mask="true"
      class="planner-dialog"
    >
      <div class="planner-dialog-body planner-dialog-confirm">
        <i class="pi pi-exclamation-triangle planner-dialog-icon" />
        <span>
          This will permanently delete
          <strong>{{ planToDelete?.name }}</strong>
          ({{ planToDelete ? (planToDelete.distanceM / 1000).toFixed(2) : 0 }} km).
          This cannot be undone.
        </span>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" text @click="deleteDialogVisible = false" :disabled="deleteSubmitting" />
        <Button
          :label="deleteSubmitting ? 'Deleting…' : 'Delete'"
          icon="pi pi-trash"
          severity="danger"
          :disabled="deleteSubmitting"
          @click="confirmDelete"
        />
      </template>
    </Dialog>
  </BottomSheet>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue';
import maplibregl from 'maplibre-gl';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import PlannerToolbar from '@/planner/components/PlannerToolbar.vue';
import LiveStatsBar from '@/planner/components/LiveStatsBar.vue';
import ElevationProfile from '@/planner/components/ElevationProfile.vue';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import { usePlannerState } from '@/planner/composables/usePlannerState';
import { useBRouterSegmentStatus } from '@/planner/composables/useBRouterSegmentStatus';
import {
  fetchPlannerConfig,
  listPlannedTracks,
  loadPlannedTrack,
  savePlannedRoute,
  deletePlannedTrack,
  downloadPlannedTrackGpx,
} from '@/planner/repositories/plannerRepository';
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
  components: { BottomSheet, PlannerToolbar, LiveStatsBar, ElevationProfile, Dialog, InputText, Textarea, Button },
  emits: ['active-changed', 'tool-opened', 'tool-closed'],
  props: {
    map: { type: Object as () => MapLike | undefined, default: undefined },
  },
  setup() {
    const planner = usePlannerState();
    // `active` lives here so we can gate BRouter status polling on it —
    // no point hitting /api/planner/status while the planner sheet is closed.
    const active = ref(false);
    const { status, refresh } = useBRouterSegmentStatus(active);
    return { planner, status, refresh, downloadPlannedTrackGpx, active };
  },
  data() {
    return {
      profiles: ['trekking'] as string[],
      plans: [] as PlannedTrackSummary[],
      plansLoading: false,
      loadingPlanId: null as number | null,
      // dialogs
      saveDialogVisible: false,
      saveName: '',
      saveDescription: '',
      saveSubmitting: false,
      deleteDialogVisible: false,
      planToDelete: null as PlannedTrackSummary | null,
      deleteSubmitting: false,
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
    };
  },
  watch: {
    // Map instance may be swapped out on theme reload — re-attach layers if open.
    map(newMap: unknown, oldMap: unknown) {
      const self = this as unknown as { active: boolean; attachedMap: unknown; detachFromMap: () => void; attachToMap: () => void };
      if (self.active && oldMap && self.attachedMap === oldMap) {
        self.detachFromMap();
      }
      if (self.active && newMap) {
        self.attachToMap();
      }
    },
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
          await Promise.all([this.loadConfig(), this.loadPlans()]);
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

    async loadPlans() {
      this.plansLoading = true;
      try {
        this.plans = await listPlannedTracks();
      } finally {
        this.plansLoading = false;
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
        await this.loadPlans();
      } finally {
        this.saveSubmitting = false;
      }
    },

    openDeleteDialog(plan: PlannedTrackSummary) {
      this.planToDelete = plan;
      this.deleteDialogVisible = true;
    },

    async confirmDelete() {
      if (!this.planToDelete) return;
      this.deleteSubmitting = true;
      try {
        await deletePlannedTrack(this.planToDelete.id);
        this.deleteDialogVisible = false;
        this.planToDelete = null;
        await this.loadPlans();
      } finally {
        this.deleteSubmitting = false;
      }
    },

    async loadPlan(id: number) {
      this.loadingPlanId = id;
      try {
        const detail = await loadPlannedTrack(id);
        this.planner.loadPlan({
          profile: detail.profile,
          waypoints: detail.waypoints,
          coordinates: detail.coordinates,
          distanceM: detail.distanceM,
        });
        // Fly to the loaded route's bbox so the user sees what they loaded.
        const map = this.attachedMap;
        if (map && detail.coordinates.length > 1) {
          let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
          for (const c of detail.coordinates) {
            if (c[1] < minLat) minLat = c[1];
            if (c[1] > maxLat) maxLat = c[1];
            if (c[0] < minLng) minLng = c[0];
            if (c[0] > maxLng) maxLng = c[0];
          }
          map.fitBounds([[minLng, minLat], [maxLng, maxLat]], { padding: 60, duration: 600, maxZoom: 15 });
        }
      } finally {
        this.loadingPlanId = null;
      }
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
        { deep: true },
      );
      this.unwatchWaypoints = this.$watch(
        () => this.planner.waypoints.value,
        () => this.syncSources(),
        { deep: true },
      );
    },

    detachFromMap() {
      const map = this.attachedMap;
      if (this.unwatchRoute) { this.unwatchRoute(); this.unwatchRoute = null; }
      if (this.unwatchWaypoints) { this.unwatchWaypoints(); this.unwatchWaypoints = null; }
      if (this.longPressTimer !== null) {
        window.clearTimeout(this.longPressTimer);
        this.longPressTimer = null;
      }
      this.longPressLngLat = null;

      for (const marker of this.dragMarkerMap.values()) marker.remove();
      this.dragMarkerMap.clear();
      if (this.hoverMarker) { this.hoverMarker.remove(); this.hoverMarker = null; }

      if (!map) { this.attachedMap = null; return; }
      if (this.clickHandler) { map.off('click', this.clickHandler); this.clickHandler = null; }
      if (this.moveHandler) { map.off('moveend', this.moveHandler); this.moveHandler = null; }
      if (this.touchStartHandler) { map.off('touchstart', this.touchStartHandler); this.touchStartHandler = null; }
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
      } catch (e) {
        // Map may already be torn down on theme reload — safe to ignore
      }
      this.attachedMap = null;
    },

    routeFeature() {
      const coords = this.planner.routeCoordinates.value;
      return {
        type: 'FeatureCollection',
        features: coords.length >= 2 ? [{
          type: 'Feature',
          properties: {},
          geometry: {
            type: 'LineString',
            coordinates: coords.map((c) => [c[0], c[1]]),
          },
        }] : [],
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
        if (this.hoverMarker) { this.hoverMarker.remove(); this.hoverMarker = null; }
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
  beforeUnmount() {
    this.detachFromMap();
  },
});
</script>

<style scoped>
.planner-sheet {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0.25rem 1rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.planner-intro {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.4;
}
.planner-zoom-hint {
  padding: 0.6rem 0.8rem;
  border-radius: 12px;
  background: var(--warning-bg);
  border: 1px solid color-mix(in srgb, var(--warning) 24%, transparent);
  color: var(--warning-text);
  font-size: 0.9rem;
  line-height: 1.4;
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}
.planner-zoom-hint i {
  font-size: 1.1rem;
  margin-top: 0.05rem;
  flex-shrink: 0;
}
.planner-pristine-hint {
  padding: 0.6rem 0.8rem;
  border-radius: 8px;
  background: #e7f0ff;
  color: #1e3a8a;
  font-size: 0.9rem;
  line-height: 1.4;
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}
.planner-pristine-hint i {
  font-size: 1.1rem;
  margin-top: 0.05rem;
  flex-shrink: 0;
}
.planner-error {
  padding: 0.6rem 0.8rem;
  border-radius: 12px;
  background: var(--error-bg);
  border: 1px solid color-mix(in srgb, var(--error) 20%, transparent);
  color: var(--error);
  font-size: 0.9rem;
}
.planner-sidecar,
.planner-plans {
  padding: 0.9rem;
  border-radius: 16px;
  background: linear-gradient(180deg, var(--surface-glass-heavy), var(--surface-glass));
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-medium);
}
.planner-sidecar-head,
.planner-plans-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}
.planner-plans-head h2 {
  margin: 0;
  font-size: 1rem;
  color: var(--text-primary);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.planner-sidecar-body {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}
.planner-plans-list {
  list-style: none;
  margin: 0.75rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.planner-plans-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.7rem;
  padding: 0.35rem;
  border-radius: 12px;
  background: color-mix(in srgb, var(--surface-glass-light) 78%, transparent);
  border: 1px solid var(--border-default);
}
.planner-plans-list li:last-child { border-bottom: 0; }
.planner-plan-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  flex: 1 1 auto;
  min-width: 0;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 0.6rem 0.7rem;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.planner-plan-info:hover {
  background: var(--accent-bg);
  color: var(--text-primary);
}
.planner-plan-info:disabled { opacity: 0.6; cursor: progress; }
.planner-plan-info strong {
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.planner-plan-meta { color: var(--text-muted); font-size: 0.8rem; }
.planner-plan-empty {
  color: var(--text-muted);
  padding: 0.85rem 0.25rem;
  justify-content: center;
  background: transparent;
  border-style: dashed;
}
.planner-plan-actions { display: flex; gap: 0.4rem; flex-shrink: 0; }
.planner-plans-loading { color: var(--text-muted); font-size: 0.9rem; margin-top: 0.5rem; }
.planner-btn {
  padding: 0.45rem 0.7rem;
  border-radius: 10px;
  border: 1px solid var(--border-medium);
  background: color-mix(in srgb, var(--surface-glass-heavy) 86%, transparent);
  color: var(--text-secondary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.24);
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  min-height: 36px;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.planner-btn:hover {
  background: var(--surface-hover);
  border-color: var(--border-hover);
  color: var(--text-primary);
}
.planner-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.planner-btn--icon {
  width: 36px;
  justify-content: center;
  padding: 0.4rem 0;
}
.planner-btn--danger {
  color: var(--error);
  border-color: color-mix(in srgb, var(--error) 30%, var(--border-medium));
}
.planner-btn--danger:hover {
  background: var(--error-bg);
  border-color: color-mix(in srgb, var(--error) 45%, var(--border-medium));
}

/* Save / delete dialog body styling */
.planner-dialog :deep(.p-dialog-content) { padding-top: 1rem; }
.planner-dialog-body { display: flex; flex-direction: column; gap: 0.85rem; }
.planner-field { display: flex; flex-direction: column; gap: 0.35rem; font-size: 0.85rem; }
.planner-field span em { color: var(--text-muted); font-style: normal; font-weight: 400; }
.planner-field :deep(.p-inputtext),
.planner-field :deep(.p-textarea) { width: 100%; }
.planner-dialog-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
  padding: 0.5rem 0.6rem;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 10px;
}
.planner-dialog-meta i { margin-right: 0.25rem; }
.planner-dialog-confirm { flex-direction: row; align-items: flex-start; gap: 0.85rem; line-height: 1.5; }
.planner-dialog-icon { font-size: 1.6rem; color: var(--warning); flex-shrink: 0; margin-top: 0.15rem; }

@media (max-width: 640px) {
  .planner-plan-info strong { font-size: 0.95rem; }
  .planner-btn { font-size: 0.85rem; }
}
</style>

<style>
/* Global: drag marker is rendered on body via maplibre — scoped CSS does not apply */
.planner-drag-marker {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  border: 3px solid #ff5722;
  background: #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
  cursor: grab;
}
.planner-drag-marker:active { cursor: grabbing; }

/* Transient marker tracking the elevation-profile cursor. */
.planner-hover-marker {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: #ff5722;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px rgba(255, 87, 34, 0.35), 0 2px 6px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}
</style>
