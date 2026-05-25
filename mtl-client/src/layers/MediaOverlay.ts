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
    onMediaSelect: (mediaId: number) => void,
    onPointsUpdated?: (points: MediaBoundsPoint[]) => void
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

    // Click on cluster → zoom in
    this.clusterClickHandler = (e) => {
      const features = this.map.queryRenderedFeatures(e.point, { layers: [CLUSTER_LAYER] });
      if (!features.length) return;
      const clusterId = features[0].properties!.cluster_id;
      (this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource).getClusterExpansionZoom(clusterId).then((zoom) => {
        this.map.easeTo({
          center: (features[0].geometry as GeoJSON.Point).coordinates as [number, number],
          zoom,
        });
      });
    };
    this.map.on('click', CLUSTER_LAYER, this.clusterClickHandler);

    // Click on single media point → notify via callback
    this.mediaClickHandler = (e) => {
      if (!e.features?.length) return;
      const mediaId = e.features[0].properties!.mediaId as number;
      this.onMediaSelect(mediaId);
    };
    this.map.on('click', UNCLUSTERED_LAYER, this.mediaClickHandler);

    // Cursor style
    this.clusterMouseEnterHandler = () => {
      this.map.getCanvas().style.cursor = 'pointer';
    };
    this.clusterMouseLeaveHandler = () => {
      this.map.getCanvas().style.cursor = '';
    };
    this.mediaMouseEnterHandler = () => {
      this.map.getCanvas().style.cursor = 'pointer';
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

  destroy(): void {
    this.hide();
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
    this.debounceTimer = setTimeout(() => this.loadForCurrentBounds(), DEBOUNCE_MS);
  }

  private cancelPendingLoad(): void {
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = null;
    }
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
  }

  private async loadForCurrentBounds(): Promise<void> {
    if (this.state !== 'visible') return;

    const viewport = this.map.getBounds();

    // Skip fetch if the current viewport is already covered
    if (
      this.lastFetchedBounds &&
      this.lastFetchedBounds.contains(viewport.getSouthWest()) &&
      this.lastFetchedBounds.contains(viewport.getNorthEast())
    )
      return;

    if (this.abortController) this.abortController.abort();
    this.abortController = new AbortController();

    const sw = viewport.getSouthWest();
    const ne = viewport.getNorthEast();
    const latPad = (ne.lat - sw.lat) * BOUNDS_PADDING;
    const lngPad = (ne.lng - sw.lng) * BOUNDS_PADDING;
    const fetchBounds = new maplibregl.LngLatBounds(
      [sw.lng - lngPad, sw.lat - latPad],
      [ne.lng + lngPad, ne.lat + latPad]
    );

    this.loading = true;
    try {
      const points = await getMediaInBounds(
        fetchBounds.getSouthWest().lat,
        fetchBounds.getSouthWest().lng,
        fetchBounds.getNorthEast().lat,
        fetchBounds.getNorthEast().lng,
        this.abortController.signal
      );

      if (this.state !== 'visible') return;

      this.lastFetchedBounds = fetchBounds;
      this.updateSource(points);
      this.error = null;
    } catch (e: unknown) {
      const error = e as { name?: string; code?: string };
      if (error.name === 'CanceledError' || error.code === 'ERR_CANCELED') return;
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
      features: points.map((p) => ({
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [p.lng, p.lat] },
        properties: { mediaId: p.id },
      })),
    };
    source.setData(geojson);
  }
}
