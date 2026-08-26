import { markRaw } from 'vue';
import * as maplibregl from 'maplibre-gl';
import type { MapControllerMethodDefinitions, MediaAndHeatmapMethods, MediaPoint } from './mapControllerRuntime';
import { mergeAdjacentMediaPage } from '@/components/map/mediaPageBuffer';
import { fetchTrackMediaOptionsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
import { haversineDistance } from '@/components/map/mapGeometry';
import { isAbortLikeError } from '@/utils/errors';
import type { NearbyTrackMediaDto } from 'x8ing-mtl-api-typescript-fetch';

const MEDIA_FOCUS_MIN_ZOOM = 15;
const MEDIA_FOCUS_MARKER_LABEL = 'Selected photo location';
const MEDIA_SELECTION_POINTER_RADIUS_PX = 16;
const MEDIA_SELECTION_TOUCH_RADIUS_PX = 24;

export function findMediaPointById(points: MediaPoint[], mediaId: number | null): MediaPoint | null {
  if (mediaId == null) return null;
  return points.find((point) => point.id === mediaId) ?? null;
}

export function normalizeNearbyTrackMediaOptions(options: NearbyTrackMediaDto[]): NearbyTrackMediaDto[] {
  const seenTrackIds = new Set<number>();
  return options
    .filter((option) => {
      if (!Number.isSafeInteger(option.trackId) || option.trackId <= 0 || seenTrackIds.has(option.trackId))
        return false;
      if (!Number.isFinite(option.distanceMeters) || option.distanceMeters < 0) return false;
      if (!Number.isFinite(option.matchedMediaCount) || option.matchedMediaCount < 0) return false;
      seenTrackIds.add(option.trackId);
      return true;
    })
    .map((option) => ({
      trackId: option.trackId,
      distanceMeters: option.distanceMeters,
      matchedMediaCount: Math.trunc(option.matchedMediaCount),
    }))
    .sort((a, b) => a.distanceMeters - b.distanceMeters || a.trackId - b.trackId);
}

export function useMediaAndHeatmap(deps: {
  mapSettingsStore: { setLayerEnabled: (layerId: 'media' | 'heatmap', enabled: boolean) => void };
}): MapControllerMethodDefinitions<MediaAndHeatmapMethods> {
  const { mapSettingsStore } = deps;
  const methods: MapControllerMethodDefinitions<MediaAndHeatmapMethods> = {
    async restoreMediaLayerPreference(enabled) {
      if (!this.mediaOverlay) {
        this.mediaVisible = false;
        return;
      }
      if (enabled) {
        await this.mediaOverlay.show();
        this.applyLayerOpacity('media');
      } else if (this.mediaOverlay.isVisible()) {
        this.mediaOverlay.hide();
      }
      this.mediaVisible = enabled;
    },

    async onToggleMediaLayer() {
      if (!this.mediaOverlay || this.mediaBusy) return;
      this.mediaBusy = true;
      try {
        if (this.mediaOverlay.isVisible()) {
          this.mediaOverlay.hide();
          mapSettingsStore.setLayerEnabled('media', false);
        } else {
          await this.mediaOverlay.show();
          mapSettingsStore.setLayerEnabled('media', true);
        }
        this.syncMapSettingsFromStore();
      } finally {
        this.mediaBusy = false;
      }
    },

    onToggleHeatmapLayer() {
      if (!this.heatmapOverlay || !this.geojson) return;
      if (this.heatmapOverlay.isVisible()) {
        this.heatmapOverlay.hide();
        mapSettingsStore.setLayerEnabled('heatmap', false);
      } else {
        this.heatmapOverlay.show(this.geojson);
        this.applyLayerOpacity('heatmap');
        mapSettingsStore.setLayerEnabled('heatmap', true);
      }
      this.syncMapSettingsFromStore();
    },

    async openMediaSelection(selection) {
      this.closeMediaSelection();
      this.mediaPendingSelection = selection;
      this.mediaSelectionSheetVisible = true;
      this.mediaSelectionTracksLoading = true;
      const requestToken = ++this.mediaSelectionRequestToken;
      const controller = markRaw(new AbortController());
      this.mediaSelectionAbortController = controller;

      try {
        if (!this.overlayMap) return;
        const radiusPixels =
          'ontouchstart' in window ? MEDIA_SELECTION_TOUCH_RADIUS_PX : MEDIA_SELECTION_POINTER_RADIUS_PX;
        const edge = this.overlayMap.unproject([selection.clickPoint.x + radiusPixels, selection.clickPoint.y]);
        const radiusMeters = haversineDistance(
          selection.clickLngLat.lat,
          selection.clickLngLat.lng,
          edge.lat,
          edge.lng
        );
        const trackOptions = await fetchTrackMediaOptionsWithinDistanceOfPoint(
          selection.clickLngLat.lng,
          selection.clickLngLat.lat,
          radiusMeters,
          controller.signal
        );
        if (requestToken !== this.mediaSelectionRequestToken || controller.signal.aborted) return;
        this.mediaSelectionTrackOptions = normalizeNearbyTrackMediaOptions(trackOptions);
      } catch (error) {
        if (!isAbortLikeError(error, controller.signal)) {
          console.warn('[map-media] nearby activity lookup failed', error);
        }
      } finally {
        if (requestToken === this.mediaSelectionRequestToken) {
          this.mediaSelectionTracksLoading = false;
          this.mediaSelectionAbortController = null;
        }
      }
    },

    chooseMediaCollection(scope) {
      const selection = this.mediaPendingSelection;
      if (!selection) return;
      const useViewport = scope === 'viewport';
      const points = useViewport ? selection.viewportMediaPoints : selection.mediaPoints;
      if (points.length === 0) return;

      this.closeMediaSelection();
      this.mediaNavList = points;
      this.mediaSheetMediaId = points.some((point) => point.id === selection.selectedMediaId)
        ? selection.selectedMediaId
        : (points[0]?.id ?? null);
      this.mediaNavTotal = useViewport ? points.length : selection.totalMediaCount;
      this.mediaNavOffset = useViewport ? 0 : selection.offset;
      this.mediaNavClusterId = useViewport ? null : selection.clusterId;
      this.mediaNavPageSize = Math.max(1, points.length);
      this.mediaNavLoading = false;
      this.mediaNavScope = useViewport
        ? 'viewport'
        : selection.kind === 'cluster'
          ? 'cluster'
          : selection.totalMediaCount === 1
            ? 'photo'
            : 'location';
      this.mediaSheetVisible = true;
    },

    openMediaSelectionActivities() {
      const trackOptions = [...this.mediaSelectionTrackOptions];
      if (trackOptions.length === 0) return;
      this.closeMediaSelection();
      if (trackOptions.length === 1 && trackOptions[0].matchedMediaCount > 0) {
        this.onTrackBrowserOpenPhotos(trackOptions[0].trackId);
        return;
      }
      this.selectionPopupTrackIds = trackOptions.map((option) => option.trackId);
      this.selectionPopupMediaOptions = trackOptions;
      this.trackSelectionPurpose = 'photos';
      this.trackSelectionSheetVisible = true;
    },

    closeMediaSelection() {
      this.mediaSelectionRequestToken += 1;
      this.mediaSelectionAbortController?.abort();
      this.mediaSelectionAbortController = null;
      this.mediaSelectionSheetVisible = false;
      this.mediaPendingSelection = null;
      this.mediaSelectionTrackOptions = [];
      this.mediaSelectionTracksLoading = false;
    },

    navigateMediaTo(id) {
      if (id != null) this.mediaSheetMediaId = id;
    },

    async navigateMediaRelative(delta) {
      if (this.mediaNavLoading) return;
      const currentLocalIndex = this.mediaCurrentIndex;
      if (currentLocalIndex < 0) return;
      const targetGlobalIndex = this.mediaNavOffset + currentLocalIndex + delta;
      if (targetGlobalIndex < 0 || targetGlobalIndex >= this.mediaNavTotal) return;

      const localTargetIndex = targetGlobalIndex - this.mediaNavOffset;
      const localTargetId = this.mediaNavList[localTargetIndex]?.id;
      if (localTargetId != null) {
        this.mediaSheetMediaId = localTargetId;
        return;
      }

      const clusterId = this.mediaNavClusterId;
      const pageSize = this.mediaNavPageSize;
      const loadClusterPage = this.mediaOverlay?.getClusterPage;
      if (clusterId == null || pageSize <= 0 || !loadClusterPage) return;

      const targetOffset = Math.floor(targetGlobalIndex / pageSize) * pageSize;
      const requestToken = ++this.mediaNavRequestToken;
      this.mediaNavLoading = true;
      try {
        const page = await loadClusterPage.call(
          this.mediaOverlay,
          clusterId,
          targetOffset,
          pageSize,
          this.mediaNavTotal
        );
        if (requestToken !== this.mediaNavRequestToken || !this.mediaSheetVisible) return;
        const target = page.mediaPoints[targetGlobalIndex - page.offset];
        if (target?.id == null) return;
        const buffer = mergeAdjacentMediaPage(
          this.mediaNavList,
          this.mediaNavOffset,
          page.mediaPoints,
          page.offset,
          pageSize,
          delta
        );
        this.mediaNavList = buffer.items;
        this.mediaNavOffset = buffer.offset;
        this.mediaSheetMediaId = target.id;
      } finally {
        if (requestToken === this.mediaNavRequestToken) this.mediaNavLoading = false;
      }
    },

    async navigateMediaPage(direction) {
      if (this.mediaNavLoading || this.mediaNavClusterId == null) return;
      const targetGlobalIndex =
        direction < 0 ? this.mediaNavOffset - 1 : this.mediaNavOffset + this.mediaNavList.length;
      if (targetGlobalIndex < 0 || targetGlobalIndex >= this.mediaNavTotal) return;

      const pageSize = this.mediaNavPageSize;
      const loadClusterPage = this.mediaOverlay?.getClusterPage;
      if (pageSize <= 0 || !loadClusterPage) return;

      const targetOffset = Math.floor(targetGlobalIndex / pageSize) * pageSize;
      const requestToken = ++this.mediaNavRequestToken;
      this.mediaNavLoading = true;
      try {
        const page = await loadClusterPage.call(
          this.mediaOverlay,
          this.mediaNavClusterId,
          targetOffset,
          pageSize,
          this.mediaNavTotal
        );
        if (requestToken !== this.mediaNavRequestToken || !this.mediaSheetVisible) return;
        const target = page.mediaPoints[targetGlobalIndex - page.offset];
        if (target?.id == null) return;
        const buffer = mergeAdjacentMediaPage(
          this.mediaNavList,
          this.mediaNavOffset,
          page.mediaPoints,
          page.offset,
          pageSize,
          direction
        );
        this.mediaNavList = buffer.items;
        this.mediaNavOffset = buffer.offset;
        this.mediaSheetMediaId = target.id;
      } finally {
        if (requestToken === this.mediaNavRequestToken) this.mediaNavLoading = false;
      }
    },

    closeMediaSheet() {
      this.mediaNavRequestToken += 1;
      this.mediaSheetMediaId = null;
      this.mediaNavList = [];
      this.mediaNavTotal = 0;
      this.mediaNavOffset = 0;
      this.mediaNavClusterId = null;
      this.mediaNavPageSize = 0;
      this.mediaNavLoading = false;
      this.mediaNavScope = 'photo';
    },

    clearFocusedMediaMarker() {
      if (!this.focusedMediaMarker) return;
      this.focusedMediaMarker.remove();
      this.focusedMediaMarker = null;
    },

    focusMediaOnMainMap(point) {
      if (!Number.isFinite(point.lat) || !Number.isFinite(point.lng) || !this.overlayMap) return;
      this.mediaOverlay?.prepareForFocus?.(point);
      this.clearFocusedMediaMarker();
      const markerElement = document.createElement('div');
      markerElement.className = 'mtl-focused-media-marker';
      markerElement.setAttribute('role', 'img');
      markerElement.setAttribute('aria-label', MEDIA_FOCUS_MARKER_LABEL);
      const icon = document.createElement('i');
      icon.className = 'bi bi-camera-fill';
      icon.setAttribute('aria-hidden', 'true');
      markerElement.append(icon);
      this.focusedMediaMarker = markRaw(
        new maplibregl.Marker({ element: markerElement, anchor: 'center' })
          .setLngLat([point.lng, point.lat])
          .addTo(this.overlayMap)
      );
      this.overlayMap.stop();
      this.overlayMap.jumpTo({
        center: [point.lng, point.lat],
        zoom: Math.max(this.overlayMap.getZoom(), MEDIA_FOCUS_MIN_ZOOM),
      });
    },

    _buildMediaNavList(originId, selectedIds) {
      if (!this.mediaLoadedPoints.length) {
        this.mediaNavList = [];
        return;
      }

      const selectedIdSet = selectedIds ? new Set(selectedIds) : null;
      const viewBounds = selectedIdSet ? null : this.overlayMap?.getBounds();
      const visiblePoints = this.mediaLoadedPoints.filter((point) => {
        if (point.id == null) return false;
        if (selectedIdSet) return selectedIdSet.has(point.id);
        return viewBounds
          ? point.lat >= viewBounds.getSouth() &&
              point.lat <= viewBounds.getNorth() &&
              point.lng >= viewBounds.getWest() &&
              point.lng <= viewBounds.getEast()
          : true;
      });
      const origin = visiblePoints.find((p) => p.id === originId) ?? visiblePoints[0];
      if (!origin) {
        this.mediaNavList = visiblePoints;
        return;
      }
      const dist = (p: (typeof visiblePoints)[number]) => {
        const dlat = p.lat - origin.lat,
          dlng = p.lng - origin.lng;
        return dlat * dlat + dlng * dlng;
      };
      this.mediaNavList = visiblePoints.sort((a, b) => dist(a) - dist(b));
    },
  };
  return methods;
}
