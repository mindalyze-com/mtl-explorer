<template>
  <div class="container">
    <div ref="mapContainer" class="map"></div>

    <!-- ─── Navigation sheet (bottom bar mobile / left panel desktop) ─── -->
    <NavigationSheet
      ref="navSheet"
      :tools="toolDefs"
      :active-tool="activeToolId"
      :primary-ids="MOBILE_PRIMARY_TOOL_IDS"
      :utility-id="NAVIGATION_UTILITY_TOOL_ID"
      :utility-badge="
        releaseUpdates.hasUpdate ? 'Update' : releaseUpdates.browserUpdateAvailable ? 'Reload' : undefined
      "
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
      @track-updated="onTrackMetadataUpdated"
      @tool-opened="onToolOpened('statistics')"
      @tool-closed="onToolClosed"
    />

    <HealthTool ref="healthTool" @tool-opened="onToolOpened('healthTool')" @tool-closed="onToolClosed" />
    <TrackReviewTool
      ref="reviewTool"
      @tool-opened="onToolOpened('reviewTool')"
      @tool-closed="onToolClosed"
      @data-changed="onMapFreshnessBrowserReload"
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
    <div class="mtl-top-right" :class="{ 'mtl-top-right--drawing': geoDrawingParamDef }">
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

    <!-- ─── Top progress bar ─── -->
    <transition name="bar-fade">
      <div v-if="showLoader || loadingTrackBatches || loadingTracks10m" class="mtl-progress-bar"></div>
    </transition>

    <!-- ─── Settings and administration ─── -->
    <AdminDialog
      ref="adminTool"
      @tool-opened="onToolOpened('adminTool')"
      @tool-closed="onToolClosed"
      @reload-tracks="onAdminReloadTracks"
      @refresh-freshness-data="onAdminRefreshFreshnessData"
    />

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

    <MapDataStatusNotice
      :status="mapDataStatus"
      :reloading="freshnessReloading"
      :map-element="mapContainer"
      :placement-key="mapDataStatusPlacementKey"
      @reload="onMapFreshnessBrowserReload"
    />

    <BottomSheet
      v-if="mapInteraction"
      :model-value="true"
      :detents="MAP_INTERACTION_DETENTS"
      :initial-detent="MAP_TARGET_CHOOSER_DETENT_ID"
      :selected-detent="MAP_TARGET_CHOOSER_DETENT_ID"
      :fit-small-overflow="false"
      desktop-width="standard"
      share-layout
      :title="mapInteractionTitle"
      :icon="mapInteractionIcon"
      :back-label="
        mapInteraction.presentation === 'target' && !mapInteractionShowsTrackDetails ? 'Back to results' : ''
      "
      header-mode="compact"
      :z-index="5300"
      no-scroll-hint
      :native-fullscreen="mapInteractionShowsPhotoViewer"
      :fullscreen-control-labels="mapInteractionShowsPhotoViewer"
      :immersive="mapInteractionShowsPhotoViewer"
      :sheet-class="[
        'map-target-sheet',
        mapInteractionShowsPhotoViewer ? 'media-viewer-sheet' : '',
        mapInteractionShowsPhotoViewer ? dispatcherPhotoThemeClass : '',
      ]"
      @update:model-value="!$event && endMapInteraction(true)"
      @back="backFromMapContent"
      @layout-change="mapInteractionVisibility.onLayoutChange"
    >
      <template
        v-if="mapInteraction.presentation === 'target' && mapInteractionDetailKind === 'photo' && mediaSheetVisible"
        #title
      >
        <span class="media-preview-sheet-title">
          <i class="bi bi-image" aria-hidden="true"></i>
          <span>{{ mediaViewerScopeLabel }}</span>
          <span class="media-preview-sheet-title__counter"
            >{{ mediaCurrentIndex >= 0 ? mediaNavOffset + mediaCurrentIndex + 1 : 0 }} of {{ mediaNavTotal }}</span
          >
        </span>
      </template>
      <template #header-actions>
        <template
          v-if="mapInteraction.presentation === 'target' && mapInteractionDetailKind === 'photo' && mediaSheetVisible"
        >
          <MediaViewerViewToggle
            v-model="mediaPreviewDetailsVisible"
            v-model:map-visible="mediaPreviewMapVisible"
            v-model:photos-visible="mediaPreviewPhotosVisible"
          />
          <MediaViewerThemeToggle v-model:theme="dispatcherPhotoTheme" />
        </template>
      </template>
      <div
        v-show="
          mapInteraction.presentation === 'target' && !mapInteractionShowsTrackDetails && mapInteractionHasLookupStatus
        "
        class="map-interaction-context"
      >
        <MapInteractionStatus v-show="mapInteractionHasLookupStatus" :state="mapInteraction" />
      </div>
      <MapTargetChooser
        v-show="mapInteraction.presentation === 'chooser' || mapInteractionShowsTrackDetails"
        ref="mapInteractionChooser"
        :inert="mapInteractionShowsTrackDetails"
        :state="mapInteraction"
        @select="selectMapTarget"
        @preview-track="previewTrackById"
      />
      <div
        v-show="mapInteraction.presentation === 'target' && !mapInteractionShowsTrackDetails"
        ref="mapInteractionContent"
        tabindex="-1"
        class="map-interaction-content"
        :class="{
          'map-interaction-content--compact': mapInteractionHasCompactContent,
          'map-interaction-content--workspace':
            mapInteractionDetailKind === 'track' || mapInteractionDetailKind === 'photo',
        }"
      >
        <TrackProcessingNotice
          v-if="mapInteraction.processing"
          :processing="mapInteraction.processing"
          :empty="
            !mapInteraction.candidates.some(
              (candidate) => candidate.kind === 'track' || candidate.kind === 'track-point'
            )
          "
        />
        <p v-if="mapInteractionDetailLoading && !trackPointsPopupContext && !mediaPendingSelection" role="status">
          Loading details…
        </p>
        <p v-if="mapInteractionDetailError" role="alert">{{ mapInteractionDetailError }}</p>
        <template v-if="mapInteractionDetailKind === 'point' && trackPointsPopupContext">
          <dl class="map-point-details">
            <template v-for="row in mapInteractionPointRows" :key="row.label"
              ><dt>{{ row.label }}</dt>
              <dd>{{ row.value }}</dd></template
            >
          </dl>
          <button type="button" class="map-alternatives-button" @click="openMapInteractionTrackDetails">
            Open track details
          </button>
        </template>
        <template v-if="mapInteractionDetailKind === 'simple' && mapInteraction.selected">
          <MapPoiDetails v-if="mapInteraction.selected.kind === 'poi'" :candidate="mapInteraction.selected" />
          <MapOverlayDetails
            v-if="mapInteraction.selected.kind === 'overlay'"
            :route="mapInteractionOverlayDetails || mapInteraction.selected.route"
            :source-label="mapInteraction.selected.sourceLabel || 'Trail overlay'"
          />
        </template>
        <MediaPreview
          v-if="mapInteractionDetailKind === 'photo' && mediaSheetVisible"
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
          :map-visible="mediaPreviewMapVisible"
          :photos-visible="mediaPreviewPhotosVisible"
          @prev="navigateMediaRelative(-1)"
          @next="navigateMediaRelative(1)"
          @select="navigateMediaTo"
          @request-page="navigateMediaPage"
          @update:details-visible="mediaPreviewDetailsVisible = $event"
          @update:map-visible="mediaPreviewMapVisible = $event"
          @open-on-map="openSelectedMapMediaOnMainMap"
        />
      </div>
    </BottomSheet>

    <!-- ─── Track details bottom sheet ─── -->
    <BottomSheet
      v-if="!mapInteraction || mapInteractionDetailKind === 'track'"
      :model-value="trackDetailsSheetOpen"
      :detents="trackDetailsDetents"
      :initial-detent="trackDetailsInitialDetent"
      :initially-maximized="trackDetailsFullTakeover"
      :selected-detent="trackDetailsSelectedDetent"
      :background-detent="trackReplayActive ? trackDetailsBackgroundDetent : undefined"
      desktop-width="standard"
      :z-index="mapInteraction ? 5350 : 5300"
      :back-label="mapInteraction ? 'Back to results' : 'Back'"
      defer-close
      :share-layout="!!mapInteraction"
      no-scroll-hint
      @close-request="closeTrackDetailsFromPanel"
      @back="closeTrackDetailsFromPanel"
    >
      <template #title>
        <div class="td-sheet-header">
          <h2 data-test="track-details-title" class="td-sheet-name" :title="trackDetailsDisplayName">
            {{ trackDetailsDisplayName }}
          </h2>
          <div class="td-sheet-meta">
            <ActivityTypeBadge
              v-if="trackDetailsInfo.activityType"
              :type="trackDetailsInfo.activityType"
              size="xs"
              colored
            />
            <span v-if="trackDetailsPanel?.gpsTrack?.startDate" class="td-sheet-date">
              {{ formatDateAndTime(new Date(trackDetailsPanel.gpsTrack.startDate)) }}
            </span>
          </div>
        </div>
      </template>
      <template #header-actions>
        <TrackDetailActions
          v-if="trackDetailsSheetOpen"
          :gps-track="trackDetailsPanel?.gpsTrack"
          :replay-enabled="trackDetailsPanel?.canStart3dReplay"
          @start-3d-replay="trackDetailsPanel?.start3dReplay()"
        />
      </template>
      <div v-if="mapInteraction && mapInteractionHasLookupStatus" class="map-interaction-context">
        <MapInteractionStatus :state="mapInteraction" />
      </div>
      <TrackDetails
        v-if="trackDetailsId != null"
        :key="trackDetailsId"
        ref="trackDetailsPanel"
        :gps-track-id="trackDetailsId"
        :initial-tab="trackDetailsInitialTab"
        @back="closeTrackDetailsFromPanel"
        @track-loaded="onTrackDetailsLoaded"
        @track-updated="onTrackMetadataUpdated"
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
      v-if="!mapInteraction"
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
        :tracks-error="mediaSelectionTracksError"
        @choose-primary="chooseMediaCollection('primary')"
        @choose-viewport="chooseMediaCollection('viewport')"
        @open-activities="openMediaSelectionActivities"
        @retry-activities="retryMediaSelectionActivities"
      />
    </BottomSheet>

    <!-- ─── Media photo bottom sheet ─── -->
    <BottomSheet
      v-if="!mapInteraction"
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
      fullscreen-control-labels
      immersive
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
        <MediaViewerViewToggle
          v-model="mediaPreviewDetailsVisible"
          v-model:map-visible="mediaPreviewMapVisible"
          v-model:photos-visible="mediaPreviewPhotosVisible"
        />
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
        :map-visible="mediaPreviewMapVisible"
        :photos-visible="mediaPreviewPhotosVisible"
        @prev="navigateMediaRelative(-1)"
        @next="navigateMediaRelative(1)"
        @select="navigateMediaTo"
        @request-page="navigateMediaPage"
        @update:details-visible="mediaPreviewDetailsVisible = $event"
        @update:map-visible="mediaPreviewMapVisible = $event"
        @open-on-map="openSelectedMapMediaOnMainMap"
      />
    </BottomSheet>

    <!-- ─── Track selection bottom sheet ─── -->
    <BottomSheet
      v-if="!mapInteraction"
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
        <div class="track-selection-sheet__scroll" data-sheet-fit-target>
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
          <TrackProximityResults
            v-else
            :tracks="selectionPopupTracks"
            :processing="selectionPopupProcessing"
            @select="onPopupTrackSelect"
          />
        </div>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onScopeDispose, ref, watch } from 'vue';
import { useRoute, useRouter, type RouteRecordNameGeneric } from 'vue-router';
import AdminDialog from '@/components/admin/AdminDialog.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import GpsLocate from '@/components/gps/GpsLocate.vue';
import MeasureBetweenPoints from '@/components/measure/MeasureBetweenPoints.vue';
import PlannerTool from '@/planner/components/PlannerTool.vue';
import AnimateMap from '@/components/animate/AnimateMap.vue';
import Filter from '@/components/filter/Filter.vue';
import Statistics from '@/components/statistics/Statistics.vue';
import HealthTool from '@/features/health/HealthTool.vue';
import TrackReviewTool from '@/features/track-review/TrackReviewTool.vue';
import TrackDetails from '@/components/trackdetails/TrackDetails.vue';
import TrackDetailActions from '@/components/trackdetails/TrackDetailActions.vue';
import { formatDateAndTime } from '@/utils/Utils';
import TrackReplayControls from '@/components/replay/TrackReplayControls.vue';
import NavigationSheet from '@/components/ui/NavigationSheet.vue';
import MapSettingsPanel from '@/components/map/MapSettingsPanel.vue';
import MapLegend from '@/components/map/MapLegend.vue';
import MapTargetChooser from '@/components/map/MapTargetChooser.vue';
import MapInteractionStatus from '@/components/map/MapInteractionStatus.vue';
import MapOverlayDetails from '@/components/map/MapOverlayDetails.vue';
import MapPoiDetails from '@/components/map/MapPoiDetails.vue';
import { mapPoiPresentation } from '@/components/map/mapPoiPresentation';
import { buildArchiveTrackPointPopupRows } from '@/components/map/composables/useTrackPointLayer';
import MapConfigFallbackNotice from '@/components/map/MapConfigFallbackNotice.vue';
import MapDataStatusNotice from '@/components/map/MapDataStatusNotice.vue';
import { resolveMapDataStatus } from '@/components/map/mapDataStatus';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import LocationSearchSheet from '@/components/map/LocationSearchSheet.vue';
import BottomSheet, { type Detent } from '@/components/ui/BottomSheet.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import NearbyTrackList from '@/components/map/NearbyTrackList.vue';
import TrackProximityResults from '@/components/map/TrackProximityResults.vue';
import TrackProcessingNotice from '@/components/map/TrackProcessingNotice.vue';
import MediaCollectionChooser from '@/components/map/MediaCollectionChooser.vue';
import MediaPreview from '@/components/map/MediaPreview.vue';
import MediaViewerThemeToggle from '@/components/map/MediaViewerThemeToggle.vue';
import MediaViewerViewToggle from '@/components/map/MediaViewerViewToggle.vue';
import { useMediaViewerTheme, type MediaViewerTheme } from '@/composables/useMediaViewerTheme';
import { useMainMapController } from '@/components/map/useMainMapController';
import { useMapInteractionTrackRoute } from '@/components/map/composables/useMapInteractionTrackRoute';
import { useMediaQuery } from '@/composables/useMediaQuery';
import {
  MAP_TARGET_CHOOSER_DETENT_ID,
  useMapInteractionVisibility,
} from '@/components/map/composables/useMapInteractionVisibility';
import { mapTargetKey } from '@/components/map/mapInteractionDecision';
import { findMediaPointById } from '@/components/map/composables/useMediaAndHeatmap';
import type { MapControllerEmit, MapControllerProps } from '@/components/map/composables/mapControllerRuntime';
import type { ToastService } from '@/types/ui';
import { clearMapConfigCache, type MapConfig } from '@/utils/mapConfigService';
import { warnRequestFailure } from '@/utils/safeLogging';

defineOptions({
  name: 'Map2DRenderer',
});

const props = withDefaults(defineProps<MapControllerProps>(), { fromLogin: false });
const emit = defineEmits<MapControllerEmit>();

const toast = inject<ToastService>('toast', { add: () => undefined });
const route = useRoute();
const router = useRouter();
const { mediaViewerThemeClass } = useMediaViewerTheme();
const DEFAULT_DISPATCHER_PHOTO_THEME: MediaViewerTheme = 'light';
const dispatcherPhotoTheme = ref<MediaViewerTheme>(DEFAULT_DISPATCHER_PHOTO_THEME);
const dispatcherPhotoThemeClass = computed(() => `media-viewer-sheet--${dispatcherPhotoTheme.value}`);

const trackDetailsPanel = ref<InstanceType<typeof TrackDetails> | null>(null);
const trackDetailsDisplayName = computed(() => {
  const track = trackDetailsPanel.value?.gpsTrack;
  return track?.trackName || track?.metaName || track?.indexedFile?.name || `Track #${trackDetailsId.value}`;
});
const mapContainer = ref<HTMLElement | null>(null);
const navSheet = ref(null);
const mapSettingsTool = ref(null);
const animateTool = ref(null);
const measureTool = ref(null);
const plannerTool = ref(null);
const statistics = ref<{ close?: () => void } | null>(null);
const healthTool = ref(null);
const reviewTool = ref(null);
const gpsLocate = ref(null);
const filterTool = ref(null);
const adminTool = ref(null);
const releaseUpdates = useReleaseUpdatesStore();
const mapConfigFallbackDismissed = ref(false);
const mapConfigRetrying = ref(false);
const MEDIA_PREVIEW_DESKTOP_MIN_WIDTH = 769;
const MOBILE_PRIMARY_TOOL_IDS = ['gps', 'map', 'filter', 'planner'];
const NAVIGATION_UTILITY_TOOL_ID = 'admin';

const {
  mapInteraction,
  mapInteractionDetailKind,
  mapInteractionDetailLoading,
  mapInteractionDetailError,
  mapInteractionOverlayDetails,
  trackPointsPopupContext,
  endMapInteraction,
  selectMapTarget,
  showMapTargetChooser,
  backFromMapTargetChooser,
  openMapInteractionTrackDetails,
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
  selectionPopupProcessing,
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
  mediaSelectionTracksError,
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
  openLocationSearch,
  onLocationSearchSelect,
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
  closeSelectionPopup,
  chooseMediaCollection,
  openMediaSelectionActivities,
  retryMediaSelectionActivities,
  closeMediaSelection,
  navigateMediaTo,
  navigateMediaRelative,
  navigateMediaPage,
  closeMediaSheet: closeControllerMediaSheet,
  focusMediaOnMainMap,
  onPopupTrackSelect,
  onTrackDetailsSheetClosed: closeControllerTrackDetailsSheet,
  onTrackDetailsLoaded,
  onTrackMetadataUpdated,
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
  previewTrackById,
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
  healthTool,
  reviewTool,
  gpsLocate,
  filterTool,
  adminTool,
});

const mapInteractionChooser = ref<InstanceType<typeof MapTargetChooser> | null>(null);
const trackDetailsFullTakeover = useMediaQuery(
  '(max-width: 768px)',
  window.innerWidth < MEDIA_PREVIEW_DESKTOP_MIN_WIDTH
);
const mapInteractionContent = ref<HTMLElement | null>(null);
const mapInteractionShowsTrackDetails = computed(
  () => mapInteraction.value?.presentation === 'target' && mapInteractionDetailKind.value === 'track'
);
const trackDetailsSheetOpen = computed(
  () => trackDetailsVisible.value && (!mapInteraction.value || mapInteractionShowsTrackDetails.value)
);
const mapInteractionVisibility = useMapInteractionVisibility({
  map: () => overlayMap.value,
  interaction: () => mapInteraction.value,
});
const mapInteractionShowsPhotoViewer = computed(
  () =>
    mapInteraction.value?.presentation === 'target' &&
    mapInteractionDetailKind.value === 'photo' &&
    mediaSheetVisible.value
);
// Back and photo navigation retain the viewer choice within this map exploration.
watch(
  () => mapInteraction.value?.id,
  () => {
    dispatcherPhotoTheme.value = DEFAULT_DISPATCHER_PHOTO_THEME;
  }
);
const mapInteractionHasLookupStatus = computed(
  () => mapInteraction.value?.sources.some((source) => source.status !== 'complete') ?? false
);
const mapInteractionHasCompactContent = computed(
  () =>
    mapInteraction.value?.presentation === 'target' &&
    (mapInteractionDetailKind.value === 'simple' ||
      mapInteractionDetailKind.value === 'point' ||
      (mapInteractionDetailKind.value === 'photo' && !mediaSheetVisible.value))
);
const mapInteractionTitle = computed(() =>
  mapInteraction.value?.presentation === 'chooser' || mapInteractionShowsTrackDetails.value
    ? 'Choose what to see here'
    : mapInteractionDetailKind.value === 'track'
      ? trackDetailsDisplayName.value
      : mapInteractionDetailKind.value === 'photo' && mediaSheetVisible.value
        ? mediaViewerScopeLabel.value
        : mapInteraction.value?.selected?.kind === 'overlay'
          ? 'Route details'
          : mapInteraction.value?.selected?.label || 'At this location'
);
const mapInteractionIcon = computed(() => {
  if (mapInteraction.value?.presentation === 'chooser' || mapInteractionShowsTrackDetails.value) return 'bi bi-layers';
  const selected = mapInteraction.value?.selected;
  if (selected?.kind === 'overlay') return 'bi bi-signpost-2';
  if (selected?.kind === 'poi') return mapPoiPresentation(selected).icon;
  return 'bi bi-geo-alt';
});
const MAP_TARGET_CHOOSER_HEIGHT = 'min(30rem, 60vh)';
// GPS details use a separate foreground sheet; this dispatcher keeps its own size.
const MAP_INTERACTION_DETENTS: Detent[] = [
  { id: MAP_TARGET_CHOOSER_DETENT_ID, height: MAP_TARGET_CHOOSER_HEIGHT },
  { id: 'medium', height: '70vh' },
  { id: 'expanded', height: '92vh' },
];
const mapInteractionPointRows = computed(() => {
  const context = trackPointsPopupContext.value;
  return context ? buildArchiveTrackPointPopupRows(context.lngLat, context.point, context.canonical) : [];
});
watch(
  () => [mapInteraction.value?.id, mapInteraction.value?.presentation, mapInteractionShowsTrackDetails.value],
  async (value, previous) => {
    if (value[0] !== previous?.[0] || (value[1] === previous?.[1] && value[2] === previous?.[2])) return;
    await nextTick();
    if (value[1] === 'chooser') {
      mapInteractionChooser.value?.$el.querySelector('button')?.focus();
    } else if (value[1] === 'target') {
      if (mapInteractionShowsTrackDetails.value) {
        await nextTick();
        await nextTick();
      }
      const content = mapInteractionShowsTrackDetails.value
        ? (trackDetailsPanel.value?.$el as HTMLElement | undefined)
        : mapInteractionContent.value;
      if (mapInteraction.value?.presentation !== 'target') return;
      const control = content?.querySelector<HTMLElement>('button, a[href]') || content;
      control?.focus();
    }
  }
);

const mediaPreviewDetailsVisible = ref(window.innerWidth >= MEDIA_PREVIEW_DESKTOP_MIN_WIDTH);
const mediaPreviewMapVisible = ref(mediaPreviewDetailsVisible.value);
const mediaPreviewPhotosVisible = ref(true);
const mediaViewerOverviewBounds = ref<[[number, number], [number, number]] | null>(null);
const selectedMapMediaPoint = computed(() => findMediaPointById(mediaNavList.value, mediaSheetMediaId.value));
const trackSelectionSheetTitle = computed(() =>
  trackSelectionPurpose.value === 'photos'
    ? `${selectionPopupTracks.value.length} nearby GPS ${selectionPopupTracks.value.length === 1 ? 'track' : 'tracks'}`
    : selectionPopupTracks.value.length === 0
      ? 'Nearby tracks'
      : `${selectionPopupTracks.value.length} ${selectionPopupTracks.value.length === 1 ? 'track' : 'tracks'} — select for details`
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
  if (mediaNavScope.value === 'viewport') return 'Map view at tap';
  return 'This photo';
});
const mediaViewerFilmstripLabel = computed(() => {
  if (mediaNavScope.value === 'cluster') return 'In this cluster';
  if (mediaNavScope.value === 'location') return 'At this location';
  if (mediaNavScope.value === 'viewport') return 'Map view at tap';
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
  // Hiding the sheet through its model does not emit BottomSheet's closed event.
  onMediaSheetClosed();
}

function onMediaSheetClosed(): void {
  endMapInteraction();
  closeControllerMediaSheet();
  mediaViewerOverviewBounds.value = null;
  focusPendingMainMapMediaTarget();
}

function openTrackMediaOnMainMap(target: MainMapMediaTarget): void {
  if (!setPendingMainMapMediaTarget(target)) return;
  mapTrackRoute.exitToMap();
  endMapInteraction();
  trackDetailsVisible.value = false;
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
  const selected = mapInteraction.value?.selected;
  if (selected?.kind === 'photo' && selected.viewportBounds) {
    mediaViewerOverviewBounds.value = selected.viewportBounds;
    return;
  }
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

const applicationStatus = useApplicationStatusStore();
const mapFreshness = useDataFreshnessStore();
const mapDataStatus = computed(() =>
  resolveMapDataStatus({
    loaded: initialLoadDone.value,
    offline: isOffline.value,
    processing: applicationStatus.processing,
    processingHealthy: applicationStatus.sectionStates[APPLICATION_STATUS_SECTION.PROCESSING].healthy,
    freshnessHealthy: applicationStatus.sectionStates[APPLICATION_STATUS_SECTION.FRESHNESS].healthy,
    outOfSync: mapFreshness.isOutOfSync || Boolean(mapFreshness.serverFreshnessToken && !mapFreshness.appliedToken),
  })
);
const mapDataStatusPlacementKey = computed(() =>
  JSON.stringify([
    legendCollapsed.value,
    legendEntries.value.length,
    activeToolId.value,
    geoDrawingParamDef.value,
    mapServerStatus.value?.ready,
    showMapConfigFallbackNotice.value,
  ])
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
  health: 'health',
  review: 'review',
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
let trackDetailsRouteExitOrigin: string | null = null;
let trackDetailsRouteExitTarget: string | null = null;
let standaloneTrackReturnPath: string | null = null;
let routeSyncRevision = 0;

const mapTrackRoute = useMapInteractionTrackRoute({
  router,
  state: () => ({
    interactionId: mapInteraction.value?.id ?? null,
    presentation: mapInteraction.value?.presentation ?? null,
    selectedKey: mapInteraction.value?.selected ? mapTargetKey(mapInteraction.value.selected) : null,
    detailKind: mapInteractionDetailKind.value,
    trackId: trackDetailsId.value,
    visible: trackDetailsVisible.value,
    toolActive: activeToolId.value != null,
  }),
  showChooser: showMapTargetChooser,
  restoreSelected: backFromMapTargetChooser,
  fallbackReturnPath: () => standaloneTrackReturnPath ?? router.resolve({ name: 'home' }).fullPath,
  rememberStandaloneReturn: (path) => {
    standaloneTrackReturnPath = path;
  },
});

function backFromMapContent() {
  if (!mapTrackRoute.backToResults()) showMapTargetChooser();
}

function closeTrackDetailsFromPanel() {
  if (mapInteraction.value) {
    backFromMapContent();
    return;
  }
  if (route.name === TRACK_DETAILS_ROUTE_NAME) {
    leaveTrackDetailsRoute();
    return;
  }
  endMapInteraction();
  trackDetailsVisible.value = false;
  closeControllerTrackDetailsSheet();
}

function toolIdForRoute(name: RouteRecordNameGeneric | null | undefined): string | null {
  return typeof name === 'string' ? (ROUTE_TOOL_IDS[name] ?? null) : null;
}

function updateRouteForTool(toolId: string | null | undefined) {
  if (syncingFromRoute) return;
  if (!toolId && mapInteraction.value) return;
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
  trackDetailsRouteExitOrigin = route.fullPath;
  trackDetailsRouteExitTarget = null;
  const previousRoute = standaloneTrackReturnPath ?? (window.history.state as { back?: unknown } | null)?.back;
  if (canReturnFromTrackDetails(previousRoute)) {
    router.back();
    return;
  }
  trackDetailsRouteExitTarget = router.resolve({ name: 'home' }).fullPath;
  router.replace({ name: 'home' }).catch(() => {
    trackDetailsRouteExitPending = false;
  });
}

function isStandaloneExit(to: { fullPath: string }, from: { fullPath: string }): boolean {
  return (
    trackDetailsRouteExitPending &&
    trackDetailsRouteExitOrigin === from.fullPath &&
    (trackDetailsRouteExitTarget == null || trackDetailsRouteExitTarget === to.fullPath)
  );
}
function clearStandaloneExit(): void {
  trackDetailsRouteExitPending = false;
  trackDetailsRouteExitOrigin = null;
  trackDetailsRouteExitTarget = null;
}
const stopStandaloneExitAfter = router.afterEach((to, from) => {
  if (isStandaloneExit(to, from)) clearStandaloneExit();
});
const stopStandaloneExitError = router.onError((error, to, from) => {
  if (!isStandaloneExit(to, from)) return;
  clearStandaloneExit();
  warnRequestFailure('Could not return from GPS track details', error, {
    topic1: 'navigation',
    topic2: 'map',
    topic3: 'trackRoute',
  });
});
onScopeDispose(() => {
  stopStandaloneExitAfter();
  stopStandaloneExitError();
});

watch(
  () => route.fullPath,
  async (path, previousPath) => {
    const revision = ++routeSyncRevision;
    const to = router.resolve(path);
    const from = previousPath ? router.resolve(previousPath) : undefined;
    clearStandaloneExit();
    syncingFromRoute = true;
    await nextTick();
    if (revision !== routeSyncRevision) return;
    if (mapTrackRoute.handleRouteChange(to, from)) {
      syncingFromRoute = false;
      return;
    }
    if (to.name === TRACK_DETAILS_ROUTE_NAME) {
      if (mapInteraction.value) endMapInteraction();
      if (from && from.name !== TRACK_DETAILS_ROUTE_NAME && canReturnFromTrackDetails(from.fullPath)) {
        standaloneTrackReturnPath = from.fullPath;
      }
      const trackId = Number(to.params.id);
      if (Number.isFinite(trackId)) {
        syncTrackDetailRoute(trackId);
      }
    } else {
      if (to.name !== 'home') endMapInteraction();
      if (from?.name === TRACK_DETAILS_ROUTE_NAME && !mapInteraction.value) {
        trackDetailsVisible.value = false;
        if (trackDetailsId.value != null) closeControllerTrackDetailsSheet();
      }
      syncToolToRoute(toolIdForRoute(to.name));
    }
    await nextTick();
    if (revision === routeSyncRevision) syncingFromRoute = false;
  },
  { immediate: true }
);

watch(activeToolId, (toolId) => {
  updateRouteForTool(toolId);
});

watch([trackDetailsVisible, trackDetailsId], ([visible, id]) => {
  if (syncingFromRoute || mapInteraction.value || mapTrackRoute.isManagingRoute()) return;
  if (visible && id != null) {
    if (route.name !== TRACK_DETAILS_ROUTE_NAME || Number(route.params.id) !== Number(id)) {
      router.push({ name: TRACK_DETAILS_ROUTE_NAME, params: { id } }).catch(() => undefined);
    }
  } else if (route.name === TRACK_DETAILS_ROUTE_NAME && !activeToolId.value) {
    // A selected tool owns its route change. Only closing details without
    // selecting another tool should return to the previous page.
    leaveTrackDetailsRoute();
  }
});
</script>

<style scoped>
.map-interaction-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  min-height: 2.75rem;
  flex: 0 0 auto;
  padding: 0 var(--dlg-padding);
}
.map-interaction-context :deep(.map-identification-status) {
  flex: 1 1 auto;
  max-height: 4rem;
  min-width: 0;
  overflow: auto;
}
.map-alternatives-button {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: 0.5rem;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  cursor: pointer;
  white-space: nowrap;
}
.map-interaction-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.map-interaction-content--workspace {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
.map-interaction-content--compact {
  padding: 0.25rem var(--dlg-padding) var(--dlg-padding);
}
.map-point-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.4rem;
  padding: 0.5rem;
  font-size: 0.85rem;
}
.map-point-details dt {
  color: var(--text-secondary);
}
.map-point-details dd {
  text-align: right;
}
:global(.mtl-map-interaction-marker) {
  width: 14px;
  height: 14px;
  border: 2px solid white;
  background: var(--info, #2563eb);
  border-radius: 50%;
  box-shadow: 0 0 0 2px #0006;
  pointer-events: none;
}

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
  right: var(--safe-right, 0px);
  bottom: var(--nav-sheet-h, 0px);
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

.mtl-top-right--drawing {
  z-index: var(--z-tool-overlay);
}

@media (max-width: 1023px) {
  .mtl-top-right--drawing {
    left: calc(0.6rem + var(--safe-left, 0px));
    gap: 0.5rem;
  }

  .mtl-top-right--drawing .geo-draw-toolbar {
    position: static;
    align-self: center;
    transform: none;
    min-width: min(200px, calc(100% - 4rem));
    max-width: calc(100% - 4rem);
  }
}

.mtl-location-search-fab {
  position: fixed;
  z-index: var(--z-map-overlay-raised);
  right: calc(0.85rem + var(--safe-right, 0px));
  bottom: calc(
    var(--nav-sheet-h, 92px) + var(--mtl-map-attribution-strip-h) + var(--mtl-map-attribution-bottom-gap) +
      var(--mtl-location-search-bottom-gap)
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

/* Track identity remains visible above every detail tab. */
.td-sheet-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding-block: 4px;
}
.td-sheet-name {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.td-sheet-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}
.td-sheet-date {
  white-space: nowrap;
}
@media (max-width: 640px) {
  .td-sheet-name {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    white-space: normal;
    overflow-wrap: anywhere;
    font-size: 20px;
  }
  .td-sheet-meta {
    gap: 4px 8px;
    font-size: 12px;
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
  border: 1px solid var(--border-default);
  border-radius: 50%;
  background: var(--surface-sheet-solid);
  color: var(--text-secondary);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.2);
  cursor: pointer;
  font-size: 0.85rem;
  line-height: 1;
  padding: 0;
}

.mtl-location-search-marker__clear:hover {
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  border-color: var(--border-hover);
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
  flex-wrap: wrap;
  justify-content: center;
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
  color: var(--accent-contrast);
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
