import * as maplibregl from 'maplibre-gl';
import { getMediaInBounds } from '@/repositories/mediaRepository';
import type { MediaBoundsPoint } from '@/repositories/mediaRepository';
import { isAbortLikeError } from '@/utils/errors';

export type MediaState = 'idle' | 'visible' | 'error';
export type MediaOverlaySelection = {
  selectedMediaId: number;
  mediaIds: number[];
  mediaPoints: MediaBoundsPoint[];
  totalMediaCount: number;
  clusterId: number | null;
  offset: number;
  kind: 'cluster' | 'location';
  viewportMediaPoints: MediaBoundsPoint[];
  clickPoint: { x: number; y: number };
  clickLngLat: { lng: number; lat: number };
};

export type MediaClusterPage = {
  clusterId: number;
  offset: number;
  totalMediaCount: number;
  mediaPoints: MediaBoundsPoint[];
};

const DEBOUNCE_MS = 300;
const BOUNDS_PADDING = 2;
const MAX_CACHEABLE_MEDIA_FETCH_LATITUDE_SPAN_DEGREES = 45;
const MAX_CACHEABLE_MEDIA_FETCH_LONGITUDE_SPAN_DEGREES = 60;
export const MEDIA_CLUSTER_PAGE_SIZE = 200;
const MIN_LATITUDE = -90;
const MAX_LATITUDE = 90;

const SOURCE_ID = 'media-points';
const CLUSTER_LAYER = 'media-clusters';
const CLUSTER_COUNT_LAYER = 'media-cluster-count';
const UNCLUSTERED_LAYER = 'media-unclustered';

export class MediaOverlay {
  private map: maplibregl.Map;
  private readonly onMediaSelect: (selection: MediaOverlaySelection) => void;
  private readonly onPointsUpdated?: (points: MediaBoundsPoint[]) => void;
  private readonly isInteractionEnabled: () => boolean;
  private loadGeneration = 0;
  private abortController: AbortController | null = null;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private boundHandler: (() => void) | null = null;
  private clusterClickHandler: ((event: maplibregl.MapLayerMouseEvent) => void) | null = null;
  private mediaClickHandler: ((event: maplibregl.MapLayerMouseEvent) => void) | null = null;
  private clusterMouseEnterHandler: (() => void) | null = null;
  private clusterMouseLeaveHandler: (() => void) | null = null;
  private mediaMouseEnterHandler: (() => void) | null = null;
  private mediaMouseLeaveHandler: (() => void) | null = null;
  private lastFetchedBounds: maplibregl.LngLatBounds | null = null;
  private loadedPoints: MediaBoundsPoint[] = [];
  state: MediaState = 'idle';
  error: unknown = null;
  loading = false;

  constructor(
    map: maplibregl.Map,
    onMediaSelect: (selection: MediaOverlaySelection) => void,
    onPointsUpdated?: (points: MediaBoundsPoint[]) => void,
    isInteractionEnabled: () => boolean = () => true
  ) {
    this.map = map;
    this.onMediaSelect = onMediaSelect;
    this.onPointsUpdated = onPointsUpdated;
    this.isInteractionEnabled = isInteractionEnabled;
  }

  getLoadedPoints(): MediaBoundsPoint[] {
    return this.loadedPoints;
  }

  async show(): Promise<void> {
    if (this.state === 'visible') return;

    // Add GeoJSON source with clustering
    this.map.addSource(SOURCE_ID, {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: [] },
      cluster: true,
      clusterMaxZoom: 17,
      clusterRadius: 60,
    });

    // Cluster circles
    this.map.addLayer({
      id: CLUSTER_LAYER,
      type: 'circle',
      source: SOURCE_ID,
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': ['step', ['get', 'point_count'], '#f03', 10, '#d03', 50, '#a03'],
        'circle-radius': ['step', ['get', 'point_count'], 15, 10, 20, 50, 25],
        'circle-stroke-width': 2,
        'circle-stroke-color': '#fff',
      },
    });

    // Cluster count labels
    this.map.addLayer({
      id: CLUSTER_COUNT_LAYER,
      type: 'symbol',
      source: SOURCE_ID,
      filter: ['has', 'point_count'],
      layout: {
        'text-field': '{point_count_abbreviated}',
        'text-size': 12,
        'text-font': ['Noto Sans Regular'],
      },
      paint: {
        'text-color': '#fff',
      },
    });

    // Unclustered single points
    this.map.addLayer({
      id: UNCLUSTERED_LAYER,
      type: 'circle',
      source: SOURCE_ID,
      filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-color': '#f03',
        'circle-radius': 6,
        'circle-stroke-width': 1,
        'circle-stroke-color': '#c00',
      },
    });

    this.state = 'visible';

    // Prepare the cluster and visible-map collections. The user chooses which
    // collection to open before the viewer is shown.
    this.clusterClickHandler = (e) => {
      if (!this.isInteractionEnabled()) return;
      void this.selectCluster(e);
    };
    this.map.on('click', CLUSTER_LAYER, this.clusterClickHandler);

    // Prepare both the clicked location and the visible-map collection.
    this.mediaClickHandler = (e) => {
      if (!this.isInteractionEnabled()) return;
      if (!e.features?.length) return;
      const mediaPoints = uniqueMediaPoints(e.features, this.loadedPoints);
      const mediaIds = mediaPoints.map((point) => point.id);
      const selectedMediaId = mediaIds[0];
      if (selectedMediaId == null) return;
      this.onMediaSelect({
        selectedMediaId,
        mediaIds,
        mediaPoints,
        totalMediaCount: mediaIds.length,
        clusterId: null,
        offset: 0,
        kind: 'location',
        viewportMediaPoints: this.getViewportMediaPoints(selectedMediaId, mediaPoints),
        clickPoint: { x: e.point.x, y: e.point.y },
        clickLngLat: { lng: e.lngLat.lng, lat: e.lngLat.lat },
      });
    };
    this.map.on('click', UNCLUSTERED_LAYER, this.mediaClickHandler);

    // Cursor style
    this.clusterMouseEnterHandler = () => {
      this.map.getCanvas().style.cursor = this.isInteractionEnabled() ? 'pointer' : '';
    };
    this.clusterMouseLeaveHandler = () => {
      this.map.getCanvas().style.cursor = '';
    };
    this.mediaMouseEnterHandler = () => {
      this.map.getCanvas().style.cursor = this.isInteractionEnabled() ? 'pointer' : '';
    };
    this.mediaMouseLeaveHandler = () => {
      this.map.getCanvas().style.cursor = '';
    };
    this.map.on('mouseenter', CLUSTER_LAYER, this.clusterMouseEnterHandler);
    this.map.on('mouseleave', CLUSTER_LAYER, this.clusterMouseLeaveHandler);
    this.map.on('mouseenter', UNCLUSTERED_LAYER, this.mediaMouseEnterHandler);
    this.map.on('mouseleave', UNCLUSTERED_LAYER, this.mediaMouseLeaveHandler);

    // Bind moveend to reload markers for new viewport
    this.boundHandler = () => this.debouncedLoad();
    this.map.on('moveend', this.boundHandler);

    // Initial load for the current viewport
    await this.loadForCurrentBounds();
  }

  private getViewportMediaPoints(selectedMediaId: number, clickedPoints: MediaBoundsPoint[]): MediaBoundsPoint[] {
    const viewport = this.map.getBounds();
    const southWest = viewport.getSouthWest();
    const northEast = viewport.getNorthEast();
    const pointsById = new Map(
      this.loadedPoints
        .filter(
          (point) =>
            point.lat >= southWest.lat &&
            point.lat <= northEast.lat &&
            longitudeIsInBounds(point.lng, southWest.lng, northEast.lng)
        )
        .map((point) => [point.id, point])
    );

    const selectedPoint = clickedPoints.find((point) => point.id === selectedMediaId);
    if (selectedPoint) pointsById.set(selectedPoint.id, selectedPoint);

    const points = [...pointsById.values()];
    const origin = pointsById.get(selectedMediaId);
    if (!origin) return points;

    return points.sort((left, right) => {
      const leftDistance = squaredCoordinateDistance(left, origin);
      const rightDistance = squaredCoordinateDistance(right, origin);
      return leftDistance - rightDistance || left.id - right.id;
    });
  }

  hide(): void {
    this.cancelPendingLoad();
    if (this.boundHandler) {
      this.map.off('moveend', this.boundHandler);
      this.boundHandler = null;
    }
    this.removeLayerHandlers();
    // Remove layers then source
    for (const id of [CLUSTER_COUNT_LAYER, CLUSTER_LAYER, UNCLUSTERED_LAYER]) {
      if (this.map.getLayer(id)) this.map.removeLayer(id);
    }
    if (this.map.getSource(SOURCE_ID)) this.map.removeSource(SOURCE_ID);

    this.lastFetchedBounds = null;
    this.loadedPoints = [];
    this.state = 'idle';
    this.error = null;
    this.loading = false;
  }

  isVisible(): boolean {
    return this.state === 'visible';
  }

  async refresh(): Promise<void> {
    if (this.state !== 'visible') return;
    await this.loadForCurrentBounds(true);
  }

  destroy(): void {
    this.hide();
  }

  /**
   * Replace a potentially large viewport source before moving to one photo.
   * The normal move-end request restores the points for the focused viewport.
   */
  prepareForFocus(point: MediaBoundsPoint): void {
    if (this.state !== 'visible') return;
    this.cancelPendingLoad();
    this.lastFetchedBounds = null;
    this.updateSource([point]);
  }

  async getClusterPage(
    clusterId: number,
    offset: number,
    limit = MEDIA_CLUSTER_PAGE_SIZE,
    totalMediaCount = 0
  ): Promise<MediaClusterPage> {
    const source = this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    if (!source) {
      return { clusterId, offset, totalMediaCount, mediaPoints: [] };
    }
    const leaves = await source.getClusterLeaves(clusterId, limit, offset);
    return {
      clusterId,
      offset,
      totalMediaCount,
      mediaPoints: uniqueMediaPoints(leaves),
    };
  }

  private async selectCluster(event: maplibregl.MapLayerMouseEvent): Promise<void> {
    const feature = this.map.queryRenderedFeatures(event.point, { layers: [CLUSTER_LAYER] })[0];
    const clusterId = positiveInteger(feature?.properties?.cluster_id);
    const pointCount = positiveInteger(feature?.properties?.point_count);
    if (clusterId == null || pointCount == null) return;

    try {
      const page = await this.getClusterPage(clusterId, 0, MEDIA_CLUSTER_PAGE_SIZE, pointCount);
      if (this.state !== 'visible' || !this.isInteractionEnabled()) return;
      const mediaIds = page.mediaPoints.map((point) => point.id);
      const selectedMediaId = mediaIds[0];
      if (selectedMediaId == null) return;
      this.onMediaSelect({
        selectedMediaId,
        mediaIds,
        mediaPoints: page.mediaPoints,
        totalMediaCount: pointCount,
        clusterId,
        offset: 0,
        kind: 'cluster',
        viewportMediaPoints: this.getViewportMediaPoints(selectedMediaId, page.mediaPoints),
        clickPoint: { x: event.point.x, y: event.point.y },
        clickLngLat: { lng: event.lngLat.lng, lat: event.lngLat.lat },
      });
    } catch (error) {
      console.error('MediaOverlay: failed to resolve media cluster', error);
    }
  }

  private removeLayerHandlers(): void {
    if (this.clusterClickHandler) {
      this.map.off('click', CLUSTER_LAYER, this.clusterClickHandler);
      this.clusterClickHandler = null;
    }
    if (this.mediaClickHandler) {
      this.map.off('click', UNCLUSTERED_LAYER, this.mediaClickHandler);
      this.mediaClickHandler = null;
    }
    if (this.clusterMouseEnterHandler) {
      this.map.off('mouseenter', CLUSTER_LAYER, this.clusterMouseEnterHandler);
      this.clusterMouseEnterHandler = null;
    }
    if (this.clusterMouseLeaveHandler) {
      this.map.off('mouseleave', CLUSTER_LAYER, this.clusterMouseLeaveHandler);
      this.clusterMouseLeaveHandler = null;
    }
    if (this.mediaMouseEnterHandler) {
      this.map.off('mouseenter', UNCLUSTERED_LAYER, this.mediaMouseEnterHandler);
      this.mediaMouseEnterHandler = null;
    }
    if (this.mediaMouseLeaveHandler) {
      this.map.off('mouseleave', UNCLUSTERED_LAYER, this.mediaMouseLeaveHandler);
      this.mediaMouseLeaveHandler = null;
    }
  }

  private debouncedLoad(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.invalidatePendingRequest();
    this.debounceTimer = setTimeout(() => this.loadForCurrentBounds(), DEBOUNCE_MS);
  }

  private cancelPendingLoad(): void {
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = null;
    }
    this.invalidatePendingRequest();
  }

  private invalidatePendingRequest(): void {
    this.loadGeneration++;
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.loading = false;
  }

  private async loadForCurrentBounds(force = false): Promise<void> {
    if (this.state !== 'visible') return;
    this.invalidatePendingRequest();
    const generation = this.loadGeneration;

    const viewport = this.map.getBounds();

    if (force) this.lastFetchedBounds = null;

    const sw = viewport.getSouthWest();
    const ne = viewport.getNorthEast();
    const latPad = (ne.lat - sw.lat) * BOUNDS_PADDING;
    const lngPad = (ne.lng - sw.lng) * BOUNDS_PADDING;
    const fetchBounds = new maplibregl.LngLatBounds(
      [sw.lng - lngPad, clampLatitude(sw.lat - latPad)],
      [ne.lng + lngPad, clampLatitude(ne.lat + latPad)]
    );
    if (!isCacheableMediaFetchBounds(fetchBounds)) {
      this.lastFetchedBounds = null;
      this.error = null;
      if (this.loadedPoints.length > 0) this.updateSource([]);
      return;
    }

    // Skip a bounded fetch if the current viewport is already covered.
    if (this.lastFetchedBounds && this.lastFetchedBounds.contains(sw) && this.lastFetchedBounds.contains(ne)) return;

    const controller = new AbortController();
    this.abortController = controller;

    this.loading = true;
    try {
      const points = await getMediaInBounds(
        fetchBounds.getSouthWest().lat,
        fetchBounds.getSouthWest().lng,
        fetchBounds.getNorthEast().lat,
        fetchBounds.getNorthEast().lng,
        controller.signal
      );

      if (generation !== this.loadGeneration || controller.signal.aborted || this.state !== 'visible') return;

      this.lastFetchedBounds = fetchBounds;
      this.updateSource(points);
      this.error = null;
    } catch (e: unknown) {
      if (generation !== this.loadGeneration || isAbortLikeError(e, controller.signal)) return;
      this.error = e;
      console.error('MediaOverlay: failed to load media in bounds', e);
      if (force) throw e;
    } finally {
      if (generation === this.loadGeneration) {
        this.loading = false;
        if (this.abortController === controller) this.abortController = null;
      }
    }
  }

  private updateSource(points: MediaBoundsPoint[]): void {
    const source = this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    if (!source) return;

    this.loadedPoints = points;
    this.onPointsUpdated?.(points);

    const geojson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection',
      features: points.map((p) => ({
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [p.lng, p.lat] },
        properties: { mediaId: p.id },
      })),
    };
    source.setData(geojson);
  }
}

function isCacheableMediaFetchBounds(bounds: maplibregl.LngLatBounds): boolean {
  const southWest = bounds.getSouthWest();
  const northEast = bounds.getNorthEast();
  return (
    northEast.lat - southWest.lat <= MAX_CACHEABLE_MEDIA_FETCH_LATITUDE_SPAN_DEGREES &&
    northEast.lng - southWest.lng <= MAX_CACHEABLE_MEDIA_FETCH_LONGITUDE_SPAN_DEGREES
  );
}

function clampLatitude(latitude: number): number {
  return Math.max(MIN_LATITUDE, Math.min(MAX_LATITUDE, latitude));
}

function positiveInteger(value: unknown): number | null {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function squaredCoordinateDistance(left: MediaBoundsPoint, right: MediaBoundsPoint): number {
  const latitudeDelta = left.lat - right.lat;
  const longitudeDelta = left.lng - right.lng;
  return latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta;
}

function longitudeIsInBounds(longitude: number, west: number, east: number): boolean {
  return west <= east ? longitude >= west && longitude <= east : longitude >= west || longitude <= east;
}

function uniqueMediaPoints(features: GeoJSON.Feature[], fallbackPoints: MediaBoundsPoint[] = []): MediaBoundsPoint[] {
  const points = new Map<number, MediaBoundsPoint>();
  const fallbackById = new Map(fallbackPoints.map((point) => [point.id, point]));
  for (const feature of features) {
    const id = positiveInteger(feature.properties?.mediaId);
    const coordinates = feature.geometry?.type === 'Point' ? feature.geometry.coordinates : null;
    const lng = Number(coordinates?.[0]);
    const lat = Number(coordinates?.[1]);
    if (id == null || points.has(id)) continue;
    if (Number.isFinite(lat) && Number.isFinite(lng)) points.set(id, { id, lat, lng });
    else if (fallbackById.has(id)) points.set(id, fallbackById.get(id)!);
  }
  return [...points.values()];
}
