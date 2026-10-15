import { markRaw } from 'vue';
import { MAP_POINTER_TOLERANCE_PX, type MapTargetCandidate } from '../mapInteractionDecision';
import * as maplibregl from 'maplibre-gl';
import { GeoDrawingOverlay } from '@/layers/GeoDrawingOverlay';
import { locationSearchTargetZoom as resolveLocationSearchTargetZoom } from '@/components/map/mapGeometry';
import type { MapControllerMethodDefinitions, MapToolsMethods } from './mapControllerRuntime';

const LOCATION_SEARCH_FLY_DURATION_MS = 900;
const TRACK_DETAILS_MAP_DETENT = 'compact';
const TRACK_DETAILS_DEFAULT_DETENT = 'default';
const TRACK_DETAILS_EXPANDED_DETENT = 'expanded';
export const TRACK_DETAILS_DESKTOP_DETENT = 'desktop';
export function resolveTrackDetailsOpeningDetent(detent: string): string {
  return [TRACK_DETAILS_MAP_DETENT, TRACK_DETAILS_DEFAULT_DETENT, TRACK_DETAILS_DESKTOP_DETENT].includes(detent)
    ? TRACK_DETAILS_EXPANDED_DETENT
    : detent;
}

const TOOL_REF_NAMES = [
  'infoTool',
  'animateTool',
  'measureTool',
  'plannerTool',
  'statistics',
  'healthTool',
  'reviewTool',
  'filterTool',
  'mapSettingsTool',
  'gpsLocate',
  'adminTool',
] as const;
const TOOL_REF_BY_ID: Record<string, string> = {
  animate: 'animateTool',
  measure: 'measureTool',
  planner: 'plannerTool',
  stats: 'statistics',
  health: 'healthTool',
  review: 'reviewTool',
  filter: 'filterTool',
  map: 'mapSettingsTool',
  gps: 'gpsLocate',
  admin: 'adminTool',
};

export function useMapTools(_deps: Record<string, never> = {}): MapControllerMethodDefinitions<MapToolsMethods> {
  const methods: MapControllerMethodDefinitions<MapToolsMethods> = {
    openLocationSearch() {
      this.endMapInteraction();
      this.closeAllToolsExcept(null);
      this.closeTransientOverlaysForToolSwitch();
      this.closeSelectionPopup();
      this.activeToolId = null;
      this.locationSearchVisible = true;
    },

    onLocationSearchSelect(result) {
      const lat = Number(result?.lat);
      const lon = Number(result?.lon);
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;
      this.closeSelectionPopup();
      this.deselectTrack();
      this.locationSearchVisible = false;
      this.mapCenter = [lon, lat];
      this.setLocationSearchMarker(lon, lat);
      const targetMap = this.overlayMap;
      targetMap?.flyTo({
        center: [lon, lat],
        zoom: this.locationSearchTargetZoom(result),
        duration: LOCATION_SEARCH_FLY_DURATION_MS,
        essential: true,
      });
    },

    setLocationSearchMarker(lon, lat) {
      if (!this.overlayMap) return;
      this.clearLocationSearchMarker();
      const el = document.createElement('div');
      el.className = 'mtl-location-search-marker';
      el.innerHTML = `
    <i class="bi bi-geo-alt-fill"></i>
    <button class="mtl-location-search-marker__clear" type="button" aria-label="Clear search marker" title="Clear">
      <i class="bi bi-x"></i>
    </button>
  `;
      el.querySelector('.mtl-location-search-marker__clear')?.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        this.clearLocationSearchMarker();
      });
      this.locationSearchMarker = markRaw(
        new maplibregl.Marker({
          element: el,
          anchor: 'bottom',
        })
          .setLngLat([lon, lat])
          .addTo(this.overlayMap)
      );
    },

    clearLocationSearchMarker() {
      if (!this.locationSearchMarker) return;
      this.locationSearchMarker.remove();
      this.locationSearchMarker = null;
    },

    locationSearchTargetZoom(result) {
      return resolveLocationSearchTargetZoom(result);
    },

    onLocationUpdate(geolocationPosition) {
      const latitude = geolocationPosition.coords.latitude;
      const longitude = geolocationPosition.coords.longitude;
      this.mapCenter = [longitude, latitude];
      this.gpsLocation = [latitude, longitude];

      // Only auto-centre when in follow mode — user panning breaks this (see dragstart listener)
      if (this.gpsFollowing && this.overlayMap) {
        this.overlayMap.flyTo({
          center: [longitude, latitude],
          zoom: this.overlayMap.getZoom(),
        });
      }

      this.updateGpsMarker();
    },

    updateGpsMarker() {
      if (!this.gpsDeviceEnabledDisabled || !this.gpsLocation || !this.overlayMap) return;
      const [latitude, longitude] = this.gpsLocation;

      // Keep the marker visible even when the user pans away from follow mode.
      if (this.gpsMarker) {
        this.gpsMarker.setLngLat([longitude, latitude]);
      } else {
        const el = document.createElement('div');
        el.style.cssText =
          'width:16px;height:16px;border-radius:50%;background:red;border:2px solid white;box-shadow:0 0 4px rgba(0,0,0,0.4);';
        this.gpsMarker = markRaw(
          new maplibregl.Marker({
            element: el,
          })
            .setLngLat([longitude, latitude])
            .addTo(this.overlayMap)
        );
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
      console.log('map: onAnimationFinishedEvent');
    },

    onAnimationStopEvent() {
      // Timeline panel now self-manages its UI
    },

    onAnimateEvent(_event) {
      // available if needed for external integrations
    },

    onMeasureShowTrackDetails(id) {
      const trackId = Number(id);
      if (Number.isFinite(trackId)) this.openTrackDetails(trackId, TRACK_DETAILS_EXPANDED_DETENT);
    },

    onMeasureActiveChanged(isActive) {
      this.measureToolActive = isActive;
    },

    onPlannerActiveChanged(isActive) {
      this.plannerToolActive = isActive;
    },

    closeAllToolsExcept(skipRefName) {
      for (const name of TOOL_REF_NAMES) {
        const ref = this.$refs[name];
        if (name !== skipRefName && ref?.close) {
          ref.close();
        }
      }
    },

    closeTransientOverlaysForToolSwitch() {
      this.endMapInteraction();
      if (this.trackReplayActive) {
        this.stop3dTrackReplay();
      }
      const hadTrackDetails = this.trackDetailsVisible;
      this.trackDetailsVisible = false;
      this.trackDetailsId = null;
      this.trackDetailsSelectedDetent = undefined;
      this.trackDetailsInfo = {
        id: null,
        name: '',
        description: '',
        activityType: '',
      };
      if (hadTrackDetails) this.deselectTrack();
      this.closeMediaSelection();
      this.mediaSheetVisible = false;
      this.closeMediaSheet();
      this.trackSelectionSheetVisible = false;
      this.locationSearchVisible = false;
      this.clearLocationSearchMarker();
      this.closeSelectionPopup();
    },

    captureActiveToolNavigation() {
      const toolId = this.activeToolId;
      const refName = toolId ? TOOL_REF_BY_ID[toolId] : undefined;
      const toolRef = refName ? this.$refs[refName] : undefined;
      this.trackDetailsReturnTarget = toolId
        ? {
            toolId,
            state: toolRef?.getNavigationState?.(),
          }
        : null;
    },

    restoreToolNavigation(toolId) {
      const target = this.trackDetailsReturnTarget;
      if (!target) return;
      if (target.toolId !== toolId) {
        this.trackDetailsReturnTarget = null;
        return;
      }

      const refName = TOOL_REF_BY_ID[toolId];
      const toolRef = refName ? this.$refs[refName] : undefined;
      this.$nextTick(() => {
        toolRef?.restoreNavigationState?.(target.state);
        this.trackDetailsReturnTarget = null;
      });
    },

    onToolSelect(toolId) {
      if (!toolId) return;
      const refName = TOOL_REF_BY_ID[toolId];
      if (!refName) return;
      const returningFromTrack = this.trackDetailsVisible && this.trackDetailsReturnTarget?.toolId === toolId;
      this.closeTransientOverlaysForToolSwitch();

      // ── GPS: 3-state cycle ──────────────────────────────────────────────────
      // OFF  → tap → ON + following (blue dot, map tracks position)
      // ON + following → drag map → drifted (amber dot, marker still visible)
      // ON + drifted  → tap → re-centre + re-engage following (blue dot again)
      // ON + following → tap → OFF (same as: turn off GPS)
      if (toolId === 'gps') {
        if (this.gpsDeviceEnabledDisabled && !this.gpsFollowing) {
          // Re-centre and re-engage following without toggling GPS off
          this.gpsFollowing = true;
          if (this.gpsLocation && this.overlayMap) {
            this.overlayMap.flyTo({
              center: [this.gpsLocation[1], this.gpsLocation[0]],
              zoom: this.overlayMap.getZoom(),
            });
          }
          this.activeToolId = null;
          return;
        }
        // Otherwise: toggle GPS on/off (GpsLocate handles the watchPosition lifecycle)
        this.closeAllToolsExcept(refName);
        this.$refs[refName]?.toggle?.();
        this.activeToolId = null;
        return;
      }
      // ── All other tools ─────────────────────────────────────────────────────

      const isTogglingOff = this.activeToolId === toolId;
      this.closeAllToolsExcept(refName);
      const ref = this.$refs[refName];
      if (returningFromTrack && ref?.open) {
        ref.open();
        this.restoreToolNavigation(toolId);
      } else if (ref?.toggle) {
        ref.toggle();
      }
      if (toolId === 'gps') {
        this.activeToolId = null;
      } else {
        this.activeToolId = isTogglingOff ? null : toolId;
      }
    },

    syncToolToRoute(toolId) {
      if (!toolId) {
        this.closeAllToolsExcept(null);
        this.activeToolId = null;
        this.trackDetailsReturnTarget = null;
        return;
      }
      const refName = TOOL_REF_BY_ID[toolId];
      if (!refName) return;
      this._syncingToolRoute = true;
      this.closeTransientOverlaysForToolSwitch();
      this.closeAllToolsExcept(refName);
      const ref = this.$refs[refName];
      if (ref?.open) {
        ref.open();
      } else if (this.activeToolId !== toolId) {
        ref?.toggle?.();
      }
      this.activeToolId = toolId === 'gps' ? null : toolId;
      this.restoreToolNavigation(toolId);
      this.$nextTick(() => {
        this.activeToolId = toolId === 'gps' ? null : toolId;
        this._syncingToolRoute = false;
      });
    },

    syncTrackDetailRoute(trackId) {
      const normalizedTrackId = Number(trackId);
      if (trackId == null || !Number.isFinite(normalizedTrackId)) return;
      if (!this.trackDetailsVisible && !this.trackDetailsReturnTarget) {
        this.captureActiveToolNavigation();
      }
      const returnToolId = this.trackDetailsReturnTarget?.toolId;
      const returnToolRef = returnToolId ? TOOL_REF_BY_ID[returnToolId] : null;
      this.closeAllToolsExcept(returnToolRef ?? null);
      this.activeToolId = null;
      if (
        this.trackDetailsVisible &&
        Number(this.trackDetailsId) === normalizedTrackId &&
        Number(this.selectedTrackId) === normalizedTrackId
      ) {
        this.closeSelectionPopup();
        return;
      }
      this.closeTransientOverlaysForToolSwitch();
      if (Number(this.selectedTrackId) === normalizedTrackId) {
        this.applySelectedTrackHighlight?.();
      } else {
        this.selectTrackById(normalizedTrackId);
      }
      this.openTrackDetails(normalizedTrackId, TRACK_DETAILS_EXPANDED_DETENT);
    },

    onToolOpened(refName) {
      this.closeTransientOverlaysForToolSwitch();
      this.closeAllToolsExcept(refName);
      const idMap: Record<string, string> = {
        animateTool: 'animate',
        measureTool: 'measure',
        plannerTool: 'planner',
        statistics: 'stats',
        healthTool: 'health',
        reviewTool: 'review',
        filterTool: 'filter',
        mapSettingsTool: 'map',
        gpsLocate: 'gps',
        adminTool: 'admin',
      };
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
      if (this._syncingToolRoute) return;
      this.activeToolId = null;
      // Clear geo shape overlays when filter sheet is closed (unless actively drawing)
      if (!this.geoDrawingParamDef && this.geoDrawingOverlay) {
        this.geoDrawingOverlay.clearAll();
      }
    },

    async identifySwissMobilityRoutes(lngLat, _point, signal, tolerance = MAP_POINTER_TOLERANCE_PX) {
      if (!this.overlayMap) return [];
      const layers: Record<string, { bodId: string; type: string; icon: string }> = {
        wanderland: { bodId: 'ch.astra.wanderland', type: 'Hiking', icon: 'bi bi-signpost-2' },
        veloland: { bodId: 'ch.astra.veloland', type: 'Bike', icon: 'bi bi-bicycle' },
        mountainbikeland: { bodId: 'ch.astra.mountainbikeland', type: 'Mountainbike', icon: 'bi bi-bicycle' },
        wanderwege: { bodId: 'ch.swisstopo.swisstlm3d-wanderwege', type: 'Trail', icon: 'bi bi-signpost' },
      };
      const activeLayers = this.activeOverlays
        .filter((id) => layers[id] && this.layerOpacities[id] > 0 && this.overlayMap?.getLayer(`${id}-overlay`))
        .map((id) => layers[id]);
      if (!activeLayers.length) return [];
      const bounds = this.overlayMap.getBounds();
      const canvas = this.overlayMap.getCanvas();
      const params = new URLSearchParams({
        geometry: `${lngLat.lng},${lngLat.lat}`,
        geometryType: 'esriGeometryPoint',
        layers: `all:${activeLayers.map((layer) => layer.bodId).join(',')}`,
        mapExtent: `${bounds.getWest()},${bounds.getSouth()},${bounds.getEast()},${bounds.getNorth()}`,
        imageDisplay: `${canvas.clientWidth || canvas.width},${canvas.clientHeight || canvas.height},96`,
        tolerance: String(tolerance),
        lang: 'en',
        returnGeometry: 'false',
        sr: '4326',
      });
      const response = await fetch(`https://api3.geo.admin.ch/rest/services/api/MapServer/identify?${params}`, {
        signal,
      });
      if (!response.ok) throw new Error(`Route identification failed (${response.status})`);
      const data = (await response.json()) as {
        results?: Array<{
          id?: string | number;
          featureId?: string | number;
          layerBodId?: string;
          layerName?: string;
          attributes?: Record<string, unknown> & {
            chmobil_title?: string;
            label?: string;
            chmobil_route_number?: string | number;
          };
        }>;
      };
      const candidates: MapTargetCandidate[] = [];
      for (const result of data.results ?? []) {
        const layer = activeLayers.find((entry) => entry.bodId === result.layerBodId);
        if (!layer) continue;
        const attributes = result.attributes ?? {};
        const name = attributes.chmobil_title || attributes.label || layer.type;
        // Stable feature IDs distinguish separate routes; the attribute fallback removes repeated rendered parts.
        const featureId = result.featureId ?? result.id ?? attributes.id ?? attributes.objectid;
        const identity =
          result.featureId ??
          result.id ??
          attributes.id ??
          attributes.objectid ??
          JSON.stringify(Object.fromEntries(Object.entries(attributes).sort(([a], [b]) => a.localeCompare(b))));
        candidates.push({
          id: String(identity),
          kind: 'overlay',
          source: layer.bodId,
          sourceLabel: result.layerName || layer.type,
          label: name,
          detail: attributes.chmobil_route_number != null ? `Route #${attributes.chmobil_route_number}` : layer.type,
          match: 'identified',
          route: {
            name,
            type: layer.type,
            icon: layer.icon,
            number: attributes.chmobil_route_number,
            featureId: featureId == null ? undefined : String(featureId),
            classification: typeof attributes.wanderwegtyp === 'string' ? attributes.wanderwegtyp : undefined,
          },
        });
      }
      return candidates;
    },

    showTrackSelectionPopup(point, trackIds, processing) {
      this.closeSelectionPopup();
      this.selectionPopupTrackIds = trackIds;
      this.selectionPopupProcessing = processing ?? null;
      this.selectionPopupMediaOptions = [];
      this.trackSelectionPurpose = 'details';
      this.trackSelectionSheetVisible = true;
    },

    closeSelectionPopup() {
      this.trackSelectionSheetVisible = false;
      this.selectionPopupTrackIds = [];
      this.selectionPopupProcessing = null;
      this.selectionPopupMediaOptions = [];
      this.trackSelectionPurpose = 'details';
    },

    onPopupTrackSelect(id) {
      const purpose = this.trackSelectionPurpose;
      const mediaOption = this.selectionPopupMediaOptions.find((option) => option.trackId === id);
      this.closeSelectionPopup();
      if (purpose === 'photos' && (mediaOption?.matchedMediaCount ?? 0) > 0) {
        if (this.mapInteraction) {
          this.mapInteractionDetailKind = 'track';
          if (this.selectedTrackId !== id) this.selectTrackById(id);
          this.openTrackDetails(id, TRACK_DETAILS_EXPANDED_DETENT, 'photos');
        } else this.onTrackBrowserOpenPhotos(id);
        return;
      }
      if (this.mapInteraction) this.mapInteractionDetailKind = 'track';
      this.selectTrackById(id);
      this.openTrackDetails(id, TRACK_DETAILS_MAP_DETENT);
    },

    openTrackDetails(trackId, initialDetent = TRACK_DETAILS_DEFAULT_DETENT, initialTab = 'overview') {
      const normalizedTrackId = Number(trackId ?? this.selectedTrackId);
      if (!Number.isFinite(normalizedTrackId)) return;
      if (!this.trackDetailsVisible && !this.trackDetailsReturnTarget) {
        this.captureActiveToolNavigation();
      }
      const feature = this.gpsTrackIdToFeature.get(normalizedTrackId) || this.selectedFeature;
      const p = feature?.properties;
      this.trackDetailsId = normalizedTrackId;
      this.trackDetailsInfo = {
        id: normalizedTrackId,
        name: p?.trackName || p?.trackDescription || '',
        description: '',
        activityType: p?.activityType || '',
      };
      this.trackDetailsInitialDetent = resolveTrackDetailsOpeningDetent(initialDetent);
      this.trackDetailsInitialTab = initialTab;
      this.trackDetailsVisible = true;
    },

    onTrackDetailsSheetClosed() {
      if (this.trackReplayActive) {
        this.stop3dTrackReplay();
      }
      this.trackDetailsId = null;
      this.trackDetailsInfo = {
        id: null,
        name: '',
        description: '',
        activityType: '',
      };
      this.trackDetailsSelectedDetent = undefined;
      this.trackDetailsInitialTab = 'overview';
      this.deselectTrack();
    },

    onTrackDetailsLoaded({ id, name, description, activityType }) {
      if (Number(id) !== Number(this.trackDetailsId)) return;
      this.trackDetailsInfo = {
        id,
        name,
        description,
        activityType: activityType || '',
      };
    },

    onTrackMetadataUpdated(track) {
      const id = Number(track.id);
      const previous = this.gpsTracksById.get(id);
      if (!previous) return;
      if (previous.version !== track.version) {
        // Metadata can arrive before geometry. Let the next geometry response
        // replace the old shape even when its precision is coarser.
        this.trackPrecisions.delete(id);
        this.trackPointsCanonicalCache.delete(id);
        for (const key of this.trackPointsDetailsCache.keys()) {
          if (key.startsWith(`${id}_`)) this.trackPointsDetailsCache.delete(key);
        }
      }
      this.gpsTracksById.set(id, { ...previous, ...track });
      this.publishGpsTrackMetadataChanges();
    },
  };
  return methods;
}
