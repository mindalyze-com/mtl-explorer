import maplibregl from 'maplibre-gl';
import { getMediaInBounds } from '@/repositories/mediaRepository';
import type { MediaBoundsPoint } from '@/repositories/mediaRepository';

export type MediaState = 'idle' | 'visible' | 'error';

const DEBOUNCE_MS = 300;
const BOUNDS_PADDING = 2;

const SOURCE_ID = 'media-points';
const CLUSTER_LAYER = 'media-clusters';
const CLUSTER_COUNT_LAYER = 'media-cluster-count';
const UNCLUSTERED_LAYER = 'media-unclustered';

export class MediaOverlay {
  private map: maplibregl.Map;
  private readonly onMediaSelect: (mediaId: number) => void;
  private readonly onPointsUpdated?: (points: MediaBoundsPoint[]) => void;
  private abortController: AbortController | null = null;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private boundHandler: (() => void) | null = null;
  private lastFetchedBounds: maplibregl.LngLatBounds | null = null;
  private loadedPoints: MediaBoundsPoint[] = [];
  state: MediaState = 'idle';
  error: unknown = null;
  loading = false;

  constructor(
    map: maplibregl.Map,
    onMediaSelect: (mediaId: number) => void,
    onPointsUpdated?: (points: MediaBoundsPoint[]) => void,
  ) {
    this.map = map;
    this.onMediaSelect = onMediaSelect;
    this.onPointsUpdated = onPointsUpdated;
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
        'circle-color': [
          'step', ['get', 'point_count'],
          '#f03', 10,
          '#d03', 50,
          '#a03',
        ],
        'circle-radius': [
          'step', ['get', 'point_count'],
          15, 10,
          20, 50,
          25,
        ],
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

    // Click on cluster → zoom in
    this.map.on('click', CLUSTER_LAYER, (e) => {
      const features = this.map.queryRenderedFeatures(e.point, { layers: [CLUSTER_LAYER] });
      if (!features.length) return;
      const clusterId = features[0].properties!.cluster_id;
      (this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource).getClusterExpansionZoom(clusterId)
        .then(zoom => {
          this.map.easeTo({
            center: (features[0].geometry as GeoJSON.Point).coordinates as [number, number],
            zoom,
          });
        });
    });

    // Click on single media point → notify via callback
    this.map.on('click', UNCLUSTERED_LAYER, (e) => {
      if (!e.features?.length) return;
      const mediaId = e.features[0].properties!.mediaId as number;
      this.onMediaSelect(mediaId);
    });

    // Cursor style
    this.map.on('mouseenter', CLUSTER_LAYER, () => { this.map.getCanvas().style.cursor = 'pointer'; });
    this.map.on('mouseleave', CLUSTER_LAYER, () => { this.map.getCanvas().style.cursor = ''; });
    this.map.on('mouseenter', UNCLUSTERED_LAYER, () => { this.map.getCanvas().style.cursor = 'pointer'; });
    this.map.on('mouseleave', UNCLUSTERED_LAYER, () => { this.map.getCanvas().style.cursor = ''; });

    // Bind moveend to reload markers for new viewport
    this.boundHandler = () => this.debouncedLoad();
    this.map.on('moveend', this.boundHandler);

    // Initial load for the current viewport
    await this.loadForCurrentBounds();
  }

  hide(): void {
    this.cancelPendingLoad();
    if (this.boundHandler) {
      this.map.off('moveend', this.boundHandler);
      this.boundHandler = null;
    }
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

  isVisible(): boolean { return this.state === 'visible'; }

  destroy(): void { this.hide(); }

  private debouncedLoad(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => this.loadForCurrentBounds(), DEBOUNCE_MS);
  }

  private cancelPendingLoad(): void {
    if (this.debounceTimer) { clearTimeout(this.debounceTimer); this.debounceTimer = null; }
    if (this.abortController) { this.abortController.abort(); this.abortController = null; }
  }

  private async loadForCurrentBounds(): Promise<void> {
    if (this.state !== 'visible') return;

    const viewport = this.map.getBounds();

    // Skip fetch if the current viewport is already covered
    if (this.lastFetchedBounds && this.lastFetchedBounds.contains(viewport.getSouthWest()) &&
        this.lastFetchedBounds.contains(viewport.getNorthEast())) return;

    if (this.abortController) this.abortController.abort();
    this.abortController = new AbortController();

    const sw = viewport.getSouthWest();
    const ne = viewport.getNorthEast();
    const latPad = (ne.lat - sw.lat) * BOUNDS_PADDING;
    const lngPad = (ne.lng - sw.lng) * BOUNDS_PADDING;
    const fetchBounds = new maplibregl.LngLatBounds(
      [sw.lng - lngPad, sw.lat - latPad],
      [ne.lng + lngPad, ne.lat + latPad],
    );

    this.loading = true;
    try {
      const points = await getMediaInBounds(
        fetchBounds.getSouthWest().lat, fetchBounds.getSouthWest().lng,
        fetchBounds.getNorthEast().lat, fetchBounds.getNorthEast().lng,
        this.abortController.signal,
      );

      if (this.state !== 'visible') return;

      this.lastFetchedBounds = fetchBounds;
      this.updateSource(points);
      this.error = null;
    } catch (e: any) {
      if (e?.name === 'CanceledError' || e?.code === 'ERR_CANCELED') return;
      this.error = e;
      console.error('MediaOverlay: failed to load media in bounds', e);
    } finally {
      this.loading = false;
    }
  }

  private updateSource(points: MediaBoundsPoint[]): void {
    const source = this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    if (!source) return;

    this.loadedPoints = points;
    this.onPointsUpdated?.(points);

    const geojson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection',
      features: points.map(p => ({
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [p.lng, p.lat] },
        properties: { mediaId: p.id },
      })),
    };
    source.setData(geojson);
  }
}
