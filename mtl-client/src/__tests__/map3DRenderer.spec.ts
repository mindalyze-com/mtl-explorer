import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useMapStateStore } from '@/stores/mapStateStore';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import Map3DRenderer from '@/components/map/Map3DRenderer.vue';

const maplibreMock = vi.hoisted(() => {
  class MockLngLatBounds {
    private empty = true;

    constructor() {
      this.empty = false;
    }

    extend() {
      this.empty = false;
      return this;
    }

    isEmpty() {
      return this.empty;
    }
  }

  class MockMap {
    addControl = vi.fn();
    addLayer = vi.fn((layer: { id: string }) => {
      this.layers.add(layer.id);
    });
    addSource = vi.fn((id: string) => {
      this.sources.add(id);
    });
    fitBounds = vi.fn();
    getBounds = vi.fn(() => ({
      getEast: () => 8.7,
      getNorth: () => 47.7,
      getSouth: () => 47.4,
      getWest: () => 8.4,
    }));
    getCanvas = vi.fn(() => document.createElement('canvas'));
    getLayer = vi.fn((id: string) => (this.layers.has(id) ? { id } : undefined));
    getSource = vi.fn((id: string) => (this.sources.has(id) ? { id } : undefined));
    jumpTo = vi.fn();
    loaded = vi.fn(() => true);
    off = vi.fn();
    on = vi.fn();
    once = vi.fn((_event: string, handler: () => void) => handler());
    remove = vi.fn();
    removeLayer = vi.fn((id: string) => {
      this.layers.delete(id);
    });
    setLayoutProperty = vi.fn();
    triggerRepaint = vi.fn();
    private layers = new Set<string>();
    private sources = new Set<string>();
  }

  const instances: MockMap[] = [];

  const Map = vi.fn(function () {
    const map = new MockMap();
    instances.push(map);
    return map;
  });

  return {
    instances,
    Map,
    MockMap,
    LngLatBounds: MockLngLatBounds,
    MercatorCoordinate: {
      fromLngLat: vi.fn((lngLat: { lng: number; lat: number }, elevation = 0) => ({
        x: lngLat.lng,
        y: lngLat.lat,
        z: elevation,
      })),
    },
    NavigationControl: vi.fn(),
    ScaleControl: vi.fn(),
    AttributionControl: vi.fn(),
  };
});

const trackLoaderMock = vi.hoisted(() => ({
  fetchDetailTrackAtPrecision: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
}));

const replayControllerMock = vi.hoisted(() => ({
  instances: [] as Array<{ play: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> }>,
}));

vi.mock('maplibre-gl', () => ({
  default: maplibreMock,
}));

vi.mock('@/components/replay/TrackReplayControls.vue', () => ({
  default: {
    name: 'TrackReplayControls',
    emits: [
      'toggle-play',
      'stop',
      'close',
      'seek',
      'update-show-context-tracks',
      'update-duration',
      'update-camera-preset',
      'update-camera-smoothness',
      'recenter',
    ],
    template:
      '<div data-test="track-replay-controls"><button data-test="close" @click="$emit(\'close\')">Close</button></div>',
  },
}));

vi.mock('@/utils/mapConfigService', () => ({
  fetchMapConfig: vi.fn(() =>
    Promise.resolve({
      tileMode: 'local',
      tileBaseUrl: '/tiles',
      tilesetName: 'planet',
      remoteRasterStyles: {
        light: {
          url: 'https://example.test/{z}/{x}/{y}.png',
          attribution: '© Example Tiles',
        },
      },
    })
  ),
  mainTileArchiveUrl: vi.fn(() => '/tiles/planet.pmtiles'),
  MapConfigDtoTileModeEnum: { Local: 'local' },
}));

vi.mock('@/components/map/mapStyleResolver', () => ({
  resolveConfiguredMapStyle: vi.fn(() => ({
    style: { version: 8, sources: {}, layers: [] },
    styleMode: 'test-style',
  })),
}));

vi.mock('@/utils/maplibrePmtilesProtocol', () => ({
  registerCachingPMTilesArchive: vi.fn(),
}));

vi.mock('@/utils/apiClient', () => ({
  apiClient: {
    get: vi.fn(() => Promise.resolve({ data: { ready: true } })),
  },
}));

vi.mock('@/components/map/terrainMode', () => ({
  enableTerrainView: vi.fn(() => true),
  sanitizeTerrainExaggeration: vi.fn((value: unknown) => (Number.isFinite(Number(value)) ? Number(value) : 1)),
  TERRAIN_EXAGGERATION_DEFAULT: 1,
  TERRAIN_TARGET_PITCH: 65,
}));

vi.mock('@/utils/tracks/trackCollectionLoader', () => trackLoaderMock);

vi.mock('@/components/replay/trackReplayController', () => ({
  TrackReplayController: class {
    private onFrame: (frame: {
      progress: number;
      elapsedReplaySeconds: number;
      status: string;
      targetDurationSeconds: number;
    }) => void;
    private targetDurationSeconds: number;
    play = vi.fn(() => {
      this.onFrame({
        progress: 0,
        elapsedReplaySeconds: 0,
        status: 'playing',
        targetDurationSeconds: this.targetDurationSeconds,
      });
    });
    destroy = vi.fn();
    toggle = vi.fn();
    stop = vi.fn();
    seek = vi.fn();
    setTargetDuration = vi.fn();

    constructor(options: {
      targetDurationSeconds: number;
      onFrame: (frame: {
        progress: number;
        elapsedReplaySeconds: number;
        status: string;
        targetDurationSeconds: number;
      }) => void;
    }) {
      this.onFrame = options.onFrame;
      this.targetDurationSeconds = options.targetDurationSeconds;
      replayControllerMock.instances.push(this);
    }
  },
}));

vi.mock('@/components/replay/TrackReplayLayer', () => ({
  TRACK_REPLAY_LAYER_ID: 'track-replay-layer',
  TrackReplayLayer: class {
    id = 'track-replay-layer';
    type = 'custom';
    renderingMode = '3d';
    setData = vi.fn();
    setProgress = vi.fn();
  },
}));

vi.mock('@/components/replay/replayCameraRailPlanner', () => ({
  ReplayCameraRailPlanner: {
    build: vi.fn(() => ({
      sample: vi.fn(() => ({
        center: [8.5, 47.5],
        zoom: 15,
        bearing: 30,
        pitch: 65,
      })),
    })),
  },
}));

function flushPromises() {
  return new Promise((resolve) => window.setTimeout(resolve, 0));
}

describe('Map3DRenderer', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    maplibreMock.instances.length = 0;
    maplibreMock.Map.mockClear();
    vi.mocked(resolveConfiguredMapStyle).mockClear();
    replayControllerMock.instances.length = 0;
    trackLoaderMock.fetchDetailTrackAtPrecision.mockResolvedValue({
      coordinates: [
        [8.5, 47.5, 450],
        [8.51, 47.51, 460],
      ],
      gpsTrack: {
        id: 7,
        trackName: 'Synthetic replay track',
        trackDescription: 'Synthetic test track',
        activityType: 'HIKING',
        trackDurationInMotionSecs: 120,
      },
    });
    trackLoaderMock.loadCachedTrackCollection.mockResolvedValue({
      geojson: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            properties: { id: 9 },
            geometry: {
              type: 'LineString',
              coordinates: [
                [8.49, 47.5, 430],
                [8.52, 47.53, 470],
              ],
            },
          },
        ],
      },
    });
  });

  it('uses one MapLibre instance for terrain, tracks, replay, and camera updates', async () => {
    const store = useMapStateStore();
    store.enter3DReplay({ trackId: 7, trackLabel: 'Synthetic replay track' });

    const wrapper = mount(Map3DRenderer, { attachTo: document.body });
    await flushPromises();
    await flushPromises();

    expect(maplibreMock.Map).toHaveBeenCalledTimes(1);
    expect(maplibreMock.instances).toHaveLength(1);
    expect(maplibreMock.instances[0].jumpTo).toHaveBeenCalled();
    expect(wrapper.emitted('ready')).toBeTruthy();

    wrapper.unmount();
    expect(maplibreMock.instances[0].remove).toHaveBeenCalledTimes(1);
  });

  it('forces the bright topo style for 3D replay', async () => {
    const store = useMapStateStore();
    const settingsStore = useMapSettingsStore();
    settingsStore.setTheme('dark');
    store.enter3DReplay({ trackId: 7, trackLabel: 'Synthetic replay track' });

    const wrapper = mount(Map3DRenderer, { attachTo: document.body });
    await flushPromises();
    await flushPromises();

    expect(resolveConfiguredMapStyle).toHaveBeenCalledWith(expect.objectContaining({ theme: 'light-topo' }));

    wrapper.unmount();
  });

  it('requests mode close from the replay controls without creating another map', async () => {
    const store = useMapStateStore();
    store.enter3DReplay({ trackId: 7, trackLabel: 'Synthetic replay track' });

    const wrapper = mount(Map3DRenderer, { attachTo: document.body });
    await flushPromises();
    await flushPromises();

    await wrapper.get('[data-test="close"]').trigger('click');

    expect(wrapper.emitted('mode-close-requested')).toBeTruthy();
    expect(maplibreMock.Map).toHaveBeenCalledTimes(1);
    expect(maplibreMock.instances[0].remove).toHaveBeenCalledTimes(1);
  });
});
