<template>
  <BottomSheet
    v-model="active"
    icon="bi bi-signpost-split"
    :detents="[{ height: '40vh' }, { height: '70vh' }, { height: '95vh' }]"
    :initial-detent="1"
    fit-content-initial
    :no-backdrop="true"
    @closed="onSheetClosed"
  >
    <template #title>
      <div class="planner-header-nav">
        <i class="bi bi-signpost-split planner-sheet-icon"></i>
        <div class="planner-header-tabs">
          <button
            class="planner-header-tab"
            :class="{ 'planner-header-tab--active': activeTab === 'draw' }"
            @pointerdown.stop
            @click="activeTab = 'draw'"
          >
            Drawing
          </button>
          <button
            class="planner-header-tab"
            :class="{ 'planner-header-tab--active': activeTab === 'load' }"
            @pointerdown.stop
            @click="switchToLoad"
          >
            Load
          </button>
        </div>
      </div>
    </template>
    <template #header-actions>
      <div ref="brouterPanel" class="brouter-pill-wrap brouter-pill-wrap--header" @pointerdown.stop @click.stop>
        <button
          type="button"
          :class="[
            'brouter-pill',
            'brouter-pill--icon',
            brouterStatusClass,
            { 'brouter-pill--updating': routeUpdateVisible && !brouterNeedsAttention },
          ]"
          :aria-expanded="brouterExpanded"
          :aria-label="brouterStatusLabel"
          :title="brouterStatusLabel"
          @click="brouterExpanded = !brouterExpanded"
        >
          <i :class="brouterIconClass" aria-hidden="true"></i>
          <span v-if="brouterBadgeText" :class="['brouter-badge', { 'brouter-badge--danger': brouterNeedsAttention }]">
            {{ brouterBadgeText }}
          </span>
        </button>
        <div v-if="brouterExpanded" class="brouter-detail brouter-detail--header">
          <p class="brouter-detail-summary">{{ brouterStatusSummary }}</p>
          <div v-if="!status" class="brouter-detail-body">
            <span class="brouter-val--warn">Checking BRouter status...</span>
          </div>
          <div v-else-if="status.available" class="brouter-detail-body">
            <div class="brouter-detail-row">
              <span class="brouter-detail-label">Running</span>
              <span :class="['brouter-detail-val', status.brouterRunning ? 'brouter-val--ok' : 'brouter-val--warn']">{{
                status.brouterRunning ? 'yes' : 'no'
              }}</span>
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
    </template>
    <div class="planner-root">
      <!-- ── Drawing tab ─────────────────────────────────────────── -->
      <div v-if="activeTab === 'draw'" class="planner-panel planner-panel--draw">
        <div class="planner-controls-row">
          <div class="planner-control-group planner-control-group--profile">
            <PlannerToolbar
              :profiles="profiles"
              :profile="planner.profile.value"
              @profile-changed="planner.setProfile"
            />
          </div>

          <div class="planner-control-group planner-control-group--actions">
            <div class="planner-actions" role="group" aria-label="Edit history">
              <button
                type="button"
                class="planner-action-btn"
                :disabled="!planner.canUndo.value"
                title="Undo"
                aria-label="Undo"
                @click="planner.undo"
              >
                <i class="bi bi-arrow-counterclockwise" />
              </button>
              <button
                type="button"
                class="planner-action-btn"
                :disabled="!planner.canRedo.value"
                title="Redo"
                aria-label="Redo"
                @click="planner.redo"
              >
                <i class="bi bi-arrow-clockwise" />
              </button>
              <button
                type="button"
                class="planner-action-btn"
                :disabled="!planner.waypoints.value.length"
                title="Clear route"
                aria-label="Clear route"
                @click="clearRoute"
              >
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
          </div>

          <div class="planner-control-group planner-control-group--hint">
            <p class="planner-subtitle">Tap the map to add waypoints. Drag to adjust.</p>
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

        <LiveStatsBar :stats="planner.stats.value" />

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
        <template v-else>
          <div v-if="planActionError" class="planner-load-error" role="alert">
            <i class="bi bi-exclamation-circle"></i>
            <span>{{ planActionError }}</span>
          </div>
          <ul class="planner-plan-list">
            <li v-for="plan in savedPlans" :key="plan.id" class="planner-plan-item">
              <button
                type="button"
                class="planner-plan-open"
                :disabled="deletingPlanId === plan.id"
                @click="selectPlan(plan.id)"
              >
                <span class="planner-plan-body">
                  <span class="planner-plan-name">{{ plan.name }}</span>
                  <span class="planner-plan-meta">
                    <i class="bi bi-rulers"></i> {{ (plan.distanceM / 1000).toFixed(1) }} km
                    <span v-if="plan.description" class="planner-plan-desc">· {{ plan.description }}</span>
                  </span>
                </span>
                <span class="planner-plan-date">{{ formatDate(plan.createDate) }}</span>
              </button>
              <span class="planner-plan-actions">
                <button
                  type="button"
                  class="planner-plan-action planner-plan-export"
                  :disabled="exportingPlanId !== null || deletingPlanId !== null"
                  :title="`Export ${plan.name || 'route'} as GPX`"
                  :aria-label="`Export ${plan.name || 'route'} as GPX`"
                  @click.stop="exportPlanGpx(plan)"
                >
                  <i
                    :class="exportingPlanId === plan.id ? 'bi bi-arrow-repeat planner-load-spin' : 'bi bi-download'"
                  ></i>
                  <span>GPX</span>
                </button>
                <button
                  type="button"
                  class="planner-plan-action planner-plan-delete"
                  :disabled="exportingPlanId !== null || deletingPlanId !== null"
                  :title="`Delete ${plan.name || 'route'}`"
                  :aria-label="`Delete ${plan.name || 'route'}`"
                  @click.stop="openDeleteDialog(plan)"
                >
                  <i :class="deletingPlanId === plan.id ? 'bi bi-arrow-repeat planner-load-spin' : 'bi bi-trash'"></i>
                </button>
              </span>
            </li>
          </ul>
        </template>
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
          <span
            ><i class="bi bi-pin-map"></i> {{ planner.waypoints.value.length }} /
            {{ planner.maxWaypoints.value }} waypoints</span
          >
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

    <!-- Delete confirmation dialog -->
    <PrimeDialog
      v-model:visible="deleteDialogVisible"
      :modal="true"
      header="Delete saved route"
      :style="{ width: 'min(420px, 92vw)' }"
      :draggable="false"
      :dismissable-mask="deletingPlanId === null"
      :closable="deletingPlanId === null"
      class="planner-dialog"
    >
      <div class="planner-dialog-body">
        <p class="planner-delete-copy">
          Delete <strong>{{ planPendingDelete?.name || 'this saved route' }}</strong> from saved routes?
        </p>
        <div v-if="planPendingDelete" class="planner-dialog-meta">
          <span><i class="bi bi-rulers"></i> {{ (planPendingDelete.distanceM / 1000).toFixed(2) }} km</span>
          <span><i class="bi bi-calendar3"></i> {{ formatDate(planPendingDelete.createDate) }}</span>
        </div>
        <div v-if="planDeleteError" class="planner-load-error" role="alert">
          <i class="bi bi-exclamation-circle"></i>
          <span>{{ planDeleteError }}</span>
        </div>
      </div>
      <template #footer>
        <PrimeButton
          label="Cancel"
          severity="secondary"
          text
          :disabled="deletingPlanId !== null"
          @click="deleteDialogVisible = false"
        />
        <PrimeButton
          :label="deletingPlanId !== null ? 'Deleting…' : 'Delete route'"
          icon="pi pi-trash"
          severity="danger"
          :disabled="!planPendingDelete || deletingPlanId !== null"
          @click="confirmDeletePlan"
        />
      </template>
    </PrimeDialog>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
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
import {
  deletePlannedTrack,
  downloadPlannedTrackGpx,
  fetchPlannerConfig,
  savePlannedRoute,
  listPlannedTracks,
  loadPlannedTrack,
} from '@/planner/repositories/plannerRepository';
import type { PlannedTrackSummary, Waypoint } from '@/planner/types';
import {
  distanceToRouteLegSquared,
  nearestRouteLegIndexFromCandidates,
  routeLegCoordinateGroups,
  type RouteCoordinate,
} from '@/planner/utils/routeHitTesting';
import {
  MAP_CLICK_SUPPRESSION_MS,
  PLANNER_LAYER_ID,
  PLANNER_ROUTE_HIT_LAYER_ID,
  PLANNER_SOURCE_ID,
  PLANNER_WAYPOINT_DRAG_RING_LAYER_ID,
  PLANNER_WAYPOINT_HIT_LAYER_ID,
  PLANNER_WAYPOINT_LAYER_ID,
  PLANNER_WAYPOINT_SOURCE_ID,
  ROUTE_HIT_LINE_WIDTH_PX,
  ROUTE_LINE_COLOR,
  ROUTE_LINE_WIDTH_PX,
  ROUTE_MOUSE_INSERT_RADIUS_PX,
  ROUTE_TOUCH_INSERT_RADIUS_PX,
  SYNTHETIC_CLICK_SUPPRESSION_RADIUS_PX,
  TOUCH_SYNTHETIC_CLICK_SUPPRESSION_MS,
  WAYPOINT_ACTIVE_RING_RADIUS_PX,
  WAYPOINT_DRAG_RING_COLOR,
  WAYPOINT_DRAGGING_RING_RADIUS_PX,
  WAYPOINT_FILL_COLOR,
  WAYPOINT_MOUSE_DRAG_THRESHOLD_PX,
  WAYPOINT_MOUSE_HIT_RADIUS_PX,
  WAYPOINT_RADIUS_PX,
  WAYPOINT_STROKE_COLOR,
  WAYPOINT_STROKE_WIDTH_PX,
  WAYPOINT_TOUCH_DRAG_THRESHOLD_PX,
  WAYPOINT_TOUCH_HIT_RADIUS_PX,
} from '@/planner/constants/PlannerConstants';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type MapLike = any;
type PlannerPointerKind = 'mouse' | 'touch';
type PlannerInteractionMode = 'waypointPointerDown' | 'waypointDragging';
type PlannerScreenPoint = { x: number; y: number };
type PlannerLngLat = { lng: number; lat: number };
type PlannerInteraction = {
  mode: PlannerInteractionMode;
  pointerKind: PlannerPointerKind;
  startPoint: PlannerScreenPoint;
  lastLngLat: PlannerLngLat;
  waypointId?: string;
  touchId?: number;
};
type PlannerMapPointerEvent = maplibregl.MapMouseEvent | maplibregl.MapTouchEvent;
type RouteHitFeature = {
  properties?: { legIndex?: unknown };
};
type RouteHitDebug = {
  rawHitCount: number;
  candidates: Array<{
    legIndex: number;
    distancePx: number;
    start: [number, number] | null;
    end: [number, number] | null;
  }>;
  selectedLegIndex: number | null;
  insertRadiusPx: number;
};

const ROUTE_UPDATE_MIN_VISIBLE_MS = 320;
const PLANNER_INTERACTION_DEBUG = import.meta.env.DEV;

const props = defineProps<{
  map?: MapLike;
}>();

const emit = defineEmits<{
  (event: 'active-changed', active: boolean): void;
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
}>();

defineOptions({ name: 'PlannerTool' });

const brouterPanel = ref<HTMLElement | null>(null);
const planner = usePlannerState();
// `active` lives here so we can gate BRouter status polling on it —
// no point hitting /api/planner/status while the planner sheet is closed.
const active = ref(false);
const routeUpdateVisible = ref(false);
const { status, refresh } = useBRouterSegmentStatus(active);
let routeUpdateShownAt = 0;
let routeUpdateHideTimer: number | undefined;

const profiles = ref<string[]>(['trekking']);
const activeTab = ref<'draw' | 'load'>('draw');
const savedPlans = ref<PlannedTrackSummary[]>([]);
const plansLoading = ref(false);
const exportingPlanId = ref<number | null>(null);
const deletingPlanId = ref<number | null>(null);
const planActionError = ref('');
const planDeleteError = ref('');
const deleteDialogVisible = ref(false);
const planPendingDelete = ref<PlannedTrackSummary | null>(null);
const saveDialogVisible = ref(false);
const saveName = ref('');
const saveDescription = ref('');
const saveSubmitting = ref(false);
const attachedMap = shallowRef<MapLike | null>(null);
let clickHandler: ((ev: maplibregl.MapMouseEvent) => void) | null = null;
let pointerDownHandler: ((ev: PlannerMapPointerEvent) => void) | null = null;
let pointerMoveHandler: ((ev: PlannerMapPointerEvent) => void) | null = null;
let pointerUpHandler: ((ev: PlannerMapPointerEvent) => void) | null = null;
let pointerCancelHandler: ((ev: PlannerMapPointerEvent) => void) | null = null;
let keydownHandler: ((ev: KeyboardEvent) => void) | null = null;
let moveHandler: (() => void) | null = null;
const hoverMarker = shallowRef<MapLike | null>(null);
const selectedWaypointDeleteMarker = shallowRef<MapLike | null>(null);
const plannerInteraction = ref<PlannerInteraction | null>(null);
const selectedWaypointId = ref<string | null>(null);
let suppressMapClickUntil = 0;
let suppressMapClickPoint: PlannerScreenPoint | null = null;
let unwatchRoute: null | (() => void) = null;
let unwatchWaypoints: null | (() => void) = null;
let brouterOutsideClickHandler: ((ev: PointerEvent) => void) | null = null;
const configLoaded = ref(false);
const brouterExpanded = ref(false);

function clearRouteUpdateHideTimer() {
  if (routeUpdateHideTimer === undefined) return;
  window.clearTimeout(routeUpdateHideTimer);
  routeUpdateHideTimer = undefined;
}

watch(planner.computing, (computing) => {
  if (computing) {
    clearRouteUpdateHideTimer();
    routeUpdateShownAt = Date.now();
    routeUpdateVisible.value = true;
    return;
  }

  if (!routeUpdateVisible.value) return;

  const elapsedMs = Date.now() - routeUpdateShownAt;
  const remainingMs = Math.max(ROUTE_UPDATE_MIN_VISIBLE_MS - elapsedMs, 0);
  clearRouteUpdateHideTimer();
  routeUpdateHideTimer = window.setTimeout(() => {
    routeUpdateVisible.value = false;
    routeUpdateHideTimer = undefined;
  }, remainingMs);
});

const brouterNeedsAttention = computed(() => {
  const s = status.value;
  return Boolean(s && (!s.available || !s.brouterRunning));
});
const brouterBadgeText = computed(() => {
  if (brouterNeedsAttention.value) return '!';
  const queued = status.value?.segmentsQueued ?? 0;
  return queued > 0 ? String(queued) : '';
});
const brouterIconClass = computed(() => {
  return brouterNeedsAttention.value ? 'bi bi-cloud-slash brouter-icon' : 'bi bi-cloud brouter-icon';
});
const brouterStatusClass = computed(() => {
  const s = status.value;
  if (!s) return 'brouter-pill--checking';
  if (!s.available || !s.brouterRunning) return 'brouter-pill--danger';
  if ((s.segmentsQueued ?? 0) > 0 || (s.segmentsInProgress?.length ?? 0) > 0) return 'brouter-pill--warn';
  return 'brouter-pill--ok';
});
const brouterStatusLabel = computed(() => {
  if (routeUpdateVisible.value) return 'BRouter status: updating route';
  const s = status.value;
  if (!s) return 'BRouter status: checking';
  if (!s.available) return `BRouter status: unavailable${s.reason ? ` (${s.reason})` : ''}`;
  if (!s.brouterRunning) return 'BRouter status: stopped';
  const queued = s.segmentsQueued ?? 0;
  const inProgress = s.segmentsInProgress?.length ?? 0;
  if (queued > 0 || inProgress > 0) {
    return `BRouter status: preparing ${queued + inProgress} segment${queued + inProgress === 1 ? '' : 's'}`;
  }
  return 'BRouter status: ready';
});
const brouterStatusSummary = computed(() => {
  const s = status.value;
  if (!s) return 'Checks whether local routing is ready.';
  if (!s.available) return 'Start the BRouter sidecar, then refresh.';
  if (!s.brouterRunning) return 'Start BRouter in the backend stack, then refresh.';
  if ((s.segmentsQueued ?? 0) > 0 || (s.segmentsInProgress?.length ?? 0) > 0) {
    return 'Preparing map segments for route calculation.';
  }
  return 'Calculates routes from your waypoints.';
});
const hasSelectedWaypoint = computed(() => {
  return Boolean(
    selectedWaypointId.value && planner.waypoints.value.some((waypoint) => waypoint.id === selectedWaypointId.value)
  );
});

function isOpen(): boolean {
  return active.value;
}

async function toggle() {
  active.value = !active.value;
  emit('active-changed', active.value);
  if (active.value) {
    emit('tool-opened');
    if (!configLoaded.value) {
      await loadConfig();
      configLoaded.value = true;
    }
    attachToMap();
  } else {
    detachFromMap();
  }
}

function close() {
  if (!active.value) return;
  active.value = false;
  emit('active-changed', false);
  detachFromMap();
}

function onSheetClosed() {
  emit('active-changed', false);
  emit('tool-closed');
  detachFromMap();
}

// ── Config / plans loading ─────────────────────────────────────
async function loadConfig() {
  try {
    const cfg = await fetchPlannerConfig();
    profiles.value = cfg.profiles.length ? cfg.profiles : ['trekking'];
    planner.setMaxWaypoints(cfg.maxWaypoints);
    if (!profiles.value.includes(planner.profile.value)) {
      planner.setProfile(cfg.defaultProfile);
    }
  } catch (e) {
    console.warn('[planner] failed to load config', e);
  }
}

async function saveCurrent() {
  // Legacy entry point — kept for any external callers; opens the proper dialog.
  openSaveDialog();
}

function openSaveDialog() {
  if (planner.routeCoordinates.value.length < 2) return;
  saveName.value = saveName.value || suggestedPlanName();
  saveDescription.value = '';
  saveDialogVisible.value = true;
}

function suggestedPlanName(): string {
  const km = (planner.stats.value.distanceM / 1000).toFixed(1);
  const date = new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  return `Plan — ${km} km (${date})`;
}

async function confirmSave() {
  const name = saveName.value.trim();
  if (!name || planner.routeCoordinates.value.length < 2) return;
  saveSubmitting.value = true;
  try {
    await savePlannedRoute({
      name,
      description: saveDescription.value.trim() || undefined,
      profile: planner.profile.value,
      coordinates: planner.routeCoordinates.value,
      legs: planner.legs.value,
      stats: planner.stats.value,
      waypoints: planner.waypoints.value.map((w: Waypoint) => ({ lat: w.lat, lng: w.lng })),
    });
    saveDialogVisible.value = false;
    saveName.value = '';
    saveDescription.value = '';
    // Refresh the saved plans list so next Load tab visit is up-to-date
    savedPlans.value = await listPlannedTracks().catch(() => []);
  } finally {
    saveSubmitting.value = false;
  }
}

// ── Load plans ───────────────────────────────────────────────────
async function switchToLoad() {
  activeTab.value = 'load';
  plansLoading.value = true;
  planActionError.value = '';
  planDeleteError.value = '';
  try {
    savedPlans.value = await listPlannedTracks();
  } catch {
    savedPlans.value = [];
  } finally {
    plansLoading.value = false;
  }
}

async function exportPlanGpx(plan: PlannedTrackSummary) {
  planActionError.value = '';
  exportingPlanId.value = plan.id;
  try {
    await downloadPlannedTrackGpx(plan.id, plan.name);
  } catch (e) {
    console.warn('[planner] failed to export plan as GPX', plan.id, e);
    planActionError.value = 'Could not export this route as GPX. Please try again.';
  } finally {
    exportingPlanId.value = null;
  }
}

function openDeleteDialog(plan: PlannedTrackSummary) {
  if (exportingPlanId.value !== null || deletingPlanId.value !== null) return;
  planPendingDelete.value = plan;
  planActionError.value = '';
  planDeleteError.value = '';
  deleteDialogVisible.value = true;
}

async function confirmDeletePlan() {
  const plan = planPendingDelete.value;
  if (!plan || deletingPlanId.value !== null) return;
  planDeleteError.value = '';
  deletingPlanId.value = plan.id;
  try {
    await deletePlannedTrack(plan.id);
    savedPlans.value = savedPlans.value.filter((savedPlan: PlannedTrackSummary) => savedPlan.id !== plan.id);
    deleteDialogVisible.value = false;
    planPendingDelete.value = null;
    planDeleteError.value = '';
  } catch (e) {
    console.warn('[planner] failed to delete plan', plan.id, e);
    planDeleteError.value = 'Could not delete this saved route. Please try again.';
  } finally {
    deletingPlanId.value = null;
  }
}

async function selectPlan(id: number) {
  try {
    const detail = await loadPlannedTrack(id);
    planner.loadPlan(detail);
    activeTab.value = 'draw';
  } catch (e) {
    console.warn('[planner] failed to load plan', id, e);
  }
}

function formatDate(iso: string): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function installBrouterOutsideClickHandler() {
  if (brouterOutsideClickHandler) return;
  brouterOutsideClickHandler = (ev: PointerEvent) => {
    const panel = brouterPanel.value as HTMLElement | undefined;
    if (panel?.contains(ev.target as Node)) return;
    brouterExpanded.value = false;
  };
  document.addEventListener('pointerdown', brouterOutsideClickHandler, true);
}

function removeBrouterOutsideClickHandler() {
  if (!brouterOutsideClickHandler) return;
  document.removeEventListener('pointerdown', brouterOutsideClickHandler, true);
  brouterOutsideClickHandler = null;
}

// ── Map integration ─────────────────────────────────────────────
function attachToMap() {
  const map = props.map;
  if (!map) return;
  if (attachedMap.value === map) return;
  attachedMap.value = map;

  const addLayers = () => {
    if (!map.getSource(PLANNER_SOURCE_ID)) {
      map.addSource(PLANNER_SOURCE_ID, { type: 'geojson', data: routeFeature() as never });
    }
    if (!map.getLayer(PLANNER_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_LAYER_ID,
        type: 'line',
        source: PLANNER_SOURCE_ID,
        layout: {
          'line-cap': 'round',
          'line-join': 'round',
        },
        paint: {
          'line-color': ROUTE_LINE_COLOR,
          'line-width': ROUTE_LINE_WIDTH_PX,
          'line-opacity': 0.95,
        },
      });
    }
    if (!map.getLayer(PLANNER_ROUTE_HIT_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_ROUTE_HIT_LAYER_ID,
        type: 'line',
        source: PLANNER_SOURCE_ID,
        layout: {
          'line-cap': 'round',
          'line-join': 'round',
        },
        paint: {
          'line-color': ROUTE_LINE_COLOR,
          'line-width': ROUTE_HIT_LINE_WIDTH_PX,
          'line-opacity': 0,
        },
      });
    }
    if (!map.getSource(PLANNER_WAYPOINT_SOURCE_ID)) {
      map.addSource(PLANNER_WAYPOINT_SOURCE_ID, { type: 'geojson', data: waypointFeature() as never });
    }
    if (!map.getLayer(PLANNER_WAYPOINT_DRAG_RING_LAYER_ID)) {
      const beforeLayerId = map.getLayer(PLANNER_WAYPOINT_LAYER_ID) ? PLANNER_WAYPOINT_LAYER_ID : undefined;
      map.addLayer(
        {
          id: PLANNER_WAYPOINT_DRAG_RING_LAYER_ID,
          type: 'circle',
          source: PLANNER_WAYPOINT_SOURCE_ID,
          paint: {
            'circle-radius': [
              'case',
              ['boolean', ['get', 'dragging'], false],
              WAYPOINT_DRAGGING_RING_RADIUS_PX,
              ['boolean', ['get', 'active'], false],
              WAYPOINT_ACTIVE_RING_RADIUS_PX,
              0,
            ],
            'circle-color': WAYPOINT_DRAG_RING_COLOR,
            'circle-stroke-color': ROUTE_LINE_COLOR,
            'circle-stroke-width': ['case', ['boolean', ['get', 'active'], false], 2, 0],
            'circle-opacity': ['case', ['boolean', ['get', 'active'], false], 1, 0],
          },
        },
        beforeLayerId
      );
    }
    if (!map.getLayer(PLANNER_WAYPOINT_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_WAYPOINT_LAYER_ID,
        type: 'circle',
        source: PLANNER_WAYPOINT_SOURCE_ID,
        paint: {
          'circle-radius': [
            'case',
            ['boolean', ['get', 'dragging'], false],
            WAYPOINT_RADIUS_PX + 1,
            WAYPOINT_RADIUS_PX,
          ],
          'circle-color': WAYPOINT_FILL_COLOR,
          'circle-stroke-color': WAYPOINT_STROKE_COLOR,
          'circle-stroke-width': WAYPOINT_STROKE_WIDTH_PX,
        },
      });
    }
    if (!map.getLayer(PLANNER_WAYPOINT_HIT_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_WAYPOINT_HIT_LAYER_ID,
        type: 'circle',
        source: PLANNER_WAYPOINT_SOURCE_ID,
        paint: {
          'circle-radius': WAYPOINT_TOUCH_HIT_RADIUS_PX,
          'circle-color': WAYPOINT_STROKE_COLOR,
          'circle-opacity': 0,
          'circle-stroke-opacity': 0,
        },
      });
    }
    syncSources();
    emitViewport();
  };

  if (map.isStyleLoaded && map.isStyleLoaded()) {
    addLayers();
  } else {
    map.once('load', addLayers);
  }

  // Click (add / insert waypoint)
  clickHandler = (ev: maplibregl.MapMouseEvent) => handleMapClick(ev);
  map.on('click', clickHandler);

  // Layer-based direct manipulation
  pointerDownHandler = (ev: PlannerMapPointerEvent) => handleMapPointerDown(ev);
  pointerMoveHandler = (ev: PlannerMapPointerEvent) => handleMapPointerMove(ev);
  pointerUpHandler = (ev: PlannerMapPointerEvent) => handleMapPointerUp(ev);
  pointerCancelHandler = (ev: PlannerMapPointerEvent) => handleMapPointerCancel(ev);
  map.on('mousedown', pointerDownHandler);
  map.on('touchstart', pointerDownHandler);
  map.on('mousemove', pointerMoveHandler);
  map.on('touchmove', pointerMoveHandler);
  map.on('mouseup', pointerUpHandler);
  map.on('touchend', pointerUpHandler);
  map.on('touchcancel', pointerCancelHandler);
  keydownHandler = (ev: KeyboardEvent) => handlePlannerKeyDown(ev);
  window.addEventListener('keydown', keydownHandler);

  // Viewport updates
  moveHandler = () => emitViewport();
  map.on('moveend', moveHandler);

  // Watch planner state to re-sync sources.
  unwatchRoute = watch(
    () => planner.routeCoordinates.value,
    () => syncSources(),
    { deep: true }
  );
  unwatchWaypoints = watch(
    () => planner.waypoints.value,
    () => syncSources(),
    { deep: true }
  );
}

function detachFromMap() {
  const map = attachedMap.value;
  if (unwatchRoute) {
    unwatchRoute();
    unwatchRoute = null;
  }
  if (unwatchWaypoints) {
    unwatchWaypoints();
    unwatchWaypoints = null;
  }
  finishPlannerInteraction(false);
  setMapGestureDragEnabled(true);
  removeSelectedWaypointDeleteMarker();
  if (hoverMarker.value) {
    hoverMarker.value.remove();
    hoverMarker.value = null;
  }
  if (keydownHandler) {
    window.removeEventListener('keydown', keydownHandler);
    keydownHandler = null;
  }

  if (!map) {
    attachedMap.value = null;
    return;
  }
  if (clickHandler) {
    map.off('click', clickHandler);
    clickHandler = null;
  }
  if (pointerDownHandler) {
    map.off('mousedown', pointerDownHandler);
    map.off('touchstart', pointerDownHandler);
    pointerDownHandler = null;
  }
  if (pointerMoveHandler) {
    map.off('mousemove', pointerMoveHandler);
    map.off('touchmove', pointerMoveHandler);
    pointerMoveHandler = null;
  }
  if (pointerUpHandler) {
    map.off('mouseup', pointerUpHandler);
    map.off('touchend', pointerUpHandler);
    pointerUpHandler = null;
  }
  if (pointerCancelHandler) {
    map.off('touchcancel', pointerCancelHandler);
    pointerCancelHandler = null;
  }
  if (moveHandler) {
    map.off('moveend', moveHandler);
    moveHandler = null;
  }
  try {
    if (map.getLayer(PLANNER_ROUTE_HIT_LAYER_ID)) map.removeLayer(PLANNER_ROUTE_HIT_LAYER_ID);
    if (map.getLayer(PLANNER_LAYER_ID)) map.removeLayer(PLANNER_LAYER_ID);
    if (map.getSource(PLANNER_SOURCE_ID)) map.removeSource(PLANNER_SOURCE_ID);
    if (map.getLayer(PLANNER_WAYPOINT_HIT_LAYER_ID)) map.removeLayer(PLANNER_WAYPOINT_HIT_LAYER_ID);
    if (map.getLayer(PLANNER_WAYPOINT_LAYER_ID)) map.removeLayer(PLANNER_WAYPOINT_LAYER_ID);
    if (map.getLayer(PLANNER_WAYPOINT_DRAG_RING_LAYER_ID)) map.removeLayer(PLANNER_WAYPOINT_DRAG_RING_LAYER_ID);
    if (map.getSource(PLANNER_WAYPOINT_SOURCE_ID)) map.removeSource(PLANNER_WAYPOINT_SOURCE_ID);
  } catch {
    // Map may already be torn down on theme reload — safe to ignore
  }
  attachedMap.value = null;
}

function routeFeature() {
  return {
    type: 'FeatureCollection',
    features: routeFeatureCoordinatesByLeg().map((leg: RouteCoordinate[], legIndex: number) => ({
      type: 'Feature',
      properties: { legIndex },
      geometry: {
        type: 'LineString',
        coordinates: leg.map((c: RouteCoordinate) => [c[0], c[1]]),
      },
    })),
  };
}

function routeFeatureCoordinatesByLeg(): RouteCoordinate[][] {
  return routeLegCoordinateGroups(planner.legs.value);
}

function waypointFeature() {
  const activeWaypointId = plannerInteraction.value?.waypointId ?? null;
  const draftLngLat =
    plannerInteraction.value?.mode === 'waypointDragging' ? plannerInteraction.value.lastLngLat : null;
  return {
    type: 'FeatureCollection',
    features: planner.waypoints.value.map((w: Waypoint, index: number) => {
      const selected = w.id === selectedWaypointId.value;
      const active = w.id === activeWaypointId || selected;
      const lngLat = active && draftLngLat ? draftLngLat : w;
      return {
        type: 'Feature',
        properties: {
          id: w.id,
          index,
          active,
          selected,
          dragging: active && plannerInteraction.value?.mode === 'waypointDragging',
        },
        geometry: { type: 'Point', coordinates: [lngLat.lng, lngLat.lat] },
      };
    }),
  };
}

function syncSources() {
  const map = attachedMap.value;
  if (!map) return;
  const routeSrc = map.getSource(PLANNER_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
  if (routeSrc) routeSrc.setData(routeFeature() as never);
  const wpSrc = map.getSource(PLANNER_WAYPOINT_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
  if (wpSrc) wpSrc.setData(waypointFeature() as never);
  syncSelectedWaypointDeleteMarker();
}

function syncSelectedWaypointDeleteMarker() {
  const map = attachedMap.value;
  const selectedWaypoint = selectedWaypointId.value
    ? planner.waypoints.value.find((waypoint: Waypoint) => waypoint.id === selectedWaypointId.value)
    : null;

  if (!map || !selectedWaypoint || plannerInteraction.value?.mode === 'waypointDragging') {
    removeSelectedWaypointDeleteMarker();
    return;
  }

  if (!selectedWaypointDeleteMarker.value) {
    const el = document.createElement('button');
    el.type = 'button';
    el.className = 'planner-waypoint-delete-marker';
    el.title = 'Delete waypoint';
    el.setAttribute('aria-label', 'Delete selected waypoint');
    el.innerHTML = '<i class="bi bi-x-lg" aria-hidden="true"></i>';

    const stopMapEvent = (ev: Event) => {
      ev.preventDefault();
      ev.stopPropagation();
    };
    const deleteFromMarker = (ev: Event) => {
      stopMapEvent(ev);
      suppressNextMapClick(TOUCH_SYNTHETIC_CLICK_SUPPRESSION_MS);
      deleteSelectedWaypoint();
    };
    el.addEventListener('pointerdown', stopMapEvent);
    el.addEventListener('pointerup', deleteFromMarker);
    el.addEventListener('mousedown', stopMapEvent);
    el.addEventListener('touchstart', stopMapEvent, { passive: false });
    el.addEventListener('touchend', deleteFromMarker, { passive: false });
    el.addEventListener('click', deleteFromMarker);

    const MarkerCtor = maplibregl.Marker as unknown as new (options: Record<string, unknown>) => MapLike;
    selectedWaypointDeleteMarker.value = new MarkerCtor({
      element: el,
      anchor: 'left',
      offset: [14, -2],
    })
      .setLngLat([selectedWaypoint.lng, selectedWaypoint.lat])
      .addTo(map);
    return;
  }

  selectedWaypointDeleteMarker.value.setLngLat([selectedWaypoint.lng, selectedWaypoint.lat]);
}

function removeSelectedWaypointDeleteMarker() {
  if (!selectedWaypointDeleteMarker.value) return;
  selectedWaypointDeleteMarker.value.remove();
  selectedWaypointDeleteMarker.value = null;
}

function suppressNextMapClick(durationMs = MAP_CLICK_SUPPRESSION_MS, point: PlannerScreenPoint | null = null) {
  suppressMapClickUntil = Date.now() + durationMs;
  suppressMapClickPoint = point;
}

function isMapClickSuppressed(point: PlannerScreenPoint): boolean {
  if (Date.now() >= suppressMapClickUntil) return false;
  if (!suppressMapClickPoint) return true;
  return distancePx(point, suppressMapClickPoint) <= SYNTHETIC_CLICK_SUPPRESSION_RADIUS_PX;
}

function handleMapPointerDown(ev: PlannerMapPointerEvent) {
  if (!attachedMap.value || !ev.lngLat || !isPrimaryStartEvent(ev)) return;
  const pointerKind = pointerKindForEvent(ev);
  const point = pointFromEvent(ev);
  const waypointHit = queryWaypointHit(point, pointerKind);

  if (waypointHit) {
    finishPlannerInteraction(false);
    preventPlannerEvent(ev);
    suppressNextMapClick(clickSuppressionFor(pointerKind), point);
    setMapGestureDragEnabled(false);
    selectedWaypointId.value = waypointHit.id;
    plannerInteraction.value = {
      mode: 'waypointPointerDown',
      pointerKind,
      startPoint: point,
      lastLngLat: lngLatFromEvent(ev),
      waypointId: waypointHit.id,
      touchId: touchIdentifierForEvent(ev) ?? undefined,
    };
    syncSources();
    return;
  }
}

function handleMapPointerMove(ev: PlannerMapPointerEvent) {
  const interaction = plannerInteraction.value;
  if (!interaction || !eventMatchesInteraction(ev, interaction) || !ev.lngLat) return;
  const point = pointFromEvent(ev);
  const movedPx = distancePx(point, interaction.startPoint);
  const thresholdPx = dragThresholdFor(interaction.pointerKind);

  preventPlannerEvent(ev);
  suppressNextMapClick(clickSuppressionFor(interaction.pointerKind), point);

  if (interaction.mode === 'waypointPointerDown' && movedPx < thresholdPx) return;

  plannerInteraction.value = {
    ...interaction,
    mode: 'waypointDragging',
    lastLngLat: lngLatFromEvent(ev),
  };
  syncSources();
}

function handleMapPointerUp(ev: PlannerMapPointerEvent) {
  const interaction = plannerInteraction.value;
  if (!interaction || !eventMatchesInteraction(ev, interaction)) return;

  preventPlannerEvent(ev);
  suppressNextMapClick(clickSuppressionFor(interaction.pointerKind), pointFromEvent(ev));

  if (interaction.mode === 'waypointDragging') {
    const lngLat = ev.lngLat ? lngLatFromEvent(ev) : interaction.lastLngLat;
    finishPlannerInteraction(true, lngLat);
    return;
  }

  finishPlannerInteraction(false);
}

function handleMapPointerCancel(ev: PlannerMapPointerEvent) {
  const interaction = plannerInteraction.value;
  if (!interaction || !eventMatchesInteraction(ev, interaction)) return;
  preventPlannerEvent(ev);
  suppressNextMapClick(clickSuppressionFor(interaction.pointerKind), pointFromEvent(ev));
  finishPlannerInteraction(false);
}

function finishPlannerInteraction(commit: boolean, lngLat?: PlannerLngLat) {
  const interaction = plannerInteraction.value;
  if (!interaction) return;
  const waypointId = interaction.mode === 'waypointDragging' ? interaction.waypointId : undefined;
  const finalLngLat = lngLat ?? interaction.lastLngLat;

  if (commit && waypointId) {
    planner.moveWaypoint(waypointId, finalLngLat.lat, finalLngLat.lng);
  }

  plannerInteraction.value = null;
  setMapGestureDragEnabled(true);
  syncSources();
}

function queryWaypointHit(point: PlannerScreenPoint, pointerKind: PlannerPointerKind): { id: string } | null {
  const map = attachedMap.value;
  if (!map?.getLayer?.(PLANNER_WAYPOINT_HIT_LAYER_ID)) return null;
  const hits = map.queryRenderedFeatures(point, { layers: [PLANNER_WAYPOINT_HIT_LAYER_ID] }) as Array<{
    properties?: { id?: unknown };
  }>;
  const hitRadiusPx = waypointHitRadiusFor(pointerKind);
  let best: { id: string; distancePx: number } | null = null;

  for (const hit of hits) {
    const id = typeof hit.properties?.id === 'string' ? hit.properties.id : '';
    const waypoint = planner.waypoints.value.find((w: Waypoint) => w.id === id);
    if (!waypoint) continue;
    const projected = map.project([waypoint.lng, waypoint.lat]) as PlannerScreenPoint;
    const waypointDistancePx = distancePx(point, projected);
    if (waypointDistancePx > hitRadiusPx) continue;
    if (!best || waypointDistancePx < best.distancePx) best = { id, distancePx: waypointDistancePx };
  }

  return best ? { id: best.id } : null;
}

function inspectRouteHit(point: PlannerScreenPoint, pointerKind: PlannerPointerKind): RouteHitDebug {
  const map = attachedMap.value;
  const insertRadiusPx = routeInsertRadiusFor(pointerKind);
  const emptyDebug: RouteHitDebug = {
    rawHitCount: 0,
    candidates: [],
    selectedLegIndex: null,
    insertRadiusPx,
  };
  if (!map?.getLayer?.(PLANNER_ROUTE_HIT_LAYER_ID)) return emptyDebug;
  const routeLegs = routeFeatureCoordinatesByLeg();
  const hits = map.queryRenderedFeatures(point, { layers: [PLANNER_ROUTE_HIT_LAYER_ID] }) as RouteHitFeature[];
  const candidateIndexes = Array.from(
    new Set(
      hits
        .map((hit) => Number(hit.properties?.legIndex))
        .filter((legIndex) => Number.isInteger(legIndex) && legIndex >= 0 && legIndex < routeLegs.length)
    )
  );
  const project = (coordinate: RouteCoordinate) => map.project([coordinate[0], coordinate[1]]) as PlannerScreenPoint;
  const selectedLegIndex = nearestRouteLegIndexFromCandidates(
    point,
    candidateIndexes,
    routeLegs,
    project,
    insertRadiusPx
  );
  return {
    rawHitCount: hits.length,
    candidates: candidateIndexes.map((legIndex) => {
      const coordinates = routeLegs[legIndex];
      const first = coordinates[0] ?? null;
      const last = coordinates[coordinates.length - 1] ?? null;
      return {
        legIndex,
        distancePx: Math.sqrt(distanceToRouteLegSquared(point, coordinates, project)),
        start: first ? [first[1], first[0]] : null,
        end: last ? [last[1], last[0]] : null,
      };
    }),
    selectedLegIndex,
    insertRadiusPx,
  };
}

function pointerKindForEvent(ev: PlannerMapPointerEvent): PlannerPointerKind {
  const original = ev.originalEvent as MouseEvent | TouchEvent | undefined;
  return original && 'changedTouches' in original ? 'touch' : 'mouse';
}

function isPrimaryStartEvent(ev: PlannerMapPointerEvent): boolean {
  const original = ev.originalEvent as MouseEvent | TouchEvent | undefined;
  if (!original) return true;
  if ('button' in original && original.button !== 0) return false;
  if ('touches' in original && original.touches.length !== 1) return false;
  return true;
}

function eventMatchesInteraction(ev: PlannerMapPointerEvent, interaction: PlannerInteraction): boolean {
  if (pointerKindForEvent(ev) !== interaction.pointerKind) return false;
  if (interaction.pointerKind === 'mouse' || interaction.touchId === undefined) return true;
  const original = ev.originalEvent as TouchEvent | undefined;
  if (!original || !('changedTouches' in original)) return false;
  return Boolean(
    touchById(original.changedTouches, interaction.touchId) ?? touchById(original.touches, interaction.touchId)
  );
}

function touchIdentifierForEvent(ev: PlannerMapPointerEvent): number | null {
  const original = ev.originalEvent as TouchEvent | undefined;
  if (!original || !('changedTouches' in original)) return null;
  return original.changedTouches.item(0)?.identifier ?? original.touches.item(0)?.identifier ?? null;
}

function touchById(touches: TouchList | undefined, id: number): Touch | null {
  if (!touches) return null;
  for (let index = 0; index < touches.length; index++) {
    const touch = touches.item(index);
    if (touch?.identifier === id) return touch;
  }
  return null;
}

function pointFromEvent(ev: PlannerMapPointerEvent): PlannerScreenPoint {
  const point = ev.point as PlannerScreenPoint;
  return { x: point.x, y: point.y };
}

function lngLatFromEvent(ev: PlannerMapPointerEvent): PlannerLngLat {
  return { lng: ev.lngLat.lng, lat: ev.lngLat.lat };
}

function distancePx(a: PlannerScreenPoint, b: PlannerScreenPoint): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function dragThresholdFor(pointerKind: PlannerPointerKind): number {
  return pointerKind === 'touch' ? WAYPOINT_TOUCH_DRAG_THRESHOLD_PX : WAYPOINT_MOUSE_DRAG_THRESHOLD_PX;
}

function waypointHitRadiusFor(pointerKind: PlannerPointerKind): number {
  return pointerKind === 'touch' ? WAYPOINT_TOUCH_HIT_RADIUS_PX : WAYPOINT_MOUSE_HIT_RADIUS_PX;
}

function routeInsertRadiusFor(pointerKind: PlannerPointerKind): number {
  return pointerKind === 'touch' ? ROUTE_TOUCH_INSERT_RADIUS_PX : ROUTE_MOUSE_INSERT_RADIUS_PX;
}

function clickSuppressionFor(pointerKind: PlannerPointerKind): number {
  return pointerKind === 'touch' ? TOUCH_SYNTHETIC_CLICK_SUPPRESSION_MS : MAP_CLICK_SUPPRESSION_MS;
}

function preventPlannerEvent(ev: PlannerMapPointerEvent) {
  ev.preventDefault();
  ev.originalEvent?.preventDefault?.();
  ev.originalEvent?.stopPropagation?.();
}

function setMapGestureDragEnabled(enabled: boolean) {
  const map = attachedMap.value;
  if (!map) return;
  const method = enabled ? 'enable' : 'disable';
  map.dragPan?.[method]?.();
  map.touchZoomRotate?.[method]?.();
}

function emitViewport() {
  const map = attachedMap.value;
  if (!map) return;
  const b = map.getBounds();
  const sw = b.getSouthWest();
  const ne = b.getNorthEast();
  planner.setViewport(sw.lat, sw.lng, ne.lat, ne.lng);
}

function handleMapClick(ev: maplibregl.MapMouseEvent) {
  if (!attachedMap.value || plannerInteraction.value) return;
  const pointerKind = pointerKindForEvent(ev);
  const point = pointFromEvent(ev);
  const suppressed = isMapClickSuppressed(point);
  const waypointHit = suppressed ? null : queryWaypointHit(point, pointerKind);
  const routeHit =
    !suppressed && !waypointHit && !planner.viewportTooLarge.value ? inspectRouteHit(point, pointerKind) : null;
  const waypointsBefore = planner.waypoints.value.map((w: Waypoint, index: number) => ({
    index,
    lat: w.lat,
    lng: w.lng,
  }));
  let action: 'suppressed' | 'waypoint-hit' | 'blocked-viewport' | 'route-insert' | 'append' = 'append';

  if (suppressed) {
    action = 'suppressed';
  } else if (waypointHit) {
    action = 'waypoint-hit';
  } else if (planner.viewportTooLarge.value) {
    action = 'blocked-viewport';
  } else if (routeHit?.selectedLegIndex !== null && routeHit?.selectedLegIndex !== undefined) {
    action = 'route-insert';
  }

  debugPlannerInteraction('click', {
    action,
    pointerKind,
    screenPoint: point,
    lngLat: { lat: ev.lngLat.lat, lng: ev.lngLat.lng },
    suppressed,
    suppressMapClickUntil: suppressMapClickUntil,
    suppressMapClickPoint: suppressMapClickPoint,
    waypointHit,
    routeHit,
    viewportTooLarge: planner.viewportTooLarge.value,
    routeLegCount: routeFeatureCoordinatesByLeg().length,
    waypointCountBefore: planner.waypoints.value.length,
    waypointsBefore,
  });

  if (suppressed) return;
  if (waypointHit) {
    selectedWaypointId.value = waypointHit.id;
    suppressNextMapClick(clickSuppressionFor(pointerKind), point);
    syncSources();
    return;
  }
  if (planner.viewportTooLarge.value) return;
  const routeLegIndex = routeHit?.selectedLegIndex ?? null;
  if (routeLegIndex !== null) {
    selectedWaypointId.value = null;
    planner.insertWaypoint(routeLegIndex, ev.lngLat.lat, ev.lngLat.lng);
    debugPlannerInteraction('click-result', {
      action,
      insertedAfterLegIndex: routeLegIndex,
      waypointCountAfter: planner.waypoints.value.length,
      waypointsAfter: planner.waypoints.value.map((w: Waypoint, index: number) => ({ index, lat: w.lat, lng: w.lng })),
    });
    return;
  }
  selectedWaypointId.value = null;
  planner.addWaypoint(ev.lngLat.lat, ev.lngLat.lng);
  debugPlannerInteraction('click-result', {
    action,
    waypointCountAfter: planner.waypoints.value.length,
    waypointsAfter: planner.waypoints.value.map((w: Waypoint, index: number) => ({ index, lat: w.lat, lng: w.lng })),
  });
}

function debugPlannerInteraction(label: string, payload: Record<string, unknown>) {
  if (!PLANNER_INTERACTION_DEBUG) return;
  console.info(`[planner-interaction] ${label}`, payload);
}

function deleteSelectedWaypoint() {
  const waypointId = selectedWaypointId.value;
  if (!waypointId || !planner.waypoints.value.some((waypoint: Waypoint) => waypoint.id === waypointId)) return;
  planner.removeWaypoint(waypointId);
  selectedWaypointId.value = null;
  finishPlannerInteraction(false);
  syncSources();
}

function clearRoute() {
  selectedWaypointId.value = null;
  planner.clearAll();
  finishPlannerInteraction(false);
  syncSources();
}

function handlePlannerKeyDown(ev: KeyboardEvent) {
  if (!active.value || !hasSelectedWaypoint.value) return;
  if (ev.key !== 'Backspace' && ev.key !== 'Delete') return;
  if (isEditableKeyboardTarget(ev.target)) return;
  ev.preventDefault();
  deleteSelectedWaypoint();
}

function isEditableKeyboardTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tagName = target.tagName.toLowerCase();
  return tagName === 'input' || tagName === 'textarea' || tagName === 'select' || target.isContentEditable;
}

function onElevationHover(point: { lng: number; lat: number; elevationM: number; distanceM: number } | null) {
  const map = attachedMap.value;
  if (!map) return;
  if (!point) {
    if (hoverMarker.value) {
      hoverMarker.value.remove();
      hoverMarker.value = null;
    }
    return;
  }
  if (!hoverMarker.value) {
    const el = document.createElement('div');
    el.className = 'planner-hover-marker';
    const marker = new maplibregl.Marker({ element: el, anchor: 'center' });
    marker.setLngLat([point.lng, point.lat]);
    marker.addTo(map as unknown as maplibregl.Map);
    hoverMarker.value = marker;
  } else {
    hoverMarker.value.setLngLat([point.lng, point.lat]);
  }
}
watch(
  () => props.map,
  (newMap, oldMap) => {
    if (active.value && oldMap && attachedMap.value === oldMap) {
      detachFromMap();
    }
    if (active.value && newMap) {
      attachToMap();
    }
  }
);

watch(brouterExpanded, (expanded) => {
  if (expanded) installBrouterOutsideClickHandler();
  else removeBrouterOutsideClickHandler();
});

watch(deleteDialogVisible, (visible) => {
  if (visible || deletingPlanId.value !== null) return;
  planPendingDelete.value = null;
  planDeleteError.value = '';
});

onBeforeUnmount(() => {
  clearRouteUpdateHideTimer();
  removeBrouterOutsideClickHandler();
  detachFromMap();
});

defineExpose({
  isOpen,
  toggle,
  close,
  saveCurrent,
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
  transition:
    background 0.15s,
    color 0.15s;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* ── Controls row ──────────────────────────────────────────────── */
.planner-controls-row {
  display: grid;
  grid-template-columns: max-content max-content minmax(13rem, 1fr);
  align-items: center;
  column-gap: clamp(0.6rem, 1.8vw, 1.5rem);
  row-gap: 0.55rem;
}
.planner-control-group {
  display: flex;
  align-items: center;
  min-width: 0;
}
.planner-control-group--profile {
  justify-content: flex-start;
}
.planner-control-group--hint {
  justify-content: center;
}
.planner-control-group--actions {
  justify-content: center;
}
.planner-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
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
  transition:
    background 0.12s,
    color 0.12s;
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
  to {
    transform: rotate(360deg);
  }
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
.planner-load-error {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--error) 28%, var(--border-default));
  border-radius: 10px;
  background: var(--error-bg);
  color: var(--error);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.planner-load-error i {
  flex-shrink: 0;
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
  align-items: stretch;
  gap: 0.45rem;
  padding: 0.25rem;
  border-radius: 10px;
  border: 1px solid var(--border-default);
  background: var(--accent-bg);
  transition: border-color 0.12s;
}
.planner-plan-item:hover {
  border-color: var(--accent-muted);
}
.planner-plan-open {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1 1 auto;
  min-width: 0;
  padding: 0.45rem 0.55rem;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.12s;
}
.planner-plan-open:hover {
  background: var(--accent-subtle);
}
.planner-plan-open:disabled {
  opacity: 0.65;
  cursor: wait;
}
.planner-plan-open:focus-visible,
.planner-plan-action:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
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
  min-width: 0;
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
.planner-plan-actions {
  display: flex;
  align-items: stretch;
  gap: 0.35rem;
  flex-shrink: 0;
}
.planner-plan-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-height: 2.75rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-glass-heavy);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.12s,
    color 0.12s,
    border-color 0.12s;
  flex-shrink: 0;
}
.planner-plan-action:disabled {
  opacity: 0.65;
  cursor: wait;
}
.planner-plan-export {
  width: 4.8rem;
  border-color: var(--accent);
  color: var(--accent-text);
}
.planner-plan-export:hover:not(:disabled) {
  background: var(--accent-subtle);
}
.planner-plan-delete {
  width: 2.75rem;
  border-color: color-mix(in srgb, var(--error) 34%, var(--border-default));
  color: var(--error);
  background: color-mix(in srgb, var(--error-bg) 74%, var(--surface-glass-heavy));
}
.planner-plan-delete:hover:not(:disabled) {
  background: var(--error-bg);
  border-color: var(--error);
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
}
.brouter-pill {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  width: var(--bs-btn-size, 2.4rem);
  height: var(--bs-btn-size, 2.4rem);
  padding: 0;
  border-radius: 50%;
  border: 1px solid var(--border-default);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  background: var(--surface-glass-subtle);
  cursor: pointer;
  overflow: visible;
}
.brouter-pill::after {
  content: '';
  position: absolute;
  inset: -0.55rem;
}
.brouter-pill:hover {
  background: var(--surface-glass-heavy);
}
.brouter-pill--danger:hover {
  background: color-mix(in srgb, var(--error) 16%, var(--surface-glass-heavy));
}
.brouter-pill--icon {
  color: var(--text-muted);
  font-size: var(--bs-btn-fs-icon, var(--text-xs-size));
}
.brouter-icon {
  position: relative;
  z-index: 1;
}
.brouter-pill--ok {
  color: var(--success);
  border-color: color-mix(in srgb, var(--success) 26%, var(--border-default));
  background: color-mix(in srgb, var(--success) 7%, var(--surface-glass-subtle));
}
.brouter-pill--warn {
  color: var(--warning);
  border-color: color-mix(in srgb, var(--warning) 35%, var(--border-default));
  background: color-mix(in srgb, var(--warning) 9%, var(--surface-glass-subtle));
}
.brouter-pill--checking {
  color: var(--text-muted);
  border-color: var(--border-default);
  background: var(--surface-glass-subtle);
}
.brouter-pill--danger {
  color: var(--error);
  border-color: color-mix(in srgb, var(--error) 70%, var(--border-default));
  background: color-mix(in srgb, var(--error) 13%, var(--surface-glass-heavy));
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--error) 22%, transparent),
    0 0.45rem 1rem color-mix(in srgb, var(--error) 22%, transparent);
  animation: brouter-danger-pulse 1.35s ease-in-out infinite;
}
.brouter-pill--updating {
  color: var(--accent-text);
  border-color: color-mix(in srgb, var(--accent) 42%, var(--border-default));
  background: color-mix(in srgb, var(--accent) 10%, var(--surface-glass-subtle));
}
.brouter-pill--updating .brouter-icon {
  animation: planner-cloud-pulse 0.95s ease-in-out infinite;
}
@keyframes planner-cloud-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.72;
  }
  50% {
    transform: scale(1.12);
    opacity: 1;
  }
}
@keyframes brouter-danger-pulse {
  0%,
  100% {
    transform: scale(1);
    box-shadow:
      0 0 0 2px color-mix(in srgb, var(--error) 20%, transparent),
      0 0.45rem 1rem color-mix(in srgb, var(--error) 20%, transparent);
  }
  50% {
    transform: scale(1.08);
    box-shadow:
      0 0 0 5px color-mix(in srgb, var(--error) 13%, transparent),
      0 0.55rem 1.2rem color-mix(in srgb, var(--error) 27%, transparent);
  }
}
.brouter-badge {
  position: absolute;
  top: -0.4rem;
  right: -0.45rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1rem;
  height: 1rem;
  background: var(--warning);
  color: var(--text-inverse);
  border-radius: 999px;
  padding: 0 0.25rem;
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 700;
  z-index: 3;
}
.brouter-badge--danger {
  background: var(--error);
  color: var(--text-inverse);
  border: 2px solid var(--surface-glass-heavy);
}
.brouter-detail {
  position: absolute;
  right: 0;
  top: calc(100% + 0.7rem);
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
.brouter-detail--header {
  width: min(18rem, calc(100vw - 1.5rem));
}
.brouter-detail-summary {
  margin: 0 0 0.55rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
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
.planner-delete-copy {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.planner-delete-copy strong {
  color: var(--text-primary);
  font-weight: 700;
}

@media (max-width: 960px) {
  .planner-controls-row {
    grid-template-columns: max-content max-content minmax(0, 1fr);
  }
  .planner-control-group--actions {
    grid-column: 2;
    grid-row: 1;
  }
  .planner-control-group--hint {
    grid-column: 3;
    grid-row: 1;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .planner-panel {
    padding-inline: 0.75rem;
    padding-bottom: 0.85rem;
  }
  .planner-controls-row {
    grid-template-columns: minmax(0, 1fr) max-content;
    align-items: center;
  }
  .planner-control-group--profile {
    grid-column: 1;
    grid-row: 1;
  }
  .planner-control-group--actions {
    grid-column: 2;
    grid-row: 1;
    justify-content: flex-end;
  }
  .planner-control-group--hint {
    grid-column: 1 / -1;
    grid-row: 2;
  }
  .planner-plan-item {
    gap: 0.35rem;
  }
  .planner-plan-open {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }
  .planner-plan-date {
    align-self: flex-start;
  }
  .planner-plan-actions {
    gap: 0.3rem;
  }
  .planner-plan-export {
    width: 4.2rem;
  }
  .planner-plan-delete {
    width: 2.65rem;
  }
  .brouter-detail {
    right: -0.1rem;
  }
}

@media (max-width: 420px) {
  .planner-actions {
    gap: 0.2rem;
  }
  .planner-action-btn {
    width: 2.25rem;
    height: 2.25rem;
  }
}

@media (max-width: 360px) {
  .planner-control-group--actions {
    grid-column: 1 / -1;
    grid-row: 2;
    justify-content: center;
  }
  .planner-control-group--hint {
    grid-column: 1 / -1;
    grid-row: 3;
  }
}
</style>

<style>
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

.planner-waypoint-delete-marker {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 1px solid color-mix(in srgb, #dc2626 42%, var(--border-default));
  border-radius: 999px;
  background: color-mix(in srgb, #ffffff 92%, #fee2e2);
  color: #dc2626;
  box-shadow: 0 0.25rem 0.8rem rgba(15, 23, 42, 0.2);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.planner-waypoint-delete-marker::after {
  content: '';
  position: absolute;
  inset: -0.45rem;
  border-radius: 999px;
}
.planner-waypoint-delete-marker:hover {
  background: #fee2e2;
  border-color: #dc2626;
}
.planner-waypoint-delete-marker i {
  font-size: 0.72rem;
  line-height: 1;
}
</style>
