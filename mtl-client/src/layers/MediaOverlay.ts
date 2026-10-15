import { logSanitizedError } from '@/utils/safeLogging';
import * as maplibregl from 'maplibre-gl';
import { getMediaInBounds } from '@/repositories/mediaRepository';
import type { MediaBoundsPoint } from '@/repositories/mediaRepository';
import { isAbortLikeError } from '@/utils/errors';
import { mapLabelFonts } from '@/utils/mapLabelFonts';

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
  viewportBounds?: [[number, number], [number, number]];
  clickPoint: { x: number; y: number };
  clickLngLat: { lng: number; lat: number };
};

export type MediaOverlayCandidate = {
  key: string;
  kind: 'cluster' | 'location';
  clusterId: number | null;
  totalMediaCount: number;
  mediaPoints: MediaBoundsPoint[];
  viewportMediaPoints: MediaBoundsPoint[];
  viewportBounds?: [[number, number], [number, number]];
  clickPoint: { x: number; y: number };
  clickLngLat: { lng: number; lat: number };
};

type MediaCandidateContext = {
  source: maplibregl.GeoJSONSource;
  selectionGeneration: number;
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
  private readonly onPointsUpdated?: (points: MediaBoundsPoint[]) => void;
  private readonly isInteractionEnabled: () => boolean;
  private loadGeneration = 0;
  private selectionGeneration = 0;
  private sourcePublicationGeneration = 0;
  private sourceUpdatePending = false;
  private retainedCandidateContext = false;
  private queuedSourcePoints: MediaBoundsPoint[] | null = null;
  private readonly candidateContexts = new WeakMap<MediaOverlayCandidate, MediaCandidateContext>();
  private abortController: AbortController | null = null;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private boundHandler: (() => void) | null = null;
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
    _onMediaSelect: (selection: MediaOverlaySelection) => void,
    onPointsUpdated?: (points: MediaBoundsPoint[]) => void,
    isInteractionEnabled: () => boolean = () => true
  ) {
    this.map = map;
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
        'text-font': mapLabelFonts(this.map.getGlyphs()),
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

  /** Collect counted targets without resolving cluster leaves or opening content. */
  collectCandidates(point: { x: number; y: number }, radius = 0): MediaOverlayCandidate[] {
    if (this.state !== 'visible' || !this.isInteractionEnabled()) return [];
    const source = this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    const layers = [CLUSTER_LAYER, UNCLUSTERED_LAYER].filter((layer) => this.map.getLayer(layer));
    if (!source || layers.length === 0) return [];
    const query: [number, number] | [[number, number], [number, number]] =
      radius > 0
        ? [
            [point.x - radius, point.y - radius],
            [point.x + radius, point.y + radius],
          ]
        : [point.x, point.y];
    const features = this.map.queryRenderedFeatures(query, { layers });
    if (features.length === 0) return [];
    const lngLat = this.map.unproject([point.x, point.y]);
    const viewport = this.map.getBounds();
    const southWest = viewport.getSouthWest();
    const northEast = viewport.getNorthEast();
    const context = { source, selectionGeneration: this.selectionGeneration };
    const common = {
      viewportMediaPoints: this.getViewportMediaPoints(viewport),
      viewportBounds: [
        [southWest.lng, southWest.lat],
        [northEast.lng, northEast.lat],
      ] as [[number, number], [number, number]],
      clickPoint: { x: point.x, y: point.y },
      clickLngLat: { lng: lngLat.lng, lat: lngLat.lat },
    };
    const candidates = new Map<string, MediaOverlayCandidate>();
    for (const feature of features) {
      // Rendered tiles can still show the previous tree while setData is pending.
      if (this.sourceUpdatePending) break;
      const clusterId = positiveInteger(feature.properties?.cluster_id);
      const totalMediaCount = positiveInteger(feature.properties?.point_count);
      if (clusterId == null || totalMediaCount == null) continue;
      const key = `media-cluster:${clusterId}`;
      if (!candidates.has(key)) {
        candidates.set(key, { ...common, key, kind: 'cluster', clusterId, totalMediaCount, mediaPoints: [] });
      }
    }
    if ([...candidates.values()].some((candidate) => candidate.kind === 'cluster')) {
      this.retainedCandidateContext = true;
    }
    const locations = new Map<string, MediaBoundsPoint[]>();
    for (const mediaPoint of uniqueMediaPoints(features, this.loadedPoints)) {
      const location = `${mediaPoint.lng}:${mediaPoint.lat}`;
      const points = locations.get(location) ?? [];
      points.push(mediaPoint);
      locations.set(location, points);
    }
    for (const mediaPoints of locations.values()) {
      mediaPoints.sort((left, right) => left.id - right.id);
      const key = `media:${mediaPoints.map((mediaPoint) => mediaPoint.id).join(',')}`;
      candidates.set(key, {
        ...common,
        key,
        kind: 'location',
        clusterId: null,
        totalMediaCount: mediaPoints.length,
        mediaPoints,
      });
    }
    for (const candidate of candidates.values()) this.candidateContexts.set(candidate, context);
    return [...candidates.values()].sort((left, right) => left.key.localeCompare(right.key));
  }

  /** Keep cluster IDs and pagination tied to their original tree until this click ends. */
  releaseCandidateContext(): void {
    this.retainedCandidateContext = false;
    this.selectionGeneration++;
    if (this.queuedSourcePoints == null) return;
    const generation = ++this.sourcePublicationGeneration;
    queueMicrotask(() => {
      if (generation !== this.sourcePublicationGeneration || this.retainedCandidateContext || this.state !== 'visible')
        return;
      const points = this.queuedSourcePoints;
      this.queuedSourcePoints = null;
      if (points) this.updateSource(points);
    });
  }

  /** Resolve only the chosen target; the coordinator owns the resulting presentation. */
  async selectCandidate(
    candidate: MediaOverlayCandidate,
    isCurrent: () => boolean,
    scope: 'primary' | 'viewport' = 'primary'
  ): Promise<MediaOverlaySelection | null> {
    const context = this.candidateContexts.get(candidate);
    const isValid = () =>
      isCurrent() &&
      this.state === 'visible' &&
      this.isInteractionEnabled() &&
      context?.selectionGeneration === this.selectionGeneration &&
      this.map.getSource(SOURCE_ID) === context?.source;
    if (!context || !isValid()) return null;
    try {
      const mediaPoints =
        scope === 'viewport'
          ? [...new Map(candidate.viewportMediaPoints.map((point) => [point.id, point])).values()]
          : candidate.clusterId == null
            ? candidate.mediaPoints
            : (await this.getClusterPage(candidate.clusterId, 0, MEDIA_CLUSTER_PAGE_SIZE, candidate.totalMediaCount))
                .mediaPoints;
      if (!isValid()) return null;
      const mediaIds = mediaPoints.map((mediaPoint) => mediaPoint.id);
      const selectedMediaId =
        scope === 'viewport'
          ? (candidate.mediaPoints.find((point) => mediaIds.includes(point.id))?.id ??
            [...mediaPoints].sort(
              (left, right) =>
                squaredCoordinateDistance(left, candidate.clickLngLat) -
                  squaredCoordinateDistance(right, candidate.clickLngLat) || left.id - right.id
            )[0]?.id)
          : mediaIds[0];
      if (selectedMediaId == null) return null;
      return {
        selectedMediaId,
        mediaIds,
        mediaPoints,
        totalMediaCount: scope === 'viewport' ? mediaIds.length : candidate.totalMediaCount,
        clusterId: scope === 'viewport' ? null : candidate.clusterId,
        offset: 0,
        kind: scope === 'viewport' ? 'location' : candidate.kind,
        viewportMediaPoints: orderViewportMediaPoints(candidate.viewportMediaPoints, selectedMediaId, mediaPoints),
        viewportBounds: candidate.viewportBounds,
        clickPoint: candidate.clickPoint,
        clickLngLat: candidate.clickLngLat,
      };
    } catch (error) {
      if (!isValid()) return null;
      logSanitizedError('MediaOverlay: failed to resolve media target', error, {
        topic1: 'media',
        topic2: 'MediaOverlay',
        topic3: 'selectCandidate',
      });
      throw error;
    }
  }

  private getViewportMediaPoints(viewport: maplibregl.LngLatBounds): MediaBoundsPoint[] {
    const southWest = viewport.getSouthWest();
    const northEast = viewport.getNorthEast();
    return this.loadedPoints.filter(
      (point) =>
        point.lat >= southWest.lat &&
        point.lat <= northEast.lat &&
        longitudeIsInBounds(point.lng, southWest.lng, northEast.lng)
    );
  }

  hide(): void {
    this.selectionGeneration++;
    this.discardCandidateContext();
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

  async refresh(clearSelection = false): Promise<void> {
    if (clearSelection) {
      this.selectionGeneration++;
      this.discardCandidateContext();
      this.invalidatePendingRequest();
      this.lastFetchedBounds = null;
      this.updateSource([]);
    }
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
    const selectionGeneration = this.selectionGeneration;
    const leaves = await source.getClusterLeaves(clusterId, limit, offset);
    if (
      selectionGeneration !== this.selectionGeneration ||
      this.state !== 'visible' ||
      this.map.getSource(SOURCE_ID) !== source
    ) {
      return { clusterId, offset, totalMediaCount, mediaPoints: [] };
    }
    return {
      clusterId,
      offset,
      totalMediaCount,
      mediaPoints: uniqueMediaPoints(leaves),
    };
  }

  private removeLayerHandlers(): void {
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
      logSanitizedError('MediaOverlay: failed to load media in bounds', e, {
        topic1: 'media',
        topic2: 'MediaOverlay',
        topic3: 'loadForCurrentBounds',
      });
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
    if (this.retainedCandidateContext) {
      this.queuedSourcePoints = points;
      return;
    }
    this.queuedSourcePoints = null;
    const generation = ++this.sourcePublicationGeneration;

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
    this.sourceUpdatePending = true;
    void Promise.resolve(source.setData(geojson)).then(
      () => {
        if (generation === this.sourcePublicationGeneration) this.sourceUpdatePending = false;
      },
      (error: unknown) => {
        if (generation !== this.sourcePublicationGeneration || this.map.getSource(SOURCE_ID) !== source) return;
        this.sourceUpdatePending = false;
        logSanitizedError('MediaOverlay: failed to update media source', error, {
          topic1: 'media',
          topic2: 'MediaOverlay',
          topic3: 'updateSource',
        });
      }
    );
  }

  private discardCandidateContext(): void {
    this.retainedCandidateContext = false;
    this.queuedSourcePoints = null;
    this.sourcePublicationGeneration++;
    this.sourceUpdatePending = false;
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

function squaredCoordinateDistance(left: MediaBoundsPoint, right: Pick<MediaBoundsPoint, 'lat' | 'lng'>): number {
  const latitudeDelta = left.lat - right.lat;
  const longitudeDelta = left.lng - right.lng;
  return latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta;
}

function orderViewportMediaPoints(
  viewportPoints: MediaBoundsPoint[],
  selectedMediaId: number,
  clickedPoints: MediaBoundsPoint[]
): MediaBoundsPoint[] {
  const pointsById = new Map(viewportPoints.map((point) => [point.id, point]));
  const selectedPoint = clickedPoints.find((point) => point.id === selectedMediaId);
  if (selectedPoint) pointsById.set(selectedPoint.id, selectedPoint);
  const points = [...pointsById.values()];
  const origin = pointsById.get(selectedMediaId);
  if (!origin) return points;
  return points.sort(
    (left, right) =>
      squaredCoordinateDistance(left, origin) - squaredCoordinateDistance(right, origin) || left.id - right.id
  );
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
