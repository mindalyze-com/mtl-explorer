import * as maplibregl from 'maplibre-gl';
import { formatDate } from '@/utils/Utils';
import { buildTrackOverviewFeatures } from '@/components/map/mapGeometry';
import { TRACK_COLOR, TRACK_SELECTED_COLOR } from '@/utils/trackColors';
import { createArrowImage } from './useTrackPointLayer';
import type { MapControllerMethodDefinitions, TrackLayersMethods } from './mapControllerRuntime';

const TRACK_REPLAY_HIDDEN_TRACK_FILTER = ['==', ['get', 'id'], -1];
const TRACK_POINTS_MIN_ZOOM = 16;
const TRACK_DETAILS_EXPANDED_DETENT = 'expanded';
const TRACKS_SOURCE_ID = 'tracks';
const SELECTED_TRACK_SOURCE_ID = 'selected-track';
const TRACK_POINTS_SOURCE_ID = 'track-points';
const TRACKS_OVERVIEW_SOURCE_ID = 'tracks-overview';
const TRACKS_LINE_LAYER_ID = 'tracks-layer';
const TRACKS_DOT_LAYER_ID = 'tracks-dot-layer';
const TRACKS_OVERVIEW_DOTS_LAYER_ID = 'tracks-overview-dots';
const TRACKS_HIGHLIGHT_LAYER_ID = 'tracks-highlight-layer';
const TRACKS_HIGHLIGHT_DASH_LAYER_ID = 'tracks-highlight-dash-layer';
const TRACKS_HIGHLIGHT_CIRCLE_LAYER_ID = 'tracks-highlight-circle-layer';
const TRACK_POINTS_LAYER_ID = 'track-points-layer';

export function useTrackLayers(_deps: Record<string, never> = {}): MapControllerMethodDefinitions<TrackLayersMethods> {
  const methods: MapControllerMethodDefinitions<TrackLayersMethods> = {
    currentTrackReplayTrackId() {
      if (!this.trackReplayActive) return null;
      const trackId = Number(this.trackReplayTrackId ?? this.selectedTrackId);
      return Number.isFinite(trackId) ? trackId : null;
    },

    hiddenGroupTrackFilter() {
      const hiddenGroups = this.hiddenGroups ?? new Set();
      if (hiddenGroups.size === 0) return null;
      return ['!', ['in', ['get', 'filterGroup'], ['literal', [...hiddenGroups]]]];
    },

    replayContextTrackFilter() {
      if (!this.trackReplayActive) return null;
      if (!this.trackReplayShowContextTracks) return TRACK_REPLAY_HIDDEN_TRACK_FILTER;
      const replayTrackId = this.currentTrackReplayTrackId();
      return replayTrackId == null ? null : ['!=', ['get', 'id'], replayTrackId];
    },

    trackLayerFilter(layerId) {
      const filters = [];
      if (layerId === TRACKS_DOT_LAYER_ID) {
        filters.push(['==', ['geometry-type'], 'Point']);
      }
      const hiddenGroupFilter = this.hiddenGroupTrackFilter();
      if (hiddenGroupFilter) filters.push(hiddenGroupFilter);
      const replayFilter = this.replayContextTrackFilter();
      if (replayFilter) filters.push(replayFilter);
      if (filters.length === 0) return null;
      if (filters.length === 1) return filters[0];
      return ['all', ...filters];
    },

    applyTrackLayerFilters() {
      if (!this.overlayMap) return;
      for (const layerId of [TRACKS_LINE_LAYER_ID, TRACKS_DOT_LAYER_ID, TRACKS_OVERVIEW_DOTS_LAYER_ID]) {
        if (this.overlayMap.getLayer(layerId)) {
          this.overlayMap.setFilter(layerId, this.trackLayerFilter(layerId) as maplibregl.FilterSpecification | null);
        }
      }
      this.updateSelectedTrackSource();
    },

    selectedTrackGeojson() {
      if (this.trackReplayActive) return emptySelectedTrackGeojson();
      const trackId = Number(this.selectedTrackId);
      if (!Number.isFinite(trackId)) return emptySelectedTrackGeojson();
      const selectedFeature =
        this.gpsTrackIdToFeature?.get?.(trackId) ??
        (Number(this.selectedFeature?.properties?.id) === trackId ? this.selectedFeature : null);
      if (!selectedFeature?.geometry) return emptySelectedTrackGeojson();
      return {
        type: 'FeatureCollection',
        features: [selectedFeature],
      };
    },

    updateSelectedTrackSource() {
      const source = this.overlayMap?.getSource(SELECTED_TRACK_SOURCE_ID);
      if (source) (source as unknown as { setData: (data: unknown) => void }).setData(this.selectedTrackGeojson());
    },

    markFlatTrackSourceDirty() {
      this.flatTrackSourceDirty = true;
    },

    // The flat MapLibre line/dot layers are the single source of truth for track
    // geometry in both 2D and 3D. MapLibre automatically drapes these 2D layers
    // over the terrain mesh when terrain is enabled, so no separate WebGL layer or
    // viewport reduction is needed — the full archive renders identically in 2D and 3D.
    // This stays cheap because the source carries simplified geometry (background/
    // overview precision, see trackConstants), not raw GPS. A worst-case benchmark
    // (~15k tracks / ~400k vertices, whole archive in view, terrain on, pitched,
    // rotating + panning) held a locked 60 FPS, so feeding the full set is intentional.
    flatTrackSourceGeojson() {
      return this.geojson ?? emptySelectedTrackGeojson();
    },

    syncFlatTrackSource({ force = false } = {}) {
      const source = this.overlayMap?.getSource(TRACKS_SOURCE_ID);
      if (!source || !this.geojson) return false;
      if (!force && !this.flatTrackSourceDirty) return false;
      (source as unknown as { setData: (data: unknown) => void }).setData(this.flatTrackSourceGeojson());
      this.flatTrackSourceDirty = false;
      return true;
    },

    applySelectedTrackHighlight() {
      this.updateSelectedTrackSource();
    },

    applyTrackRenderFilters() {
      this.applyTrackLayerFilters();
    },

    /** Show or hide all track-related layers. */
    applyTracksVisibility() {
      if (!this.overlayMap) return;
      const visibility = this.tracksEnabled ? 'visible' : 'none';
      this.syncFlatTrackSource();
      for (const id of [
        TRACKS_LINE_LAYER_ID,
        TRACKS_HIGHLIGHT_LAYER_ID,
        TRACKS_HIGHLIGHT_DASH_LAYER_ID,
        TRACKS_DOT_LAYER_ID,
        TRACKS_HIGHLIGHT_CIRCLE_LAYER_ID,
        TRACKS_OVERVIEW_DOTS_LAYER_ID,
      ]) {
        if (this.overlayMap.getLayer(id)) this.overlayMap.setLayoutProperty(id, 'visibility', visibility);
      }
    },

    /** Update the line width of highlight and dot layers based on current zoom. */
    updateTrackLineWidth() {
      if (!this.overlayMap) return;
      const zoom = this.overlayMap.getZoom();
      let lineWeight = 4;
      let correction = 14 - zoom * 1.3;
      if (correction < 0) correction = 0;
      lineWeight = Math.round(lineWeight + correction);

      // tracks-layer uses a GPU-native interpolate expression — no JS update needed.
      if (this.overlayMap.getLayer(TRACKS_HIGHLIGHT_LAYER_ID)) {
        this.overlayMap.setPaintProperty(TRACKS_HIGHLIGHT_LAYER_ID, 'line-width', lineWeight * 1.5);
      }
      if (this.overlayMap.getLayer(TRACKS_HIGHLIGHT_DASH_LAYER_ID)) {
        this.overlayMap.setPaintProperty(TRACKS_HIGHLIGHT_DASH_LAYER_ID, 'line-width', lineWeight * 1.5);
      }
      if (this.overlayMap.getLayer(TRACKS_DOT_LAYER_ID)) {
        this.overlayMap.setPaintProperty(TRACKS_DOT_LAYER_ID, 'circle-radius', Math.round(lineWeight / 2));
      }
      if (this.overlayMap.getLayer(TRACKS_HIGHLIGHT_CIRCLE_LAYER_ID)) {
        this.overlayMap.setPaintProperty(
          TRACKS_HIGHLIGHT_CIRCLE_LAYER_ID,
          'circle-radius',
          Math.round((lineWeight / 2) * 1.5)
        );
      }
    },

    /** Fit the map viewport to the bounds of the given GeoJSON FeatureCollection. */
    fitToTrackBounds(geojson) {
      if (!this.overlayMap || !geojson?.features?.length) return;
      const bounds = new maplibregl.LngLatBounds();
      for (const f of geojson.features) {
        if (!f.geometry) continue;
        if (f.geometry.type === 'Point') {
          const [lng, lat] = f.geometry.coordinates;
          if (Number.isFinite(lng) && Number.isFinite(lat)) bounds.extend([lng, lat]);
        } else if (f.geometry.type === 'LineString') {
          for (const coord of f.geometry.coordinates) {
            const [lng, lat] = coord;
            if (Number.isFinite(lng) && Number.isFinite(lat)) bounds.extend([lng, lat]);
          }
        }
      }
      if (!bounds.isEmpty()) {
        this.overlayMap.fitBounds(bounds, {
          padding: 40,
          maxZoom: 14,
        });
      }
    },

    /** Update the MapLibre 'tracks' source with the current geojson data. */
    updateTracksSource() {
      if (!this.overlayMap || !this.geojson) return;
      this.markFlatTrackSourceDirty();
      this.syncFlatTrackSource();
      const overviewSource = this.overlayMap.getSource(TRACKS_OVERVIEW_SOURCE_ID);
      if (overviewSource) {
        (overviewSource as unknown as { setData: (data: unknown) => void }).setData({
          type: 'FeatureCollection',
          features: buildTrackOverviewFeatures(this.geojson),
        });
      }
      // Keep heatmap density in sync as track precision improves
      if (this.heatmapOverlay) {
        this.heatmapOverlay.updateData?.(this.geojson);
      }
      this.applyTrackRenderFilters();
      this.applyTracksVisibility();
      this.applyLayerOpacity('tracks');
      this.updateTrackLineWidth();
      this.applyGroupFilter();
    },

    async addTracksToMap() {
      if (!this.overlayMap || !this.geojson) return;
      const startedAt = performance.now();
      this.visibleTrackCount = 0;
      const filterConfigId =
        (this.activeTrackFilterResult as { filterConfigId?: number } | null | undefined)?.filterConfigId ?? null;
      if (filterConfigId !== this.renderedFilterConfigId) {
        this.hiddenGroups = new Set();
      }
      this.renderedFilterConfigId = filterConfigId;
      const lineColor = (await this.resolveTrackLineColor()) as maplibregl.DataDrivenPropertyValueSpecification<string>;
      this.visibleTrackCount = this.geojson.features.length;
      this.detachTrackPointLayerHandlers();

      // Remove old track layers and sources.
      for (const layerId of [
        TRACK_POINTS_LAYER_ID,
        TRACKS_HIGHLIGHT_CIRCLE_LAYER_ID,
        TRACKS_DOT_LAYER_ID,
        TRACKS_OVERVIEW_DOTS_LAYER_ID,
        TRACKS_HIGHLIGHT_DASH_LAYER_ID,
        TRACKS_HIGHLIGHT_LAYER_ID,
        TRACKS_LINE_LAYER_ID,
      ]) {
        if (this.overlayMap.getLayer(layerId)) this.overlayMap.removeLayer(layerId);
      }
      for (const sourceId of [
        TRACK_POINTS_SOURCE_ID,
        SELECTED_TRACK_SOURCE_ID,
        TRACKS_OVERVIEW_SOURCE_ID,
        TRACKS_SOURCE_ID,
      ]) {
        if (this.overlayMap.getSource(sourceId)) this.overlayMap.removeSource(sourceId);
      }

      // Add the track GeoJSON sources.
      this.overlayMap.addSource(TRACKS_SOURCE_ID, {
        type: 'geojson',
        data: this.flatTrackSourceGeojson(),
      });
      this.flatTrackSourceDirty = false;
      this.overlayMap.addSource(SELECTED_TRACK_SOURCE_ID, {
        type: 'geojson',
        data: this.selectedTrackGeojson(),
      });

      // Main tracks layer
      this.overlayMap.addLayer({
        id: TRACKS_LINE_LAYER_ID,
        type: 'line',
        source: TRACKS_SOURCE_ID,
        layout: {
          'line-join': 'round',
          'line-cap': 'round',
        },
        paint: {
          'line-color': lineColor,
          'line-width': ['interpolate', ['linear'], ['zoom'], 0, 5, 7, 4, 14, 3.5, 22, 2],
          'line-opacity': 1,
        },
      });

      // Highlight layer (solid amber, only shows selected track)
      this.overlayMap.addLayer({
        id: TRACKS_HIGHLIGHT_LAYER_ID,
        type: 'line',
        source: SELECTED_TRACK_SOURCE_ID,
        layout: {
          'line-join': 'round',
          'line-cap': 'round',
        },
        paint: {
          'line-color': TRACK_SELECTED_COLOR,
          'line-width': 6,
          'line-opacity': 1,
        },
      });

      // Highlight dash layer (white dashed overlay on selected)
      this.overlayMap.addLayer({
        id: TRACKS_HIGHLIGHT_DASH_LAYER_ID,
        type: 'line',
        source: SELECTED_TRACK_SOURCE_ID,
        layout: {
          'line-join': 'round',
          'line-cap': 'round',
        },
        paint: {
          'line-color': TRACK_COLOR,
          'line-width': 6,
          'line-opacity': 1,
          'line-dasharray': [2, 3],
        },
      });

      // Circle layer for degenerate tracks (trackLengthInMeter < 50 m) stored as Point features
      this.overlayMap.addLayer({
        id: TRACKS_DOT_LAYER_ID,
        type: 'circle',
        source: TRACKS_SOURCE_ID,
        filter: ['==', ['geometry-type'], 'Point'],
        paint: {
          'circle-color': lineColor,
          'circle-radius': 6,
          'circle-opacity': 1,
          'circle-stroke-color': this.mapThemeSelected === 'dark' ? '#ffffff' : '#1a1a1a',
          'circle-stroke-width': 1.5,
        },
      });

      // Highlight circle for a selected degenerate track
      this.overlayMap.addLayer({
        id: TRACKS_HIGHLIGHT_CIRCLE_LAYER_ID,
        type: 'circle',
        source: SELECTED_TRACK_SOURCE_ID,
        filter: ['==', ['geometry-type'], 'Point'],
        paint: {
          'circle-color': '#FFB300',
          'circle-radius': 9,
          'circle-opacity': 1,
        },
      });

      // ── Overview dots: one circle per track centre, visible at low zoom ──────
      // At world view, short/medium tracks are sub-pixel as lines. This dedicated
      // Point source shows a dot so every track is visible at any zoom level.
      // Long tracks remain visible as lines; the dot sits beneath them harmlessly.
      const overviewFeatures = buildTrackOverviewFeatures(this.geojson);
      this.overlayMap.addSource(TRACKS_OVERVIEW_SOURCE_ID, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: overviewFeatures,
        },
      });
      this.overlayMap.addLayer({
        id: TRACKS_OVERVIEW_DOTS_LAYER_ID,
        type: 'circle',
        source: TRACKS_OVERVIEW_SOURCE_ID,
        maxzoom: 10,
        paint: {
          'circle-color': lineColor,
          'circle-radius': 5,
          'circle-opacity': 0.85,
          'circle-stroke-color': this.mapThemeSelected === 'dark' ? '#ffffff' : '#1a1a1a',
          'circle-stroke-width': 1,
        },
      });

      // ── Individual GPS track points with direction arrows (visible at zoom 18+) ──
      if (!this.overlayMap.hasImage('track-point-arrow')) {
        const arrow = createArrowImage();
        this.overlayMap.addImage('track-point-arrow', arrow, {
          pixelRatio: arrow.pixelRatio,
        });
      }
      this.overlayMap.addSource(TRACK_POINTS_SOURCE_ID, {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: [],
        },
      });
      this.overlayMap.addLayer({
        id: TRACK_POINTS_LAYER_ID,
        type: 'symbol',
        source: TRACK_POINTS_SOURCE_ID,
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
      this.attachTrackPointLayerHandlers();

      // Re-apply heatmap if it was active (persists across theme changes and reloads)
      if (this.heatmapVisible && this.heatmapOverlay && this.geojson) {
        this.heatmapOverlay.show(this.geojson);
        this.applyLayerOpacity('heatmap');
      }
      this.updateTrackLineWidth();
      this.applyTrackRenderFilters();
      this.applyTracksVisibility();
      this.applyAllLayerOpacities();
      this.scheduleDetailCheck();
      console.log('[tracks] Map layers rebuilt', {
        durationMs: Math.round(performance.now() - startedAt),
        trackCount: this.geojson.features.length,
        overviewDotCount: overviewFeatures.length,
        hasPalette: this.legendEntries.length > 0,
        legendGroupCount: this.legendEntries.length,
        heatmapVisible: this.heatmapVisible,
      });
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
      this.selectedTrackId = trackId;
      this.selectedFeature = feature ?? null;
      this.applySelectedTrackHighlight();
    },

    deselectTrack() {
      this.selectedTrackId = null;
      this.selectedFeature = null;
      this.applySelectedTrackHighlight();
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
        name,
        displayName: name,
        description,
        date: date || 'No date',
        activityType: track?.activityType || '',
      };
    },

    onTrackBrowserSelect(trackId) {
      const normalizedTrackId = Number(trackId);
      if (!Number.isFinite(normalizedTrackId)) return;
      this.selectTrackById(normalizedTrackId);

      // Fit to track bounds
      const feature = this.gpsTrackIdToFeature.get(normalizedTrackId);
      const coords = lineCoordinatesFromFeature(feature);
      if (coords.length > 0 && this.overlayMap) {
        const bounds = coords.reduce((b, c) => b.extend(c), new maplibregl.LngLatBounds(coords[0], coords[0]));
        this.overlayMap.fitBounds(bounds, {
          padding: 32,
          maxZoom: Math.max(this.overlayMap.getZoom(), 15),
        });
      }
    },

    onTrackBrowserOpenDetails(trackId) {
      const normalizedTrackId = Number(trackId);
      if (!Number.isFinite(normalizedTrackId)) return;
      this.selectTrackById(normalizedTrackId);
      this.openTrackDetails(normalizedTrackId, TRACK_DETAILS_EXPANDED_DETENT);
    },

    onTrackBrowserOpenPhotos(trackId) {
      const normalizedTrackId = Number(trackId);
      if (!Number.isFinite(normalizedTrackId)) return;
      this.selectTrackById(normalizedTrackId);
      this.openTrackDetails(normalizedTrackId, TRACK_DETAILS_EXPANDED_DETENT, 'photos');
    },
  };
  return methods;
}

function emptySelectedTrackGeojson(): import('./mapControllerRuntime').TrackFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: [],
  };
}

function lineCoordinatesFromFeature(
  feature: import('./mapControllerRuntime').TrackFeature | null | undefined
): [number, number][] {
  if (feature?.geometry?.type !== 'LineString') return [];
  return feature.geometry.coordinates.filter(
    (coordinate): coordinate is [number, number] =>
      coordinate.length >= 2 && Number.isFinite(coordinate[0]) && Number.isFinite(coordinate[1])
  );
}
