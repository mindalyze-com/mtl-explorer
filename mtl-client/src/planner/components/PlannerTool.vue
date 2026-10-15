<template>
  <BottomSheet
    ref="sheet"
    v-model="active"
    :icon="NAVIGATION_TOOL_ICONS.planner"
    :detents="sectionDetents"
    initial-detent="hub"
    :content-key="activeTab"
    :fit-small-overflow="false"
    share-layout
    :drag-to-close="activeTab !== 'draw'"
    :free-resize="activeTab === 'draw'"
    :no-scroll-hint="activeTab === 'draw' && panel.summaryLayout.value.essentialActionsOnly"
    :header-mode="activeTab === 'draw' ? 'compact' : 'default'"
    :back-label="activeTab === 'hub' ? '' : 'Back to Planner'"
    :no-backdrop="true"
    sheet-class="sheet--solid-over-map planner-sheet"
    @back="showHub"
    @closed="onSheetClosed"
    @layout-change="panel.onLayoutChange"
    @detent-change="panel.onDetentChange"
    @resize-end="panel.onResizeEnd"
  >
    <template #title>
      <div class="planner-header-nav">
        <i
          v-if="activeTab === 'hub'"
          :class="[NAVIGATION_TOOL_ICONS.planner, 'planner-sheet-icon']"
          aria-hidden="true"
        ></i>
        <h2 ref="sectionTitle" class="panel-title" tabindex="-1">{{ sectionTitleText }}</h2>
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
              class="brouter-download-failures"
            >
              <span class="brouter-detail-label">Failed</span>
              <p v-for="(reason, segment) in status.segmentsFailed" :key="segment" class="brouter-val--warn">
                {{ segment }}: {{ reason }}
              </p>
            </div>
          </div>
          <div v-else class="brouter-detail-body">
            <span class="brouter-val--warn">Unavailable: {{ status.reason ?? 'unknown' }}</span>
          </div>
          <button type="button" class="brouter-detail-refresh" @click.stop="refresh(true)">
            <i class="bi bi-arrow-clockwise"></i>
            <span>Refresh</span>
          </button>
        </div>
      </div>
    </template>
    <template v-if="active && activeTab === 'draw'" #size-action="{ maximized, restore }">
      <button
        ref="sizeButton"
        type="button"
        class="planner-size-button"
        :title="panel.reduced.value ? 'Show all planning tools' : 'Reduce the panel to see more map'"
        @click.stop="togglePanelSize(maximized, restore)"
      >
        <i :class="panel.reduced.value ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true"></i>
        <span>{{ panel.reduced.value ? 'Show tools' : 'More map' }}</span>
      </button>
    </template>
    <template v-if="active && activeTab === 'draw'" #pinned>
      <div v-show="panel.reduced.value" ref="reducedSummaryHost" class="planner-pinned"></div>
      <span class="planner-sr" aria-live="polite">{{ panelAnnouncement }}</span>
      <Teleport to="body">
        <div v-if="panelTip" class="planner-panel-tip" :style="panelTip.style" role="status">
          <span>{{ panelTip.text }}</span>
          <button type="button" class="planner-panel-tip__dismiss" aria-label="Dismiss tip" @click="panel.dismissTip">
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        </div>
      </Teleport>
    </template>
    <div v-if="active" class="planner-root">
      <div
        v-if="activeTab === 'hub' || activeTab === 'guide'"
        class="planner-entry-scroll sheet-scroll-target"
        data-sheet-fit-target
      >
        <PlannerHub ref="plannerHub" :has-draft="hasPlanContext" :guide="activeTab === 'guide'" @open="openSection" />
      </div>
      <div
        v-else-if="activeTab === 'draw'"
        v-show="!panel.reduced.value"
        ref="expandedSummaryHost"
        :inert="panel.reduced.value || undefined"
        class="planner-panel planner-panel--draw"
      ></div>

      <div
        v-else-if="activeTab === 'load'"
        class="planner-panel planner-panel--load sheet-scroll-target"
        data-sheet-fit-target
      >
        <PrimeButton
          v-if="hasPlanContext"
          severity="secondary"
          outlined
          class="planner-return-draft"
          @click="openSection('draw')"
          >Continue current plan{{ hasUnsavedChanges ? ' · Unsaved changes' : '' }}</PrimeButton
        >
        <div v-if="plansLoading" class="planner-load-spinner">
          <i class="bi bi-arrow-repeat planner-load-spin"></i> Loading saved plans…
        </div>
        <div v-else-if="plansLoadError" class="inline-notice planner-load-error" role="alert">
          <i class="bi bi-exclamation-circle" aria-hidden="true"></i>
          <span>{{ plansLoadError }}</span>
          <PrimeButton label="Retry" text @click="switchToLoad" />
        </div>
        <div v-else-if="savedPlans.length === 0" class="empty-placeholder planner-load-empty">
          <i class="bi bi-inbox"></i>
          <span>No saved plans yet. Add a route or points of interest, then save your plan.</span>
          <PrimeButton :label="hasPlanContext ? 'Continue planning' : 'Start planning'" @click="openSection('draw')" />
        </div>
        <template v-else>
          <div v-if="planActionError" class="inline-notice planner-load-error" role="alert">
            <i class="bi bi-exclamation-circle"></i>
            <span>{{ planActionError }}</span>
            <PrimeButton
              v-if="failedPlanId !== null"
              label="Retry"
              text
              :disabled="loadingPlanId !== null || deletingPlanId !== null"
              @click="retryOpenPlan"
            />
          </div>
          <label class="planner-field"
            >Search saved plans<InputText v-model="planSearch" type="search" placeholder="Name or description"
          /></label>
          <p v-if="!filteredPlans.length" class="planner-subtitle">No plans match your search.</p>
          <ul class="planner-plan-list" :aria-busy="loadingPlanId !== null">
            <li v-for="plan in filteredPlans" :key="plan.id" class="planner-plan-item">
              <button
                type="button"
                class="planner-plan-open"
                :disabled="deletingPlanId !== null || loadingPlanId !== null"
                @click="selectPlan(plan.id)"
              >
                <span class="planner-plan-body">
                  <span class="planner-plan-name"
                    >{{ plan.name }}<span v-if="loadingPlanId === plan.id"> · Opening…</span></span
                  >
                  <span class="planner-plan-meta">
                    <template v-if="plan.hasRoute"
                      ><i class="bi bi-rulers"></i> {{ formatDistanceSmart(plan.distanceM) }}</template
                    >
                    <span v-if="plan.pointOfInterestCount"
                      >{{ plan.pointOfInterestCount }} POI{{ plan.pointOfInterestCount === 1 ? '' : 's' }}</span
                    >
                    <span v-if="plan.description" class="planner-plan-desc">· {{ plan.description }}</span>
                  </span>
                </span>
                <span class="planner-plan-date">{{ formatDate(plan.createDate) }}</span>
              </button>
              <span class="planner-plan-actions">
                <button
                  type="button"
                  class="planner-plan-action planner-plan-edit"
                  :disabled="deletingPlanId !== null || loadingPlanId !== null"
                  :title="`Continue editing ${plan.name || 'plan'}`"
                  :aria-label="`Edit ${plan.name || 'plan'}`"
                  @click.stop="selectPlan(plan.id)"
                >
                  <i class="bi bi-pencil" aria-hidden="true" /><span>Edit</span>
                </button>
                <button
                  v-if="plan.hasRoute"
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

    <Teleport v-if="active && activeTab === 'draw' && summaryHost" :to="summaryHost">
      <div ref="summaryEl" class="planner-feedback-location">
        <PlannerSummary
          :density="panel.density.value"
          :wide="panel.wide.value"
          :result="routeResult"
          :show-stats="editMode === 'route' || routeResult !== null"
          :hint="editMode === 'poi' ? poiHint : drawingHint"
          :updating="routeUpdating"
          :error="planner.lastError.value || poiError || null"
          :can-retry="Boolean(planner.lastError.value)"
          :viewport-too-large="editMode === 'route' && planner.viewportTooLarge.value"
          :retry-disabled="planner.computing.value"
          :can-undo="planner.canUndo.value"
          :selection-action="selectionAction"
          :stats-density="panel.summaryLayout.value.statsDensity"
          :profile-visible="editMode === 'route' && panel.summaryLayout.value.profileVisible"
          :profile-requested="editMode === 'route'"
          :content-sized="panel.desktop.value"
          :essential-actions-only="panel.summaryLayout.value.essentialActionsOnly"
          :text-scale="panel.summaryLayout.value.textScale"
          @undo="undoEdit"
          @retry="planner.recomputeNow"
          @remove-selection="removeSelection"
          @hover="onElevationHover"
          @metrics="panel.setSummaryMetrics"
        >
          <template #controls>
            <div
              ref="drawPanel"
              :inert="panel.reduced.value || undefined"
              class="planner-drawing-tools sheet-scroll-target"
              aria-label="Planning controls"
            >
              <div :class="['planner-workspace', { 'planner-workspace--poi': editMode === 'poi' }]">
                <div
                  ref="modeSwitcher"
                  class="planner-edit-mode"
                  role="group"
                  aria-label="Planning mode"
                  @keydown="onModeKeyDown"
                >
                  <PrimeButton
                    class="planner-mode-button"
                    :class="{ 'is-active': editMode === 'route' }"
                    data-plan-mode="route"
                    severity="secondary"
                    text
                    :aria-pressed="editMode === 'route'"
                    :tabindex="editMode === 'route' ? 0 : -1"
                    @click="setEditMode('route')"
                  >
                    <i :class="NAVIGATION_TOOL_ICONS.planner" aria-hidden="true" />
                    <span class="planner-mode-label">Draw route</span>
                  </PrimeButton>
                  <PrimeButton
                    class="planner-mode-button"
                    :class="{ 'is-active': editMode === 'poi' }"
                    data-plan-mode="poi"
                    severity="secondary"
                    text
                    :aria-pressed="editMode === 'poi'"
                    :tabindex="editMode === 'poi' ? 0 : -1"
                    @click="setEditMode('poi')"
                  >
                    <i class="bi bi-geo-alt" aria-hidden="true" />
                    <span class="planner-mode-label">Points of interest</span>
                  </PrimeButton>
                </div>
                <div v-if="editMode === 'route'" class="planner-routing-controls">
                  <span class="planner-setting-label">Travel mode</span>
                  <PlannerToolbar
                    :profiles="profiles"
                    :profile="planner.profile.value"
                    label="Travel mode"
                    @profile-changed="planner.setProfile"
                  />
                </div>
                <PrimeButton
                  class="planner-save-button"
                  :outlined="!hasUnsavedChanges"
                  :disabled="!canSave"
                  :aria-label="`${saveActionLabel}: ${routeStatusText}`"
                  :title="currentPlan ? `${currentPlan.name} · ${routeStatusText}` : routeStatusText"
                  @click="openSaveDialog"
                >
                  <i
                    :class="currentPlan && !hasUnsavedChanges ? 'bi bi-check2' : 'bi bi-floppy'"
                    aria-hidden="true"
                  />{{ saveActionLabel }}
                  <span v-if="hasUnsavedChanges" class="planner-unsaved-dot" aria-hidden="true" />
                </PrimeButton>
                <div class="planner-utility-actions" role="group" aria-label="Plan actions">
                  <div class="planner-action-group" role="group" aria-label="Edit history">
                    <PrimeButton
                      severity="secondary"
                      text
                      class="planner-history-button"
                      :disabled="!planner.canUndo.value"
                      title="Undo (Ctrl/⌘ Z)"
                      aria-label="Undo"
                      @click="undoEdit"
                    >
                      <i class="bi bi-arrow-counterclockwise" aria-hidden="true" />
                    </PrimeButton>
                    <PrimeButton
                      severity="secondary"
                      text
                      class="planner-history-button"
                      :disabled="!planner.canRedo.value"
                      title="Redo (Ctrl/⌘ Shift Z)"
                      aria-label="Redo"
                      @click="redoEdit"
                    >
                      <i class="bi bi-arrow-clockwise" aria-hidden="true" />
                    </PrimeButton>
                  </div>
                  <div
                    v-if="editMode === 'route'"
                    class="planner-action-group planner-context-actions"
                    role="group"
                    aria-label="Route actions"
                  >
                    <PrimeButton
                      severity="secondary"
                      text
                      :aria-expanded="routeToolsVisible"
                      aria-label="Route tools"
                      title="Route tools: reverse and clear"
                      aria-haspopup="menu"
                      @click="toggleRouteTools"
                    >
                      <i class="bi bi-sliders" aria-hidden="true" />
                      <span>Tools</span><i class="bi bi-chevron-down planner-tool-chevron" aria-hidden="true" />
                    </PrimeButton>
                  </div>
                  <div class="planner-action-group" role="group" aria-label="Map view">
                    <PrimeButton
                      severity="secondary"
                      text
                      class="planner-history-button"
                      aria-label="Fit plan"
                      title="Fit route and points of interest on map"
                      :disabled="!hasPlanContent"
                      @click="fitRoute"
                    >
                      <i class="bi bi-arrows-fullscreen" aria-hidden="true" />
                    </PrimeButton>
                  </div>
                </div>
              </div>
              <p v-if="editMode === 'route'" class="planner-subtitle">{{ drawingHint }}</p>
              <section v-if="editMode === 'poi'" class="planner-poi-tools" aria-label="Points of interest in this plan">
                <p class="planner-subtitle" role="status">{{ poiHint }}</p>
                <div v-if="selectedPoi" class="planner-poi-selected">
                  <strong
                    ><i :class="`bi bi-${poiIconOption(selectedPoi.iconType).icon}`" aria-hidden="true" />
                    {{ selectedPoi.name }}</strong
                  >
                  <p v-if="selectedPoi.notes" class="planner-poi-notes">{{ selectedPoi.notes }}</p>
                  <div class="planner-route-actions">
                    <PrimeButton
                      label="Edit POI"
                      severity="secondary"
                      outlined
                      @click="poiDraft = { ...selectedPoi }"
                    />
                    <PrimeButton
                      :label="movePoiActive ? 'Cancel move' : 'Move POI'"
                      severity="secondary"
                      outlined
                      :aria-pressed="movePoiActive"
                      @click="movePoiActive = !movePoiActive"
                    />
                    <PrimeButton label="Delete POI" severity="danger" outlined @click="deleteSelectedPoi" />
                    <PrimeButton label="Done" severity="secondary" text @click="deselectPoi" />
                  </div>
                </div>
                <ul
                  v-if="planner.pointsOfInterest.value.length"
                  class="planner-poi-list"
                  aria-label="Plan points of interest"
                >
                  <li v-for="point in planner.pointsOfInterest.value" :key="point.id">
                    <button type="button" :aria-pressed="selectedPoiId === point.id" @click="selectPoi(point.id)">
                      <i :class="`bi bi-${poiIconOption(point.iconType).icon}`" aria-hidden="true" /><span>{{
                        point.name
                      }}</span>
                    </button>
                  </li>
                </ul>
                <p class="planner-subtitle">Saved with this plan. Not included in GPX exports.</p>
              </section>
            </div>
          </template>
        </PlannerSummary>
      </div>
    </Teleport>

    <span v-if="drawingActive && editMode === 'route'" :id="mapKeyboardHelpId" class="planner-sr">
      Route planning: Alt+Left and Alt+Right select points. Enter opens point actions.
    </span>
    <PlannerActionMenu
      v-if="drawingActive && editMode === 'route' && pointMenuVisible && hasSelectedWaypoint"
      class="planner-point-menu"
      :title="waypointLabel(selectedWaypointIndex)"
      :actions="pointMenuActions"
      :hint="pointDragHint"
      :message="movePointActive ? 'Tap the map at the new position.' : undefined"
      :anchor="pointMenuAnchor"
      :bounds="pointMenuBounds"
      :fallback-bounds="pointMenuFallbackBounds"
      keep-anchor-visible
      @action="onPointMenuAction"
      @dismiss="dismissPointMenu"
      @dismiss-hint="dismissPointDragHint"
    />
    <PlannerActionMenu
      v-if="drawingActive && editMode === 'route' && routeToolsVisible"
      class="planner-route-menu"
      title="Route tools"
      :actions="routeMenuActions"
      :anchor="routeMenuAnchor"
      :bounds="routeMenuBounds"
      :trigger="routeMenuTrigger"
      @action="onRouteMenuAction"
      @dismiss="dismissRouteMenu"
    />

    <!-- Save dialog -->
    <PrimeDialog
      v-model:visible="saveDialogVisible"
      :modal="true"
      :header="currentPlan ? 'Save a copy' : 'Save plan'"
      :style="{ width: 'min(440px, 92vw)' }"
      :draggable="false"
      :dismissable-mask="!saveSubmitting"
      :closable="!saveSubmitting"
      :close-on-escape="!saveSubmitting"
      class="planner-dialog"
      :close-button-props="{ autofocus: false }"
      :pt="{ transition: { onAfterEnter: focusDialogInput } }"
    >
      <div class="planner-dialog-body">
        <p v-if="currentPlan" class="planner-subtitle">
          Saves a separate plan. “{{ currentPlan.name }}” stays in your saved plans.
        </p>
        <div v-if="saveError" class="inline-notice planner-load-error" role="alert">{{ saveError }}</div>
        <label class="planner-field">
          <span>Name</span>
          <InputText
            v-model="saveName"
            :disabled="saveSubmitting"
            maxlength="255"
            placeholder="e.g. Saturday loop via Uetliberg"
            @keyup.enter="confirmSave"
          />
        </label>
        <label class="planner-field">
          <span>Description <em>(optional)</em></span>
          <PrimeTextarea
            v-model="saveDescription"
            :disabled="saveSubmitting"
            rows="2"
            auto-resize
            placeholder="Notes for future you…"
          />
        </label>
        <div class="planner-dialog-meta">
          <template v-if="planner.routeCoordinates.value.length">
            <span><i class="bi bi-rulers"></i> {{ formatDistanceSmart(planner.stats.value.distanceM) }}</span>
            <span><i class="bi bi-arrow-up-right"></i> {{ formatElevation(planner.stats.value.ascentM) }}</span>
            <span><i class="bi bi-arrow-down-right"></i> {{ formatElevation(planner.stats.value.descentM) }}</span>
          </template>
          <span v-if="planner.waypoints.value.length"
            ><i class="bi bi-pin-map"></i> {{ planner.waypoints.value.length }} /
            {{ planner.maxWaypoints.value }} waypoints</span
          >
          <span v-if="planner.pointsOfInterest.value.length"
            ><i class="bi bi-geo-alt" /> {{ planner.pointsOfInterest.value.length }} POI{{
              planner.pointsOfInterest.value.length === 1 ? '' : 's'
            }}</span
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
          :label="saveSubmitting ? 'Saving…' : currentPlan ? 'Save copy' : 'Save plan'"
          icon="pi pi-save"
          :disabled="!saveName.trim() || !canSave"
          @click="confirmSave"
        />
      </template>
    </PrimeDialog>

    <PrimeDialog
      v-model:visible="replaceDialogVisible"
      modal
      header="Open saved plan?"
      :style="{ width: 'min(440px, 92vw)' }"
      :draggable="false"
    >
      <p>Your current plan has unsaved changes. Save it before opening another plan, or replace it.</p>
      <template #footer>
        <PrimeButton
          label="Keep editing"
          severity="secondary"
          text
          @click="
            replaceDialogVisible = false;
            openSection('draw');
          "
        />
        <PrimeButton label="Replace plan" severity="secondary" @click="confirmReplacePlan" />
        <PrimeButton label="Save first" :disabled="!canSave" @click="saveBeforeReplace" />
      </template>
    </PrimeDialog>

    <!-- Delete confirmation dialog -->
    <PrimeDialog
      v-model:visible="deleteDialogVisible"
      :modal="true"
      header="Delete saved plan"
      :style="{ width: 'min(420px, 92vw)' }"
      :draggable="false"
      :dismissable-mask="deletingPlanId === null"
      :closable="deletingPlanId === null"
      class="planner-dialog"
    >
      <div class="planner-dialog-body">
        <p class="planner-delete-copy">
          Delete <strong>{{ planPendingDelete?.name || 'this saved plan' }}</strong> from saved plans?
        </p>
        <div v-if="planPendingDelete" class="planner-dialog-meta">
          <span><i class="bi bi-rulers"></i> {{ formatDistanceSmart(planPendingDelete.distanceM) }}</span>
          <span><i class="bi bi-calendar3"></i> {{ formatDate(planPendingDelete.createDate) }}</span>
        </div>
        <div v-if="planDeleteError" class="inline-notice planner-load-error" role="alert">
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
          :label="deletingPlanId !== null ? 'Deleting…' : 'Delete plan'"
          icon="pi pi-trash"
          severity="danger"
          :disabled="!planPendingDelete || deletingPlanId !== null"
          @click="confirmDeletePlan"
        />
      </template>
    </PrimeDialog>
    <PlannerPoiEditor :point="poiDraft" @save="confirmPoi" @cancel="poiDraft = null" />
  </BottomSheet>
</template>

<script setup lang="ts">
import { warnSanitized } from '@/utils/safeLogging';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';

import { computed, inject, nextTick, onBeforeUnmount, ref, shallowRef, useId, watch } from 'vue';
import type { ToastService } from '@/types/ui';
import * as maplibregl from 'maplibre-gl';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { NAVIGATION_TOOL_ICONS } from '@/components/ui/navigationToolIcons';
import PlannerToolbar from '@/planner/components/PlannerToolbar.vue';
import PlannerPoiEditor from '@/planner/components/PlannerPoiEditor.vue';
import { poiIconOption, poiIconImageId } from '@/planner/utils/plannerPoiIconTypes';
import { installPlannerPoiIcons } from '@/planner/utils/plannerPoiIcons';
import PlannerHub, { type PlannerSection } from '@/planner/components/PlannerHub.vue';
import PlannerActionMenu, {
  type PlannerMenuAction,
  type PlannerMenuBounds,
  type PlannerMenuDismissal,
} from '@/planner/components/PlannerActionMenu.vue';
import PlannerSummary, { type PlannerRouteResult } from '@/planner/components/PlannerSummary.vue';
import { usePlannerPanelLayout } from '@/planner/composables/usePlannerPanelLayout';
import PrimeDialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import { focusDialogInput } from '@/utils/dialogFocus';
import { mapLabelFonts } from '@/utils/mapLabelFonts';
import PrimeTextarea from 'primevue/textarea';
import PrimeButton from 'primevue/button';
import { usePlannerState } from '@/planner/composables/usePlannerState';
import { useBRouterSegmentStatus } from '@/planner/composables/useBRouterSegmentStatus';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import {
  deletePlannedTrack,
  downloadPlannedTrackGpx,
  fetchPlannerConfig,
  savePlannedRoute,
  listPlannedTracks,
  loadPlannedTrack,
} from '@/planner/repositories/plannerRepository';
import type { PlannedTrackSummary, PointOfInterest, Waypoint } from '@/planner/types';
import { formatDistanceSmart, formatElevation } from '@/utils/Utils';
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
  PLANNER_POI_SOURCE_ID,
  PLANNER_POI_LAYER_ID,
  PLANNER_POI_ICON_LAYER_ID,
  PLANNER_POI_LABEL_LAYER_ID,
  PLANNER_POI_COLOR,
  PLANNER_POI_LABEL_OFFSET_EM,
  PLANNER_POI_LABEL_HALO_WIDTH_PX,
  PLANNER_TIP_OFFSET_PX,
  PLANNER_WORKSPACE_HEIGHT,
  PLANNER_SAVE_CONFIRMATION_MS,
  PLANNER_WAYPOINT_DRAG_RING_LAYER_ID,
  PLANNER_WAYPOINT_HIT_LAYER_ID,
  PLANNER_WAYPOINT_LAYER_ID,
  PLANNER_WAYPOINT_LABEL_LAYER_ID,
  WAYPOINT_LABEL_SIZE_PX,
  WAYPOINT_START_COLOR,
  WAYPOINT_FINISH_COLOR,
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
type PlannerInteractionMode =
  'waypointPointerDown' | 'waypointDragging' | 'routePointerDown' | 'poiPointerDown' | 'poiDragging';
type PlannerScreenPoint = { x: number; y: number };
type PlannerLngLat = { lng: number; lat: number };
type PlannerInteraction = {
  mode: PlannerInteractionMode;
  pointerKind: PlannerPointerKind;
  startPoint: PlannerScreenPoint;
  lastLngLat: PlannerLngLat;
  routeLegIndex?: number;
  waypointId?: string;
  poiId?: string;
  touchId?: number;
  dragOffset?: PlannerScreenPoint;
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
// All sections keep one workspace. Content and measurement changes do not resize it.
const sectionDetents = computed(() => {
  const preferences = panel.preferences.value;
  const savedSize = panel.sizes.value.find((size) => size.id === preferences.size);
  const height = preferences.heightFraction
    ? `${preferences.heightFraction * 100}dvh`
    : savedSize
      ? `${savedSize.heightPx}px`
      : PLANNER_WORKSPACE_HEIGHT;
  return [{ id: 'hub', height }, ...panel.detents.value];
});
const PLANNER_INTERACTION_DEBUG = import.meta.env.DEV;
/** Keep four touch actions usable when the expanded sheet leaves very little map visible. */
const POINT_MENU_MIN_MAP_HEIGHT_PX = 256;

const props = defineProps<{
  map?: MapLike;
}>();
const toast = inject<ToastService>('toast', { add: () => undefined });

const emit = defineEmits<{
  (event: 'active-changed', active: boolean): void;
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
}>();

defineOptions({ name: 'PlannerTool' });

const brouterPanel = ref<HTMLElement | null>(null);
const applicationStatusStore = useApplicationStatusStore();
const planner = usePlannerState({
  onPrewarmComplete: () => void applicationStatusStore.force(APPLICATION_STATUS_SECTION.SERVICES),
});
// `active` lives here so we can gate BRouter status polling on it —
// no point hitting /api/planner/status while the planner sheet is closed.
const active = ref(false);
const routeUpdateVisible = ref(false);
const { status, refresh } = useBRouterSegmentStatus(active);
let routeUpdateShownAt = 0;
let routeUpdateHideTimer: number | undefined;

const profiles = ref<string[]>(['trekking']);
const activeTab = ref<'hub' | PlannerSection>('hub');
const plannerHub = ref<InstanceType<typeof PlannerHub> | null>(null);
const sectionTitle = ref<HTMLElement | null>(null);
const sectionTitleText = computed(() => {
  if (activeTab.value === 'draw') return 'Plan a map';
  if (activeTab.value === 'load') return 'Saved plans';
  if (activeTab.value === 'guide') return 'Use the map as your guide';
  return 'Planner';
});
const drawingActive = computed(() => active.value && activeTab.value === 'draw');
const editMode = ref<'route' | 'poi'>('route');
const modeSwitcher = ref<HTMLElement | null>(null);
const selectedPoiId = ref<string | null>(null);
const movePoiActive = ref(false);
const poiDraft = ref<PointOfInterest | null>(null);
const poiError = ref('');
const selectedPoi = computed(() => planner.pointsOfInterest.value.find((point) => point.id === selectedPoiId.value));
const hasPlanContent = computed(() => planner.waypoints.value.length > 0 || planner.pointsOfInterest.value.length > 0);
const poiHint = computed(() =>
  movePoiActive.value
    ? 'Tap the map to move this POI.'
    : `Tap the map to add a POI. Select or drag a POI to edit. ${planner.pointsOfInterest.value.length} / ${planner.maxPointsOfInterest.value} POIs.`
);
const savedPlans = ref<PlannedTrackSummary[]>([]);
const plansLoading = ref(false);
const plansLoadError = ref('');
const exportingPlanId = ref<number | null>(null);
const deletingPlanId = ref<number | null>(null);
const planActionError = ref('');
const failedPlanId = ref<number | null>(null);
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
let menuMoveHandler: (() => void) | null = null;
let contextMenuHandler: ((event: maplibregl.MapMouseEvent) => void) | null = null;
let mapLoadHandler: (() => void) | null = null;
const hoverMarker = shallowRef<MapLike | null>(null);
const plannerInteraction = ref<PlannerInteraction | null>(null);
const selectedWaypointId = ref<string | null>(null);
const mapKeyboardHelpId = useId();
let restoreMapKeyboardHelp: (() => void) | null = null;
let suppressMapClickUntil = 0;
let suppressMapClickPoint: PlannerScreenPoint | null = null;
let unwatchRoute: null | (() => void) = null;
let unwatchWaypoints: null | (() => void) = null;
let unwatchPois: null | (() => void) = null;
let brouterOutsideClickHandler: ((ev: PointerEvent) => void) | null = null;
const configLoaded = ref(false);
const brouterExpanded = ref(false);
const sheet = ref<{ snapTo(id: string): boolean; resizeTo(heightPx: number): boolean } | null>(null);
const summaryEl = ref<HTMLElement | null>(null);
const reducedSummaryHost = ref<HTMLElement | null>(null);
const expandedSummaryHost = ref<HTMLElement | null>(null);
const sizeButton = ref<HTMLButtonElement | null>(null);
const drawPanel = ref<HTMLElement | null>(null);
/** Last complete result, kept while a new route is pending so totals and profile change together. */
const routeResult = shallowRef<PlannerRouteResult | null>(null);
const panel = usePlannerPanelLayout({
  sheet,
  summaryEl,
  drawing: drawingActive,
  hasRoute: computed(() => planner.routeReady.value || routeResult.value !== null),
});
const summaryHost = computed(() => (panel.reduced.value ? reducedSummaryHost.value : expandedSummaryHost.value));
const panelAnnouncement = ref('');
let announcedDensity = panel.density.value;

watch(
  () =>
    [
      planner.routeReady.value,
      planner.stats.value,
      planner.routeCoordinates.value,
      planner.waypoints.value.length,
    ] as const,
  ([ready, stats, coordinates, waypointCount]) => {
    if (waypointCount < 2) routeResult.value = null;
    else if (ready) routeResult.value = { stats, coordinates };
  },
  { immediate: true }
);

watch([panel.density, () => panel.layout.value?.dragging], ([density, dragging]) => {
  if (dragging || !drawingActive.value || density === announcedDensity) return;
  announcedDensity = density;
  panelAnnouncement.value =
    density === 'full'
      ? 'Panel shows all planning tools.'
      : density === 'compact'
        ? 'Panel shows totals and elevation profile.'
        : 'Panel shows totals only.';
});

// Runs before the tools hide so focus never falls back to the page.
watch(panel.reduced, (reduced) => {
  if (reduced && drawPanel.value?.contains(document.activeElement)) sizeButton.value?.focus();
});

const routeUpdating = computed(() => planner.computing.value && planner.waypoints.value.length >= 2);
const selectionAction = computed(() => {
  if (editMode.value === 'route') return null;
  if (editMode.value === 'poi' && selectedPoi.value) return 'Delete POI';
  return null;
});
const panelTip = computed(() => {
  const layout = panel.layout.value;
  const tip = panel.activeTip.value;
  if (!tip || !drawingActive.value || !layout?.open || layout.dragging) return null;
  return {
    text: tip === 'reduce' ? 'More map space? Try compact.' : 'Tap Show tools to return.',
    style: {
      bottom: `${Math.max(0, window.innerHeight - layout.topPx) + PLANNER_TIP_OFFSET_PX}px`,
      right: `${Math.max(0, window.innerWidth - layout.rightPx) + PLANNER_TIP_OFFSET_PX}px`,
      maxWidth: `${Math.max(0, layout.widthPx - 2 * PLANNER_TIP_OFFSET_PX)}px`,
    },
  };
});

function togglePanelSize(maximized: boolean, restoreMaximized: () => void) {
  if (maximized) restoreMaximized();
  if (panel.reduced.value) panel.showTools();
  else panel.showMoreMap();
}

function removeSelection() {
  if (editMode.value === 'poi') deleteSelectedPoi();
  else deleteSelectedWaypoint();
}

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

const brouterDownloadFailureCount = computed(
  () => Object.values(status.value?.segmentsFailed ?? {}).filter((reason) => !reason.startsWith('HTTP 404:')).length
);
const brouterNeedsAttention = computed(() => {
  const s = status.value;
  return Boolean(s && (!s.available || !s.brouterRunning || brouterDownloadFailureCount.value > 0));
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
  if (brouterNeedsAttention.value) return 'brouter-pill--danger';
  if ((s.segmentsQueued ?? 0) > 0 || (s.segmentsInProgress?.length ?? 0) > 0) return 'brouter-pill--warn';
  return 'brouter-pill--ok';
});
const brouterStatusLabel = computed(() => {
  const s = status.value;
  if (!s) return 'BRouter status: checking';
  if (!s.available) return `BRouter status: unavailable${s.reason ? ` (${s.reason})` : ''}`;
  if (!s.brouterRunning) return 'BRouter status: stopped';
  if (brouterDownloadFailureCount.value > 0) {
    return `BRouter status: ${brouterDownloadFailureCount.value} routing-data download(s) failed`;
  }
  if (routeUpdateVisible.value) return 'BRouter status: updating route';
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
  if (!s.available) return s.reason || 'Start the BRouter sidecar, then refresh.';
  if (!s.brouterRunning) return 'Start BRouter in the backend stack, then refresh.';
  if (brouterDownloadFailureCount.value > 0) {
    return 'Routing data could not be downloaded. Check the failures below, then retry the route.';
  }
  if ((s.segmentsQueued ?? 0) > 0 || (s.segmentsInProgress?.length ?? 0) > 0) {
    return 'Preparing map segments for route calculation.';
  }
  return 'Calculates routes from your waypoints.';
});
const routeToolsVisible = ref(false);
const pointMenuVisible = ref(false);
const pointMenuAnchor = ref({ x: 0, y: 0 });
const pointMenuBounds = ref<PlannerMenuBounds>({
  left: 0,
  top: 0,
  right: window.innerWidth,
  bottom: window.innerHeight,
});
const pointMenuFallbackBounds = ref<PlannerMenuBounds>({ ...pointMenuBounds.value });
const routeMenuTrigger = ref<HTMLElement | null>(null);
const routeMenuAnchor = ref({ x: 0, y: 0 });
const movePointActive = ref(false);
const pointDragHintDone = ref(readJsonStorage(STORAGE_KEYS.plannerPointDragHintDone, false, (value) => value === true));
const pointDragHint = computed(() =>
  pointDragHintDone.value || movePointActive.value ? undefined : 'Drag this point to move it.'
);
const currentPlan = ref<{ id: number; name: string; description: string } | null>(null);
const hasPlanContext = computed(
  () => hasPlanContent.value || currentPlan.value !== null || planner.canUndo.value || planner.canRedo.value
);
const savedFingerprint = ref('');
const saveError = ref('');
const loadingPlanId = ref<number | null>(null);
const planSearch = ref('');
const replaceDialogVisible = ref(false);
const pendingPlanId = ref<number | null>(null);
const openAfterSaveId = ref<number | null>(null);
const routeFingerprint = computed(() =>
  JSON.stringify({
    profile: planner.profile.value,
    waypoints: planner.waypoints.value.map(({ lat, lng }) => ({ lat, lng })),
    coordinates: planner.routeCoordinates.value,
    pointsOfInterest: planner.pointsOfInterest.value,
  })
);
const hasUnsavedChanges = computed(() =>
  savedFingerprint.value ? routeFingerprint.value !== savedFingerprint.value : hasPlanContent.value
);
const canSave = computed(
  () =>
    (planner.routeReady.value || (planner.pointsOfInterest.value.length > 0 && planner.waypoints.value.length < 2)) &&
    !planner.computing.value &&
    !saveSubmitting.value &&
    !plannerInteraction.value &&
    !movePointActive.value &&
    !movePoiActive.value &&
    !poiDraft.value
);
const saveActionLabel = computed(() => (currentPlan.value ? 'Save copy' : 'Save plan'));
const routeStatusText = computed(() =>
  planner.computing.value
    ? 'Updating route…'
    : planner.lastError.value
      ? 'Route needs attention'
      : hasUnsavedChanges.value
        ? 'Unsaved changes'
        : currentPlan.value
          ? 'Saved plan'
          : 'Not saved yet'
);
const drawingHint = computed(() =>
  !planner.waypoints.value.length
    ? 'Tap the map to place your start point.'
    : planner.waypoints.value.length === 1
      ? 'Tap the map to add your next point.'
      : 'Tap to extend · Drag points to move · Tap points for actions.'
);
const filteredPlans = computed(() => {
  const query = planSearch.value.trim().toLocaleLowerCase();
  return savedPlans.value.filter((plan) =>
    `${plan.name} ${plan.description || ''}`.toLocaleLowerCase().includes(query)
  );
});
const selectedWaypointIndex = computed(() =>
  planner.waypoints.value.findIndex((waypoint) => waypoint.id === selectedWaypointId.value)
);
const canTrimSelected = computed(
  () => selectedWaypointIndex.value > 0 && selectedWaypointIndex.value < planner.waypoints.value.length - 1
);
const pointMenuActions = computed<PlannerMenuAction[]>(() =>
  movePointActive.value
    ? [{ id: 'cancel-move', label: 'Cancel move', icon: 'bi bi-x-lg' }]
    : [
        ...(canTrimSelected.value
          ? [
              { id: 'start', label: 'Start route here', icon: 'bi bi-flag' },
              { id: 'end', label: 'End route here', icon: 'bi bi-flag-fill' },
            ]
          : []),
        { id: 'move', label: 'Move to…', icon: 'bi bi-arrows-move' },
        { id: 'remove', label: 'Remove point', icon: 'bi bi-trash', destructive: true },
      ]
);
const routeMenuActions = computed<PlannerMenuAction[]>(() => [
  { id: 'reverse', label: 'Reverse route', icon: 'bi bi-arrow-down-up', disabled: planner.waypoints.value.length < 2 },
  {
    id: 'clear',
    label: 'Clear route',
    icon: 'bi bi-trash',
    destructive: true,
    disabled: !planner.waypoints.value.length,
  },
]);
const routeMenuBounds = computed<PlannerMenuBounds>(() => ({
  left: 0,
  top: 0,
  right: Math.max(panel.layout.value?.rightPx ?? 0, window.innerWidth),
  bottom: panel.layout.value?.bottomPx ?? window.innerHeight,
}));
const hasSelectedWaypoint = computed(() => {
  return Boolean(
    selectedWaypointId.value && planner.waypoints.value.some((waypoint) => waypoint.id === selectedWaypointId.value)
  );
});

function isOpen(): boolean {
  return active.value;
}

async function toggle() {
  if (active.value) close();
  else await open();
}

async function open() {
  if (active.value) return;
  activeTab.value = 'hub';
  active.value = true;
  emit('tool-opened');
  if (!configLoaded.value) {
    await loadConfig();
    configLoaded.value = true;
  }
}

function close() {
  if (!active.value) return;
  active.value = false;
  brouterExpanded.value = false;
}

function onSheetClosed() {
  emit('active-changed', false);
  emit('tool-closed');
  brouterExpanded.value = false;
  detachFromMap();
}

function openSection(section: PlannerSection): void {
  if (section === 'load') void switchToLoad();
  else activeTab.value = section;
}

async function showHub(): Promise<void> {
  const previousSection = activeTab.value;
  activeTab.value = 'hub';
  await nextTick();
  plannerHub.value?.focusItem(previousSection);
}

watch(activeTab, async (section) => {
  brouterExpanded.value = false;
  deselectWaypoint();
  deselectPoi();
  if (section === 'hub') return;
  await nextTick();
  sectionTitle.value?.focus({ preventScroll: true });
});

// ── Config / plans loading ─────────────────────────────────────
async function loadConfig() {
  try {
    const cfg = await fetchPlannerConfig();
    profiles.value = cfg.profiles.length ? cfg.profiles : ['trekking'];
    planner.setMaxWaypoints(cfg.maxWaypoints);
    planner.setMaxPointsOfInterest(cfg.maxPointsOfInterest);
    if (!profiles.value.includes(planner.profile.value)) {
      planner.setProfile(cfg.defaultProfile);
    }
  } catch (e) {
    warnSanitized('[planner] failed to load config', e, {
      topic1: 'planner',
      topic2: 'PlannerTool',
      topic3: 'loadConfig',
    });
  }
}

async function saveCurrent() {
  // Legacy entry point — kept for any external callers; opens the proper dialog.
  openSaveDialog();
}

function openSaveDialog() {
  if (!canSave.value) return;
  saveName.value = saveName.value || (currentPlan.value ? `${currentPlan.value.name} (copy)` : suggestedPlanName());
  saveDescription.value = saveDescription.value || currentPlan.value?.description || '';
  saveError.value = '';
  saveDialogVisible.value = true;
}

function suggestedPlanName(): string {
  const date = new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  return `Plan — ${date}`;
}

async function confirmSave() {
  const name = saveName.value.trim();
  if (!name || !canSave.value) return;
  const fingerprint = routeFingerprint.value;
  const description = saveDescription.value.trim();
  const payload = JSON.parse(
    JSON.stringify({
      name,
      description: description || undefined,
      profile: planner.profile.value,
      coordinates: planner.routeCoordinates.value,
      legs: planner.legs.value,
      stats: planner.stats.value,
      waypoints: planner.waypoints.value.map((w: Waypoint) => ({ lat: w.lat, lng: w.lng })),
      pointsOfInterest: planner.pointsOfInterest.value,
    })
  );
  saveSubmitting.value = true;
  saveError.value = '';
  try {
    const saved = await savePlannedRoute(payload);
    currentPlan.value = { id: saved.id, name, description };
    savedFingerprint.value = fingerprint;
    toast.add({ severity: 'success', summary: 'Plan saved', detail: name, life: PLANNER_SAVE_CONFIRMATION_MS });
    saveDialogVisible.value = false;
    saveName.value = '';
    saveDescription.value = '';
    const nextPlanId = openAfterSaveId.value;
    openAfterSaveId.value = null;
    if (nextPlanId !== null) await openPlan(nextPlanId);
  } catch (e) {
    warnSanitized('[planner] failed to save route', e, {
      topic1: 'planner',
      topic2: 'PlannerTool',
      topic3: 'confirmSave',
    });
    saveError.value = 'Could not save this plan. Your plan and entered details are still here. Please try again.';
  } finally {
    saveSubmitting.value = false;
  }
}

// ── Load plans ───────────────────────────────────────────────────
async function switchToLoad() {
  activeTab.value = 'load';
  if (plansLoading.value) return;
  plansLoading.value = true;
  plansLoadError.value = '';
  planActionError.value = '';
  failedPlanId.value = null;
  planDeleteError.value = '';
  try {
    savedPlans.value = await listPlannedTracks();
  } catch (e) {
    warnSanitized('[planner] failed to list saved plans', e, {
      topic1: 'planner',
      topic2: 'PlannerTool',
      topic3: 'switchToLoad',
    });
    plansLoadError.value = 'Could not load saved plans. Please try again.';
  } finally {
    plansLoading.value = false;
  }
}

async function exportPlanGpx(plan: PlannedTrackSummary) {
  planActionError.value = '';
  failedPlanId.value = null;
  exportingPlanId.value = plan.id;
  try {
    await downloadPlannedTrackGpx(plan.id, plan.name);
  } catch (e) {
    warnSanitized('[planner] failed to export plan as GPX', e, {
      topic1: 'planner',
      topic2: 'PlannerTool',
      topic3: 'exportPlanGpx',
    });
    planActionError.value = 'Could not export this route as GPX. Please try again.';
  } finally {
    exportingPlanId.value = null;
  }
}

function openDeleteDialog(plan: PlannedTrackSummary) {
  if (exportingPlanId.value !== null || deletingPlanId.value !== null) return;
  planPendingDelete.value = plan;
  planActionError.value = '';
  failedPlanId.value = null;
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
    if (currentPlan.value?.id === plan.id) {
      currentPlan.value = null;
      savedFingerprint.value = '';
      saveName.value = '';
      saveDescription.value = '';
    }
    deleteDialogVisible.value = false;
    planPendingDelete.value = null;
    planDeleteError.value = '';
  } catch (e) {
    warnSanitized('[planner] failed to delete plan', e, {
      topic1: 'planner',
      topic2: 'PlannerTool',
      topic3: 'confirmDeletePlan',
    });
    planDeleteError.value = 'Could not delete this saved plan. Please try again.';
  } finally {
    deletingPlanId.value = null;
  }
}

async function selectPlan(id: number) {
  if (loadingPlanId.value !== null) return;
  if (hasUnsavedChanges.value) {
    pendingPlanId.value = id;
    replaceDialogVisible.value = true;
    return;
  }
  await openPlan(id);
}

function confirmReplacePlan() {
  const id = pendingPlanId.value;
  replaceDialogVisible.value = false;
  pendingPlanId.value = null;
  if (id !== null) void openPlan(id);
}

function saveBeforeReplace() {
  openAfterSaveId.value = pendingPlanId.value;
  replaceDialogVisible.value = false;
  openSaveDialog();
}

function retryOpenPlan() {
  if (failedPlanId.value !== null && loadingPlanId.value === null) void openPlan(failedPlanId.value);
}

async function openPlan(id: number) {
  planActionError.value = '';
  failedPlanId.value = null;
  loadingPlanId.value = id;
  const fingerprint = routeFingerprint.value;
  try {
    const detail = await loadPlannedTrack(id);
    if (!active.value || activeTab.value !== 'load' || fingerprint !== routeFingerprint.value) return;
    deselectWaypoint();
    deselectPoi();
    routeResult.value = null;
    planner.loadPlan(detail);
    currentPlan.value = { id, name: detail.name || 'Saved route', description: detail.description || '' };
    savedFingerprint.value = routeFingerprint.value;
    saveName.value = '';
    saveDescription.value = '';
    routeToolsVisible.value = false;
    editMode.value = planner.waypoints.value.length ? 'route' : 'poi';
    activeTab.value = 'draw';
    await nextTick();
    fitRoute();
  } catch (e) {
    warnSanitized('[planner] failed to load plan', e, { topic1: 'planner', topic2: 'PlannerTool', topic3: 'openPlan' });
    failedPlanId.value = id;
    planActionError.value = 'Could not open this saved plan. Please try again.';
  } finally {
    loadingPlanId.value = null;
  }
}

function waypointLabel(index: number): string {
  if (index === 0) return '1 · Start';
  if (index === planner.waypoints.value.length - 1) return `${index + 1} · Finish`;
  return `${index + 1} · Waypoint`;
}

function updatePointMenuPosition() {
  if (!pointMenuVisible.value) return;
  const map = attachedMap.value;
  const waypoint = planner.waypoints.value.find((point) => point.id === selectedWaypointId.value);
  if (!map?.project || !waypoint) return;
  const canvas = map.getCanvas?.() as HTMLCanvasElement | undefined;
  const rect = canvas?.getBoundingClientRect();
  const left = rect?.left ?? 0;
  const top = rect?.top ?? 0;
  const projected = map.project([waypoint.lng, waypoint.lat]);
  pointMenuAnchor.value = { x: left + projected.x, y: top + projected.y };
  const bottom = Math.min(window.innerHeight, rect?.height ? rect.bottom : window.innerHeight);
  const sheetTop = panel.layout.value?.topPx ?? bottom;
  const menuBottom =
    sheetTop - top >= POINT_MENU_MIN_MAP_HEIGHT_PX * panel.summaryLayout.value.textScale
      ? Math.min(bottom, sheetTop)
      : bottom;
  pointMenuBounds.value = {
    left: Math.max(0, left),
    top: Math.max(0, top),
    right: Math.min(window.innerWidth, rect?.width ? rect.right : window.innerWidth),
    bottom: menuBottom,
  };
  pointMenuFallbackBounds.value = { ...pointMenuBounds.value, bottom };
}
function openPointMenu(id: string) {
  if (!planner.waypoints.value.some((point) => point.id === id)) return;
  selectedWaypointId.value = id;
  movePointActive.value = false;
  routeToolsVisible.value = false;
  pointMenuVisible.value = true;
  updatePointMenuPosition();
  syncSources();
}
function dismissPointMenu(detail: PlannerMenuDismissal) {
  // Map taps are handled below so dismissing cannot also insert a route point.
  if (detail.event?.target instanceof Node && attachedMap.value?.getCanvas?.().contains(detail.event.target)) return;
  deselectWaypoint();
  if (detail.restoreFocus) attachedMap.value?.getCanvas?.().focus();
}
function dismissPointDragHint() {
  if (pointDragHintDone.value) return;
  pointDragHintDone.value = true;
  writeJsonStorage(STORAGE_KEYS.plannerPointDragHintDone, true);
}
function onPointMenuAction(action: string) {
  if (action === 'move') {
    movePointActive.value = true;
    suppressMapClickUntil = 0;
    attachedMap.value?.getCanvas?.().focus();
    return;
  }
  if (action === 'cancel-move') deselectWaypoint();
  else if (action === 'remove') deleteSelectedWaypoint();
  else if (action === 'start') trimRoute('before');
  else if (action === 'end') trimRoute('after');
  attachedMap.value?.getCanvas?.().focus();
}
function toggleRouteTools(event: MouseEvent) {
  if (routeToolsVisible.value) {
    routeToolsVisible.value = false;
    return;
  }
  deselectWaypoint();
  routeMenuTrigger.value = event.currentTarget as HTMLElement;
  const rect = routeMenuTrigger.value.getBoundingClientRect();
  routeMenuAnchor.value = { x: rect.left, y: rect.bottom };
  routeToolsVisible.value = true;
}
function dismissRouteMenu(detail: PlannerMenuDismissal) {
  // Map handlers consume dismissal before the same tap can append or insert a point.
  if (detail.event?.target instanceof Node && attachedMap.value?.getCanvas?.().contains(detail.event.target)) return;
  routeToolsVisible.value = false;
  if (detail.restoreFocus) routeMenuTrigger.value?.focus();
}
function onRouteMenuAction(action: string) {
  routeToolsVisible.value = false;
  if (action === 'reverse') reverseRoute();
  else if (action === 'clear') clearRoute();
  routeMenuTrigger.value?.focus();
}

function deselectWaypoint() {
  pointMenuVisible.value = false;
  movePointActive.value = false;
  selectedWaypointId.value = null;
  finishPlannerInteraction(false);
  syncSources();
}

function reverseRoute() {
  deselectWaypoint();
  planner.reverseRoute();
}

function trimRoute(side: 'before' | 'after') {
  if (!selectedWaypointId.value) return;
  planner.trimRoute(selectedWaypointId.value, side);
  deselectWaypoint();
}

function undoEdit() {
  deselectWaypoint();
  deselectPoi();
  planner.undo();
}

function redoEdit() {
  deselectWaypoint();
  deselectPoi();
  planner.redo();
}

function onModeKeyDown(event: KeyboardEvent) {
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  let next: 'route' | 'poi';
  if (event.key === 'Home') next = 'route';
  else if (event.key === 'End') next = 'poi';
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') next = editMode.value === 'route' ? 'poi' : 'route';
  else return;
  event.preventDefault();
  event.stopPropagation();
  setEditMode(next);
  modeSwitcher.value?.querySelector<HTMLElement>(`[data-plan-mode="${next}"]`)?.focus();
}

function setEditMode(mode: 'route' | 'poi') {
  if (editMode.value === mode) return;
  deselectWaypoint();
  deselectPoi();
  editMode.value = mode;
  if (mode === 'poi') onElevationHover(null);
  routeToolsVisible.value = false;
}

function selectPoi(id: string) {
  selectedPoiId.value = id;
  movePoiActive.value = false;
  poiError.value = '';
  syncSources();
}

function deselectPoi() {
  selectedPoiId.value = null;
  movePoiActive.value = false;
  poiError.value = '';
  finishPlannerInteraction(false);
  syncSources();
}

function confirmPoi(point: PointOfInterest) {
  if (point.id) planner.updatePointOfInterest(point);
  else {
    const added = planner.addPointOfInterest(point.lat, point.lng, {
      name: point.name,
      iconType: point.iconType,
      notes: point.notes,
    });
    if (!added) {
      poiError.value = `POI limit reached (max ${planner.maxPointsOfInterest.value}).`;
      return;
    }
    selectedPoiId.value = added.id;
  }
  poiDraft.value = null;
  syncSources();
}

function deleteSelectedPoi() {
  if (selectedPoiId.value) planner.removePointOfInterest(selectedPoiId.value);
  deselectPoi();
}

const FIT_ROUTE_PADDING_PX = 48;
const DRAWING_PANEL_VIEWPORT_FRACTION = 0.4;
const FIT_ROUTE_MAX_ZOOM = 16;
function fitRoute() {
  const map = attachedMap.value;
  if (!map?.fitBounds || !hasPlanContent.value) return;
  const coordinates = planner.routeCoordinates.value.length
    ? planner.routeCoordinates.value
    : planner.waypoints.value.map((point) => [point.lng, point.lat]);
  const bounds = new maplibregl.LngLatBounds();
  coordinates.forEach((coordinate) => bounds.extend([coordinate[0], coordinate[1]]));
  planner.pointsOfInterest.value.forEach((point) => bounds.extend([point.lng, point.lat]));
  const height = map.getContainer().clientHeight;
  map.fitBounds(bounds, {
    padding: {
      top: FIT_ROUTE_PADDING_PX,
      left: FIT_ROUTE_PADDING_PX,
      right: FIT_ROUTE_PADDING_PX,
      bottom: height * DRAWING_PANEL_VIEWPORT_FRACTION + FIT_ROUTE_PADDING_PX,
    },
    maxZoom: FIT_ROUTE_MAX_ZOOM,
    retainPadding: false,
  });
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
function updateMapKeyboardHelp() {
  restoreMapKeyboardHelp?.();
  restoreMapKeyboardHelp = null;
  if (!drawingActive.value || editMode.value !== 'route') return;
  const canvas = attachedMap.value?.getCanvas?.() as HTMLCanvasElement | undefined;
  if (!canvas) return;
  const attributes = ['aria-describedby', 'aria-keyshortcuts'] as const;
  const previous = attributes.map((name) => canvas.getAttribute(name));
  canvas.setAttribute('aria-describedby', [previous[0], mapKeyboardHelpId].filter(Boolean).join(' '));
  canvas.setAttribute(
    'aria-keyshortcuts',
    [previous[1], 'Alt+ArrowLeft Alt+ArrowRight Enter'].filter(Boolean).join(' ')
  );
  restoreMapKeyboardHelp = () =>
    attributes.forEach((name, i) => {
      const value = previous[i];
      if (value === null) canvas.removeAttribute(name);
      else canvas.setAttribute(name, value);
    });
}
watch(editMode, updateMapKeyboardHelp);

function attachToMap() {
  const map = props.map;
  if (!map || !drawingActive.value) return;
  if (attachedMap.value === map) return;
  attachedMap.value = map;
  updateMapKeyboardHelp();

  const addLayers = () => {
    if (!drawingActive.value || attachedMap.value !== map) return;
    mapLoadHandler = null;
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
          'circle-stroke-color': [
            'case',
            ['==', ['get', 'index'], 0],
            WAYPOINT_START_COLOR,
            ['get', 'finish'],
            WAYPOINT_FINISH_COLOR,
            WAYPOINT_STROKE_COLOR,
          ],
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
    if (!map.getLayer(PLANNER_WAYPOINT_LABEL_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_WAYPOINT_LABEL_LAYER_ID,
        type: 'symbol',
        source: PLANNER_WAYPOINT_SOURCE_ID,
        layout: {
          'text-field': ['to-string', ['+', ['get', 'index'], 1]],
          'text-font': mapLabelFonts(map.getGlyphs()),
          'text-size': WAYPOINT_LABEL_SIZE_PX,
          'text-allow-overlap': true,
          'text-ignore-placement': true,
        },
        paint: { 'text-color': '#1f2937' },
      });
    }
    if (!map.getSource(PLANNER_POI_SOURCE_ID)) {
      map.addSource(PLANNER_POI_SOURCE_ID, { type: 'geojson', data: poiFeature() as never });
    }
    if (!map.getLayer(PLANNER_POI_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_POI_LAYER_ID,
        type: 'circle',
        source: PLANNER_POI_SOURCE_ID,
        paint: {
          'circle-radius': [
            'case',
            ['boolean', ['get', 'selected'], false],
            WAYPOINT_ACTIVE_RING_RADIUS_PX,
            WAYPOINT_RADIUS_PX,
          ],
          'circle-color': PLANNER_POI_COLOR,
          'circle-stroke-color': WAYPOINT_FILL_COLOR,
          'circle-stroke-width': WAYPOINT_STROKE_WIDTH_PX,
        },
      });
    }
    if (!map.getLayer(PLANNER_POI_LABEL_LAYER_ID)) {
      map.addLayer({
        id: PLANNER_POI_LABEL_LAYER_ID,
        type: 'symbol',
        source: PLANNER_POI_SOURCE_ID,
        layout: {
          'text-field': ['get', 'name'],
          'text-font': mapLabelFonts(map.getGlyphs()),
          'text-size': WAYPOINT_LABEL_SIZE_PX,
          'text-anchor': 'top',
          'text-offset': [0, PLANNER_POI_LABEL_OFFSET_EM],
          'text-allow-overlap': false,
        },
        paint: {
          'text-color': PLANNER_POI_COLOR,
          'text-halo-color': WAYPOINT_FILL_COLOR,
          'text-halo-width': PLANNER_POI_LABEL_HALO_WIDTH_PX,
        },
      });
    }
    void installPlannerPoiIcons(map, () => drawingActive.value && attachedMap.value === map)
      .then((installed) => {
        if (!installed || map.getLayer(PLANNER_POI_ICON_LAYER_ID)) return;
        map.addLayer({
          id: PLANNER_POI_ICON_LAYER_ID,
          type: 'symbol',
          source: PLANNER_POI_SOURCE_ID,
          layout: {
            'icon-image': ['get', 'iconImage'],
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
          },
        });
      })
      .catch((error) =>
        warnSanitized('[planner] Could not prepare POI icons', error, {
          topic1: 'planner',
          topic2: 'PlannerTool',
          topic3: 'addLayers',
        })
      );
    syncSources();
    emitViewport();
  };

  if (map.isStyleLoaded && map.isStyleLoaded()) {
    addLayers();
  } else {
    mapLoadHandler = addLayers;
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
  window.addEventListener('keydown', keydownHandler, true);
  window.addEventListener('blur', cancelPointerInteraction);
  window.addEventListener('mouseup', finishOutsideMap);

  // Viewport updates
  moveHandler = () => emitViewport();
  map.on('moveend', moveHandler);
  menuMoveHandler = () => updatePointMenuPosition();
  map.on('move', menuMoveHandler);
  window.addEventListener('resize', updatePointMenuPosition);
  contextMenuHandler = (event) => {
    if (editMode.value !== 'route') return;
    const hit = queryWaypointHit(pointFromEvent(event), 'mouse');
    if (!hit) return;
    preventPlannerEvent(event);
    finishPlannerInteraction(false);
    openPointMenu(hit.id);
  };
  map.on('contextmenu', contextMenuHandler);

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
  unwatchPois = watch(planner.pointsOfInterest, () => syncSources(), { deep: true });
}

function detachFromMap() {
  restoreMapKeyboardHelp?.();
  restoreMapKeyboardHelp = null;
  const map = attachedMap.value;
  pointMenuVisible.value = false;
  routeToolsVisible.value = false;
  window.removeEventListener('resize', updatePointMenuPosition);
  if (unwatchRoute) {
    unwatchRoute();
    unwatchRoute = null;
  }
  if (unwatchWaypoints) {
    unwatchWaypoints();
    unwatchWaypoints = null;
  }
  if (unwatchPois) {
    unwatchPois();
    unwatchPois = null;
  }
  finishPlannerInteraction(false);
  setMapGestureDragEnabled(true);
  if (hoverMarker.value) {
    hoverMarker.value.remove();
    hoverMarker.value = null;
  }
  window.removeEventListener('blur', cancelPointerInteraction);
  window.removeEventListener('mouseup', finishOutsideMap);
  if (keydownHandler) {
    window.removeEventListener('keydown', keydownHandler, true);
    keydownHandler = null;
  }

  if (!map) {
    attachedMap.value = null;
    return;
  }
  if (mapLoadHandler) {
    map.off('load', mapLoadHandler);
    mapLoadHandler = null;
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
  if (menuMoveHandler) {
    map.off('move', menuMoveHandler);
    menuMoveHandler = null;
  }
  if (contextMenuHandler) {
    map.off('contextmenu', contextMenuHandler);
    contextMenuHandler = null;
  }
  try {
    if (map.getLayer(PLANNER_POI_LABEL_LAYER_ID)) map.removeLayer(PLANNER_POI_LABEL_LAYER_ID);
    if (map.getLayer(PLANNER_POI_ICON_LAYER_ID)) map.removeLayer(PLANNER_POI_ICON_LAYER_ID);
    if (map.getLayer(PLANNER_POI_LAYER_ID)) map.removeLayer(PLANNER_POI_LAYER_ID);
    if (map.getSource(PLANNER_POI_SOURCE_ID)) map.removeSource(PLANNER_POI_SOURCE_ID);
    if (map.getLayer(PLANNER_ROUTE_HIT_LAYER_ID)) map.removeLayer(PLANNER_ROUTE_HIT_LAYER_ID);
    if (map.getLayer(PLANNER_LAYER_ID)) map.removeLayer(PLANNER_LAYER_ID);
    if (map.getSource(PLANNER_SOURCE_ID)) map.removeSource(PLANNER_SOURCE_ID);
    if (map.getLayer(PLANNER_WAYPOINT_LABEL_LAYER_ID)) map.removeLayer(PLANNER_WAYPOINT_LABEL_LAYER_ID);
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
      const lngLat = w.id === activeWaypointId && draftLngLat ? draftLngLat : w;
      return {
        type: 'Feature',
        properties: {
          id: w.id,
          index,
          finish: index === planner.waypoints.value.length - 1,
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
  const poiSrc = map.getSource(PLANNER_POI_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
  if (poiSrc) poiSrc.setData(poiFeature() as never);
}

function poiFeature() {
  const interaction = plannerInteraction.value;
  return {
    type: 'FeatureCollection',
    features: planner.pointsOfInterest.value.map((point) => ({
      type: 'Feature',
      properties: {
        id: point.id,
        name: point.name,
        iconImage: poiIconImageId(point.iconType),
        selected: selectedPoiId.value === point.id,
      },
      geometry: {
        type: 'Point',
        coordinates:
          interaction?.mode === 'poiDragging' && interaction.poiId === point.id
            ? [interaction.lastLngLat.lng, interaction.lastLngLat.lat]
            : [point.lng, point.lat],
      },
    })),
  };
}

function queryPoiHit(point: PlannerScreenPoint, pointerKind: PlannerPointerKind): PointOfInterest | null {
  const map = attachedMap.value;
  if (!map?.project) return null;
  const radius = waypointHitRadiusFor(pointerKind);
  let nearest: PointOfInterest | null = null;
  let nearestDistance = radius;
  for (const poi of planner.pointsOfInterest.value) {
    const distance = distancePx(point, map.project([poi.lng, poi.lat]));
    if (distance <= nearestDistance) {
      nearest = poi;
      nearestDistance = distance;
    }
  }
  return nearest;
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
  if (!attachedMap.value || !ev.lngLat || poiDraft.value) return;
  if (!isPrimaryStartEvent(ev)) {
    finishPlannerInteraction(false);
    return;
  }
  if (movePointActive.value) return;
  const pointerKind = pointerKindForEvent(ev);
  const point = pointFromEvent(ev);
  if (editMode.value === 'poi') {
    if (movePoiActive.value) return;
    const hit = queryPoiHit(point, pointerKind);
    if (!hit) return;
    finishPlannerInteraction(false);
    preventPlannerEvent(ev);
    suppressNextMapClick(clickSuppressionFor(pointerKind), point);
    setMapGestureDragEnabled(false);
    selectPoi(hit.id);
    const projected = attachedMap.value.project([hit.lng, hit.lat]);
    plannerInteraction.value = {
      mode: 'poiPointerDown',
      pointerKind,
      startPoint: point,
      lastLngLat: { lng: hit.lng, lat: hit.lat },
      poiId: hit.id,
      dragOffset: { x: projected.x - point.x, y: projected.y - point.y },
      touchId: touchIdentifierForEvent(ev) ?? undefined,
    };
    syncSources();
    return;
  }
  const waypointHit = queryWaypointHit(point, pointerKind);

  if (waypointHit) {
    pointMenuVisible.value = false;
    routeToolsVisible.value = false;
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
      dragOffset: waypointDragOffset(waypointHit.id, point),
      touchId: touchIdentifierForEvent(ev) ?? undefined,
    };
    syncSources();
    return;
  }

  if (pointMenuVisible.value || routeToolsVisible.value) {
    routeToolsVisible.value = false;
    deselectWaypoint();
    suppressNextMapClick(clickSuppressionFor(pointerKind), point);
    return;
  }

  if (!planner.viewportTooLarge.value) {
    const routeHit = inspectRouteHit(point, pointerKind);
    if (routeHit.selectedLegIndex !== null) {
      finishPlannerInteraction(false);
      preventPlannerEvent(ev);
      suppressNextMapClick(clickSuppressionFor(pointerKind), point);
      setMapGestureDragEnabled(false);
      selectedWaypointId.value = null;
      plannerInteraction.value = {
        mode: 'routePointerDown',
        pointerKind,
        startPoint: point,
        lastLngLat: lngLatFromEvent(ev),
        routeLegIndex: routeHit.selectedLegIndex,
        touchId: touchIdentifierForEvent(ev) ?? undefined,
      };
    }
  }
}

function handleMapPointerMove(ev: PlannerMapPointerEvent) {
  const interaction = plannerInteraction.value;
  if (!interaction) {
    if (attachedMap.value?.getCanvas && !movePointActive.value) {
      attachedMap.value.getCanvas().style.cursor = (
        editMode.value === 'poi'
          ? queryPoiHit(pointFromEvent(ev), pointerKindForEvent(ev))
          : queryWaypointHit(pointFromEvent(ev), pointerKindForEvent(ev))
      )
        ? 'grab'
        : '';
    }
    return;
  }
  if (!eventMatchesInteraction(ev, interaction) || !ev.lngLat) return;
  const point = pointFromEvent(ev);
  const movedPx = distancePx(point, interaction.startPoint);
  const thresholdPx = dragThresholdFor(interaction.pointerKind);

  preventPlannerEvent(ev);
  suppressNextMapClick(clickSuppressionFor(interaction.pointerKind), point);

  if (interaction.mode === 'routePointerDown') {
    plannerInteraction.value = {
      ...interaction,
      lastLngLat: lngLatFromEvent(ev),
    };
    return;
  }

  if ((interaction.mode === 'waypointPointerDown' || interaction.mode === 'poiPointerDown') && movedPx < thresholdPx)
    return;

  if (attachedMap.value?.getCanvas) attachedMap.value.getCanvas().style.cursor = 'grabbing';
  plannerInteraction.value = {
    ...interaction,
    mode: interaction.poiId ? 'poiDragging' : 'waypointDragging',
    lastLngLat: draggedLngLat(ev, interaction),
  };
  syncSources();
}

function handleMapPointerUp(ev: PlannerMapPointerEvent) {
  const interaction = plannerInteraction.value;
  if (!interaction || !eventMatchesInteraction(ev, interaction)) return;

  preventPlannerEvent(ev);
  suppressNextMapClick(clickSuppressionFor(interaction.pointerKind), pointFromEvent(ev));

  if (
    interaction.mode === 'waypointDragging' ||
    interaction.mode === 'poiDragging' ||
    interaction.mode === 'routePointerDown'
  ) {
    const lngLat = ev.lngLat
      ? interaction.mode === 'waypointDragging' || interaction.mode === 'poiDragging'
        ? draggedLngLat(ev, interaction)
        : lngLatFromEvent(ev)
      : interaction.lastLngLat;
    finishPlannerInteraction(true, lngLat);
    return;
  }

  const tappedWaypoint = interaction.mode === 'waypointPointerDown' ? interaction.waypointId : null;
  finishPlannerInteraction(false);
  if (tappedWaypoint) openPointMenu(tappedWaypoint);
}

function cancelPointerInteraction() {
  finishPlannerInteraction(false);
}

function finishOutsideMap(ev: MouseEvent) {
  if (!plannerInteraction.value || plannerInteraction.value.pointerKind !== 'mouse') return;
  if (attachedMap.value?.getCanvas()?.contains(ev.target)) return;
  suppressNextMapClick();
  finishPlannerInteraction(
    plannerInteraction.value.mode === 'waypointDragging' || plannerInteraction.value.mode === 'poiDragging'
  );
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
  const routeLegIndex = interaction.mode === 'routePointerDown' ? interaction.routeLegIndex : undefined;
  const finalLngLat = lngLat ?? interaction.lastLngLat;

  if (commit && interaction.mode === 'poiDragging' && interaction.poiId) {
    planner.movePointOfInterest(interaction.poiId, finalLngLat.lat, finalLngLat.lng);
  } else if (commit && waypointId) {
    const waypoint = planner.waypoints.value.find((point) => point.id === waypointId);
    const moved = waypoint && (waypoint.lat !== finalLngLat.lat || waypoint.lng !== finalLngLat.lng);
    planner.moveWaypoint(waypointId, finalLngLat.lat, finalLngLat.lng);
    if (moved) dismissPointDragHint();
  } else if (commit && routeLegIndex != null) {
    const waypoint = planner.insertWaypoint(routeLegIndex, finalLngLat.lat, finalLngLat.lng);
    selectedWaypointId.value = waypoint?.id ?? null;
  }

  plannerInteraction.value = null;
  if (attachedMap.value?.getCanvas) attachedMap.value.getCanvas().style.cursor = '';
  setMapGestureDragEnabled(true);
  syncSources();
}

function queryWaypointHit(point: PlannerScreenPoint, pointerKind: PlannerPointerKind): { id: string } | null {
  const map = attachedMap.value;
  if (!map?.project || !map?.getLayer?.(PLANNER_WAYPOINT_HIT_LAYER_ID)) return null;
  const hitRadiusPx = waypointHitRadiusFor(pointerKind);
  let best: { id: string; distancePx: number } | null = null;

  for (const waypoint of planner.waypoints.value) {
    const id = waypoint.id;
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
  if (
    !planner.routeReady.value ||
    planner.legs.value.length !== planner.waypoints.value.length - 1 ||
    !map?.getLayer?.(PLANNER_ROUTE_HIT_LAYER_ID)
  )
    return emptyDebug;
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

function waypointDragOffset(id: string, point: PlannerScreenPoint): PlannerScreenPoint {
  const waypoint = planner.waypoints.value.find((point) => point.id === id)!;
  const projected = attachedMap.value.project([waypoint.lng, waypoint.lat]);
  return { x: projected.x - point.x, y: projected.y - point.y };
}

function draggedLngLat(ev: PlannerMapPointerEvent, interaction: PlannerInteraction): PlannerLngLat {
  if (!interaction.dragOffset || !attachedMap.value?.unproject) return lngLatFromEvent(ev);
  const point = pointFromEvent(ev);
  return attachedMap.value.unproject([point.x + interaction.dragOffset.x, point.y + interaction.dragOffset.y]);
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
  if (!attachedMap.value || plannerInteraction.value || poiDraft.value) return;
  const pointerKind = pointerKindForEvent(ev);
  const point = pointFromEvent(ev);
  const suppressed = isMapClickSuppressed(point);
  if (editMode.value === 'poi') {
    if (suppressed) return;
    if (movePoiActive.value && selectedPoiId.value) {
      planner.movePointOfInterest(selectedPoiId.value, ev.lngLat.lat, ev.lngLat.lng);
      movePoiActive.value = false;
      syncSources();
      return;
    }
    const hit = queryPoiHit(point, pointerKind);
    if (hit) {
      selectPoi(hit.id);
      return;
    }
    if (planner.pointsOfInterest.value.length >= planner.maxPointsOfInterest.value) {
      poiError.value = `POI limit reached (max ${planner.maxPointsOfInterest.value}).`;
      return;
    }
    poiDraft.value = { id: '', lat: ev.lngLat.lat, lng: ev.lngLat.lng, name: '' };
    return;
  }
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
  if (movePointActive.value && selectedWaypointId.value) {
    planner.moveWaypoint(selectedWaypointId.value, ev.lngLat.lat, ev.lngLat.lng);
    deselectWaypoint();
    return;
  }
  if (waypointHit) {
    openPointMenu(waypointHit.id);
    suppressNextMapClick(clickSuppressionFor(pointerKind), point);
    syncSources();
    return;
  }
  if (pointMenuVisible.value || routeToolsVisible.value) {
    routeToolsVisible.value = false;
    deselectWaypoint();
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
  movePointActive.value = false;
  const waypointId = selectedWaypointId.value;
  if (!waypointId || !planner.waypoints.value.some((waypoint: Waypoint) => waypoint.id === waypointId)) return;
  planner.removeWaypoint(waypointId);
  selectedWaypointId.value = null;
  finishPlannerInteraction(false);
  syncSources();
}

function clearRoute() {
  movePointActive.value = false;
  selectedWaypointId.value = null;
  planner.clearAll();
  finishPlannerInteraction(false);
  syncSources();
}

function handlePlannerKeyDown(ev: KeyboardEvent) {
  if (
    !drawingActive.value ||
    isEditableKeyboardTarget(ev.target) ||
    saveDialogVisible.value ||
    deleteDialogVisible.value ||
    replaceDialogVisible.value ||
    poiDraft.value
  )
    return;
  const points = planner.waypoints.value;
  if (editMode.value === 'route' && points.length && ev.target === attachedMap.value?.getCanvas?.()) {
    const cycle = ev.altKey && !ev.ctrlKey && !ev.metaKey && (ev.key === 'ArrowLeft' || ev.key === 'ArrowRight');
    const openActions = ev.key === 'Enter' && !ev.altKey && !ev.ctrlKey && !ev.metaKey;
    if (cycle || openActions) {
      ev.preventDefault();
      ev.stopPropagation();
      if (openActions) openPointMenu(selectedWaypointId.value ?? points[0].id);
      else {
        const current = selectedWaypointIndex.value;
        const direction = ev.key === 'ArrowLeft' ? -1 : 1;
        const next =
          current < 0 ? (direction > 0 ? 0 : points.length - 1) : (current + direction + points.length) % points.length;
        pointMenuVisible.value = false;
        movePointActive.value = false;
        finishPlannerInteraction(false);
        selectedWaypointId.value = points[next].id;
        syncSources();
        panelAnnouncement.value = `Selected ${waypointLabel(next)}. Press Enter for actions.`;
      }
      return;
    }
  }
  if (ev.key === 'Escape') {
    if (pointMenuVisible.value || movePointActive.value || routeToolsVisible.value) {
      ev.preventDefault();
      ev.stopPropagation();
      if (routeToolsVisible.value) dismissRouteMenu({ restoreFocus: true });
      else dismissPointMenu({ restoreFocus: true });
      return;
    }
    deselectWaypoint();
    deselectPoi();
    return;
  }
  if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'z') {
    ev.preventDefault();
    if (ev.shiftKey) redoEdit();
    else undoEdit();
  } else if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 's') {
    ev.preventDefault();
    openSaveDialog();
  } else if (
    (hasSelectedWaypoint.value || selectedPoi.value) &&
    (ev.key === 'Backspace' || ev.key === 'Delete') &&
    ev.target === attachedMap.value?.getCanvas?.()
  ) {
    ev.preventDefault();
    if (editMode.value === 'poi') deleteSelectedPoi();
    else deleteSelectedWaypoint();
  }
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
  [drawingActive, () => props.map],
  ([drawing]) => {
    detachFromMap();
    emit('active-changed', drawing);
    if (drawing) attachToMap();
  },
  { flush: 'sync' }
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

function protectUnsavedDraft(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value) return;
  event.preventDefault();
  event.returnValue = '';
}

watch(hasUnsavedChanges, (dirty) => {
  if (dirty) window.addEventListener('beforeunload', protectUnsavedDraft);
  else window.removeEventListener('beforeunload', protectUnsavedDraft);
});

watch([hasSelectedWaypoint, drawingActive], ([selected, drawing]) => {
  if (!selected || !drawing) pointMenuVisible.value = false;
  if (!drawing) routeToolsVisible.value = false;
});
watch(
  () => panel.layout.value,
  () => {
    updatePointMenuPosition();
    if (routeToolsVisible.value && routeMenuTrigger.value) {
      const rect = routeMenuTrigger.value.getBoundingClientRect();
      routeMenuAnchor.value = { x: rect.left, y: rect.bottom };
    }
  },
  { flush: 'post' }
);

watch(saveDialogVisible, (visible) => {
  if (!visible && !saveSubmitting.value) openAfterSaveId.value = null;
});

onBeforeUnmount(() => {
  planner.dispose();
  window.removeEventListener('beforeunload', protectUnsavedDraft);
  clearRouteUpdateHideTimer();
  removeBrouterOutsideClickHandler();
  detachFromMap();
});

defineExpose({
  isOpen,
  open,
  toggle,
  close,
  saveCurrent,
});
</script>

<style scoped>
.planner-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
.planner-poi-tools,
.planner-poi-selected {
  display: grid;
  gap: 0.5rem;
}
.planner-poi-selected {
  padding: 0.6rem;
  border: 1px solid var(--border-default);
  border-radius: var(--p-content-border-radius);
}
.planner-poi-notes {
  margin: 0;
  font-size: var(--text-sm-size);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.planner-poi-list {
  display: grid;
  gap: 0.3rem;
  list-style: none;
  margin: 0;
  padding: 0;
}
.planner-poi-list button {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  min-height: 2.75rem;
  padding: 0.5rem;
  border: 1px solid var(--border-default);
  border-radius: var(--p-content-border-radius);
  color: var(--text-primary);
  background: var(--surface-elevated);
  text-align: left;
  cursor: pointer;
}
.planner-poi-list button span {
  overflow-wrap: anywhere;
  min-width: 0;
}
.planner-poi-list button[aria-pressed='true'] {
  border-color: var(--accent);
  background: var(--accent-subtle);
}
.planner-poi-list button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
/* ── Section navigation ───────────────────────────────────────── */
.planner-header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}
.planner-header-nav .panel-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planner-sheet-icon {
  font-size: 1rem;
  color: var(--text-secondary);
  flex-shrink: 0;
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
.planner-panel.planner-panel--draw {
  overflow: clip;
  padding: 0;
}
.planner-feedback-location {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
.planner-drawing-tools {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  gap: 0.5rem;
  padding-block: 0.5rem;
}
.planner-drawing-tools > * {
  flex-shrink: 0;
}
.planner-summary--essential .planner-drawing-tools {
  padding-block: 0;
}
.planner-drawing-tools :deep(.p-button) {
  min-height: 2.75rem;
  font-size: var(--text-sm-size);
}
/* Editing modes lead the expanded workspace; secondary actions stay together. */
.planner-drawing-tools {
  container-type: inline-size;
  container-name: planner-controls;
}
.planner-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(10.5rem, 13.5rem) auto;
  grid-template-areas: '. profile-label .' 'mode profile save' 'utility utility utility';
  align-items: center;
  gap: 0.5rem 0.75rem;
}
.planner-workspace--poi {
  grid-template-areas: 'mode mode save' 'utility utility utility';
}
.planner-edit-mode {
  grid-area: mode;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
  max-width: 30rem;
  padding: 0.1875rem;
  border: 1px solid var(--border-default);
  border-radius: 0.75rem;
  background: var(--surface-elevated);
}
.planner-edit-mode .planner-mode-button {
  min-width: 0;
  min-height: 2.75rem;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.25rem 0.75rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  color: var(--text-secondary);
  font-weight: var(--font-semibold);
}
.planner-edit-mode .planner-mode-button.is-active {
  border-color: var(--accent-muted);
  background: var(--accent-subtle);
  color: var(--text-primary);
  font-weight: var(--font-bold);
}
.planner-mode-button.is-active i {
  color: var(--accent-text);
}
.planner-mode-button i {
  flex-shrink: 0;
}
.planner-mode-label {
  min-width: 0;
  white-space: normal;
  line-height: 1.2;
}
.planner-mode-button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}
.planner-routing-controls {
  display: contents;
}
.planner-setting-label {
  grid-area: profile-label;
  font-size: var(--text-xs-size);
  font-weight: var(--font-medium);
  color: var(--text-muted);
}
.planner-routing-controls :deep(.p-select.planner-profile-select) {
  grid-area: profile;
  width: 100%;
  min-height: 2.75rem;
  font-size: var(--text-sm-size);
}
.planner-routing-controls :deep(.p-select-label) {
  font-size: inherit;
}
.planner-save-button {
  grid-area: save;
  justify-self: end;
}
.planner-utility-actions {
  grid-area: utility;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.planner-action-group {
  display: flex;
  align-items: center;
  gap: 0.2rem;
}
.planner-action-group + .planner-action-group {
  padding-left: 0.75rem;
  border-left: 1px solid var(--border-default);
}
.planner-tool-chevron {
  font-size: var(--text-2xs-size);
}
@container planner-controls (max-width: 40rem) {
  .planner-workspace {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: 'mode mode' 'profile save' 'utility utility';
    gap: 0.5rem;
  }
  .planner-workspace--poi {
    grid-template-areas: 'mode mode' 'utility save';
  }
  .planner-edit-mode {
    max-width: none;
  }
  .planner-routing-controls {
    grid-area: profile;
    display: grid;
    min-width: 0;
    gap: 0.2rem;
  }
  .planner-setting-label,
  .planner-routing-controls :deep(.p-select.planner-profile-select) {
    grid-area: auto;
  }
  .planner-workspace:not(.planner-workspace--poi) .planner-save-button {
    align-self: end;
  }
  .planner-utility-actions {
    gap: 0.35rem;
  }
  .planner-action-group + .planner-action-group {
    padding-left: 0.35rem;
  }
}
/* ── Adaptive drawing panel ──────────────────────────────────────────── */
.planner-pinned {
  position: relative;
  overflow-y: clip;
  overscroll-behavior: contain;
}
.planner-sr {
  position: absolute;
  top: 0;
  left: 0;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.planner-size-button {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.3rem;
  height: var(--bs-btn-size, 1.6rem);
  padding: 0 0.65rem 0 0.5rem;
  border: 1px solid var(--border-medium);
  border-radius: 999px;
  background: var(--surface-hover);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
}
.planner-size-button::after {
  content: '';
  position: absolute;
  inset: -0.55rem -0.2rem;
}
.planner-size-button:hover {
  background: var(--surface-active);
}
.planner-size-button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.planner-panel-tip {
  position: fixed;
  z-index: var(--z-bottom-sheet-content);
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: max-content;
  padding: 0.35rem 0.35rem 0.35rem 0.75rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.75rem;
  background: var(--surface-sheet-solid);
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.planner-panel-tip__dismiss {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  margin: -0.35rem -0.35rem -0.35rem 0;
  border: 0;
  border-radius: 0.6rem;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.planner-panel-tip__dismiss:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.planner-panel-tip__dismiss:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.planner-entry-scroll {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: contain;
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
.planner-route-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}
.planner-panel :deep(.p-button) {
  min-height: 2.75rem;
  font-size: var(--text-sm-size);
}
.planner-save-button {
  white-space: nowrap;
}
.planner-history-button {
  width: 2.75rem;
  flex-shrink: 0;
}
.planner-unsaved-dot {
  width: 0.4rem;
  height: 0.4rem;
  flex-shrink: 0;
  border-radius: 50%;
  background: currentColor;
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
.planner-load-error {
  flex-wrap: wrap;
  border: 1px solid color-mix(in srgb, var(--error) 28%, var(--border-default));
  background: var(--error-bg);
  color: var(--error);
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
  flex-wrap: wrap;
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
  margin-left: auto;
}
.planner-plan-edit {
  min-width: 4.5rem;
  color: var(--accent-text);
  padding-inline: 0.5rem;
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
.brouter-download-failures p {
  margin: 0.25rem 0 0;
  overflow-wrap: anywhere;
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

@media (max-width: 640px) {
  .planner-route-actions :deep(.p-button) {
    padding-inline: 0.5rem;
  }
  .planner-panel {
    padding-inline: 0.75rem;
    padding-bottom: 0.85rem;
  }
  .planner-plan-item {
    gap: 0.35rem;
  }
  .planner-plan-open {
    flex-basis: 100%;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }
  .planner-plan-body {
    width: 100%;
  }
  .planner-plan-meta {
    flex-wrap: wrap;
  }
  .planner-plan-desc {
    flex-basis: 100%;
  }
  .planner-plan-date {
    align-self: flex-start;
  }
  .planner-plan-actions {
    gap: 0.3rem;
    width: 100%;
    justify-content: flex-end;
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
</style>

<style>
/* Keep the labelled size action and all essential header controls reachable at larger text sizes. */
@media (max-width: 640px) {
  .planner-sheet.sheet--header-compact {
    --bs-btn-size: 44px;
    --bs-header-px: 8px;
    --bs-header-gap: 4px;
    --bs-actions-gap: 4px;
  }
  .planner-sheet.sheet--header-compact .sheet-back-btn {
    width: var(--bs-btn-size);
    min-width: var(--bs-btn-size);
    height: var(--bs-btn-size);
  }
  .planner-sheet.sheet--header-compact .planner-size-button {
    padding-inline: 0.35rem;
  }
  .planner-sheet.sheet--header-compact .sheet-back-btn::after,
  .planner-sheet.sheet--header-compact .sheet-close-btn::after,
  .planner-sheet.sheet--header-compact .brouter-pill::after,
  .planner-sheet.sheet--header-compact .planner-size-button::after {
    inset: 0;
  }
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
