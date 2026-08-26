import { describe, expect, it, vi } from 'vitest';
import {
  findMediaPointById,
  normalizeNearbyTrackMediaOptions,
  useMediaAndHeatmap,
} from '@/components/map/composables/useMediaAndHeatmap';
import { useMapTools } from '@/components/map/composables/useMapTools';

const maplibreMock = vi.hoisted(() => {
  class MockMarker {
    static instances: MockMarker[] = [];
    readonly element: HTMLElement;
    readonly remove = vi.fn();
    readonly setLngLat = vi.fn(() => this);
    readonly addTo = vi.fn(() => this);

    constructor(options: { element: HTMLElement }) {
      this.element = options.element;
      MockMarker.instances.push(this);
    }
  }

  return { MockMarker };
});

vi.mock('maplibre-gl', () => ({
  Marker: maplibreMock.MockMarker,
}));

function preferenceMethod() {
  return useMediaAndHeatmap({
    mapSettingsStore: {
      setLayerEnabled: vi.fn(),
    },
  }).restoreMediaLayerPreference;
}

describe('media visibility preference', () => {
  it('shows the media overlay and reapplies opacity when enabled', async () => {
    const mediaOverlay = {
      show: vi.fn().mockResolvedValue(undefined),
      hide: vi.fn(),
      isVisible: vi.fn(() => false),
    };
    const context = {
      mediaOverlay,
      mediaVisible: false,
      applyLayerOpacity: vi.fn(),
    };

    await preferenceMethod().call(context as never, true);

    expect(mediaOverlay.show).toHaveBeenCalledOnce();
    expect(context.applyLayerOpacity).toHaveBeenCalledWith('media');
    expect(context.mediaVisible).toBe(true);
  });

  it('keeps the media overlay hidden when disabled', async () => {
    const mediaOverlay = {
      show: vi.fn().mockResolvedValue(undefined),
      hide: vi.fn(),
      isVisible: vi.fn(() => false),
    };
    const context = {
      mediaOverlay,
      mediaVisible: true,
      applyLayerOpacity: vi.fn(),
    };

    await preferenceMethod().call(context as never, false);

    expect(mediaOverlay.show).not.toHaveBeenCalled();
    expect(mediaOverlay.hide).not.toHaveBeenCalled();
    expect(context.mediaVisible).toBe(false);
  });
});

describe('media viewer selection', () => {
  it('keeps valid nearby activities once and sorts them by distance', () => {
    expect(
      normalizeNearbyTrackMediaOptions([
        { trackId: 43, distanceMeters: 80, matchedMediaCount: 0 },
        { trackId: 42, distanceMeters: 12, matchedMediaCount: 3 },
        { trackId: 42, distanceMeters: 14, matchedMediaCount: 3 },
        { trackId: -1, distanceMeters: 5, matchedMediaCount: 1 },
      ])
    ).toEqual([
      { trackId: 42, distanceMeters: 12, matchedMediaCount: 3 },
      { trackId: 43, distanceMeters: 80, matchedMediaCount: 0 },
    ]);
  });

  it('opens one nearby activity directly at its Photos tab', () => {
    const openMediaSelectionActivities = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).openMediaSelectionActivities;
    const context = {
      mediaSelectionTrackOptions: [{ trackId: 42, distanceMeters: 12, matchedMediaCount: 3 }],
      closeMediaSelection: vi.fn(),
      onTrackBrowserOpenPhotos: vi.fn(),
      selectionPopupTrackIds: [],
      selectionPopupMediaOptions: [],
      trackSelectionPurpose: 'details',
      trackSelectionSheetVisible: false,
    };

    openMediaSelectionActivities.call(context as never);

    expect(context.closeMediaSelection).toHaveBeenCalledOnce();
    expect(context.onTrackBrowserOpenPhotos).toHaveBeenCalledWith(42);
    expect(context.trackSelectionSheetVisible).toBe(false);
  });

  it('uses the established track chooser when several nearby activities match', () => {
    const openMediaSelectionActivities = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).openMediaSelectionActivities;
    const context = {
      mediaSelectionTrackOptions: [
        { trackId: 42, distanceMeters: 12, matchedMediaCount: 3 },
        { trackId: 43, distanceMeters: 28, matchedMediaCount: 0 },
      ],
      closeMediaSelection: vi.fn(),
      onTrackBrowserOpenPhotos: vi.fn(),
      selectionPopupTrackIds: [],
      selectionPopupMediaOptions: [],
      trackSelectionPurpose: 'details',
      trackSelectionSheetVisible: false,
    };

    openMediaSelectionActivities.call(context as never);

    expect(context.selectionPopupTrackIds).toEqual([42, 43]);
    expect(context.selectionPopupMediaOptions).toEqual(context.mediaSelectionTrackOptions);
    expect(context.trackSelectionPurpose).toBe('photos');
    expect(context.trackSelectionSheetVisible).toBe(true);
    expect(context.onTrackBrowserOpenPhotos).not.toHaveBeenCalled();
  });

  it('opens a track chosen for photos at its Photos tab', () => {
    const onPopupTrackSelect = useMapTools().onPopupTrackSelect;
    const context = {
      trackSelectionPurpose: 'photos',
      selectionPopupMediaOptions: [{ trackId: 42, distanceMeters: 12, matchedMediaCount: 3 }],
      closeSelectionPopup: vi.fn(),
      onTrackBrowserOpenPhotos: vi.fn(),
      selectTrackById: vi.fn(),
      openTrackDetails: vi.fn(),
    };

    onPopupTrackSelect.call(context as never, 42);

    expect(context.closeSelectionPopup).toHaveBeenCalledOnce();
    expect(context.onTrackBrowserOpenPhotos).toHaveBeenCalledWith(42);
    expect(context.selectTrackById).not.toHaveBeenCalled();
    expect(context.openTrackDetails).not.toHaveBeenCalled();
  });

  it('opens a nearby activity without matched photos at its overview', () => {
    const onPopupTrackSelect = useMapTools().onPopupTrackSelect;
    const context = {
      trackSelectionPurpose: 'photos',
      selectionPopupMediaOptions: [{ trackId: 42, distanceMeters: 12, matchedMediaCount: 0 }],
      closeSelectionPopup: vi.fn(),
      onTrackBrowserOpenPhotos: vi.fn(),
      selectTrackById: vi.fn(),
      openTrackDetails: vi.fn(),
    };

    onPopupTrackSelect.call(context as never, 42);

    expect(context.closeSelectionPopup).toHaveBeenCalledOnce();
    expect(context.onTrackBrowserOpenPhotos).not.toHaveBeenCalled();
    expect(context.selectTrackById).toHaveBeenCalledWith(42);
    expect(context.openTrackDetails).toHaveBeenCalledWith(42, expect.any(String));
  });

  it('opens only the clicked photo when that collection is chosen', () => {
    const chooseMediaCollection = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).chooseMediaCollection;
    const closeMediaSelection = vi.fn();
    const context = {
      mediaPendingSelection: {
        selectedMediaId: 10,
        mediaIds: [10],
        mediaPoints: [{ id: 10, lat: 47.45, lng: 7.55 }],
        totalMediaCount: 1,
        clusterId: null,
        offset: 0,
        kind: 'location',
        viewportMediaPoints: [
          { id: 10, lat: 47.45, lng: 7.55 },
          { id: 11, lat: 47.46, lng: 7.56 },
        ],
      },
      closeMediaSelection,
      mediaNavList: [],
      mediaSheetMediaId: null,
      mediaNavTotal: 0,
      mediaNavOffset: 0,
      mediaNavClusterId: null,
      mediaNavPageSize: 0,
      mediaNavLoading: false,
      mediaNavScope: 'viewport',
      mediaSheetVisible: false,
    };

    chooseMediaCollection.call(context as never, 'primary');

    expect(closeMediaSelection).toHaveBeenCalledOnce();
    expect(context.mediaNavList).toEqual([{ id: 10, lat: 47.45, lng: 7.55 }]);
    expect(context.mediaNavTotal).toBe(1);
    expect(context.mediaNavScope).toBe('photo');
    expect(context.mediaSheetVisible).toBe(true);
  });

  it('opens the current map snapshot independently of a single clicked photo', () => {
    const chooseMediaCollection = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).chooseMediaCollection;
    const viewportMediaPoints = [
      { id: 10, lat: 47.45, lng: 7.55 },
      { id: 11, lat: 47.46, lng: 7.56 },
    ];
    const context = {
      mediaPendingSelection: {
        selectedMediaId: 10,
        mediaIds: [10],
        mediaPoints: [viewportMediaPoints[0]],
        totalMediaCount: 1,
        clusterId: null,
        offset: 0,
        kind: 'location',
        viewportMediaPoints,
      },
      closeMediaSelection: vi.fn(),
      mediaNavList: [],
      mediaSheetMediaId: null,
      mediaNavTotal: 0,
      mediaNavOffset: 0,
      mediaNavClusterId: 17,
      mediaNavPageSize: 0,
      mediaNavLoading: false,
      mediaNavScope: 'photo',
      mediaSheetVisible: false,
    };

    chooseMediaCollection.call(context as never, 'viewport');

    expect(context.mediaNavList).toEqual(viewportMediaPoints);
    expect(context.mediaNavTotal).toBe(2);
    expect(context.mediaNavClusterId).toBeNull();
    expect(context.mediaNavScope).toBe('viewport');
    expect(context.mediaSheetVisible).toBe(true);
  });

  it('retains the selected map point for the viewer location mini-map', () => {
    const points = [
      { id: 1, lat: 47.48, lng: 7.51 },
      { id: 2, lat: 47.49, lng: 7.52 },
    ];

    expect(findMediaPointById(points, 2)).toEqual({ id: 2, lat: 47.49, lng: 7.52 });
    expect(findMediaPointById(points, 3)).toBeNull();
    expect(findMediaPointById(points, null)).toBeNull();
  });

  it('limits navigation to the media IDs represented by the clicked map feature', () => {
    const buildMediaNavList = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    })._buildMediaNavList;
    const context = {
      mediaLoadedPoints: [
        { id: 1, lat: 47.48, lng: 7.51 },
        { id: 2, lat: 47.48, lng: 7.52 },
        { id: 3, lat: 47.49, lng: 7.53 },
      ],
      mediaNavList: [],
      overlayMap: {
        getBounds: vi.fn(() => ({
          getSouth: () => 47,
          getNorth: () => 48,
          getWest: () => 7,
          getEast: () => 8,
        })),
      },
    };

    buildMediaNavList.call(context as never, 2, [2, 3]);

    expect(context.mediaNavList.map((point) => point.id)).toEqual([2, 3]);
    expect(context.overlayMap.getBounds).not.toHaveBeenCalled();
  });

  it('loads the next bounded cluster page at the navigation boundary', async () => {
    const navigateMediaRelative = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).navigateMediaRelative;
    const nextPage = Array.from({ length: 100 }, (_, index) => ({
      id: index + 101,
      lat: 47.48,
      lng: 7.52,
    }));
    const getClusterPage = vi.fn().mockResolvedValue({
      clusterId: 17,
      offset: 100,
      totalMediaCount: 250,
      mediaPoints: nextPage,
    });
    const context = {
      mediaNavLoading: false,
      mediaCurrentIndex: 99,
      mediaNavOffset: 0,
      mediaNavTotal: 250,
      mediaNavList: Array.from({ length: 100 }, (_, index) => ({ id: index + 1, lat: 47.48, lng: 7.52 })),
      mediaNavClusterId: 17,
      mediaNavPageSize: 100,
      mediaNavRequestToken: 0,
      mediaSheetVisible: true,
      mediaSheetMediaId: 100,
      mediaOverlay: { getClusterPage },
    };

    await navigateMediaRelative.call(context as never, 1);

    expect(getClusterPage).toHaveBeenCalledWith(17, 100, 100, 250);
    expect(context.mediaNavOffset).toBe(0);
    expect(context.mediaNavList).toHaveLength(200);
    expect(context.mediaNavList.slice(100)).toEqual(nextPage);
    expect(context.mediaSheetMediaId).toBe(101);
    expect(context.mediaNavLoading).toBe(false);
  });

  it('loads an adjacent cluster page when the filmstrip reaches its boundary', async () => {
    const navigateMediaPage = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).navigateMediaPage;
    const nextPage = Array.from({ length: 200 }, (_, index) => ({
      id: index + 201,
      lat: 47.48,
      lng: 7.52,
    }));
    const getClusterPage = vi.fn().mockResolvedValue({
      clusterId: 17,
      offset: 200,
      totalMediaCount: 500,
      mediaPoints: nextPage,
    });
    const context = {
      mediaNavLoading: false,
      mediaNavOffset: 0,
      mediaNavTotal: 500,
      mediaNavList: Array.from({ length: 200 }, (_, index) => ({ id: index + 1, lat: 47.48, lng: 7.52 })),
      mediaNavClusterId: 17,
      mediaNavPageSize: 200,
      mediaNavRequestToken: 0,
      mediaSheetVisible: true,
      mediaSheetMediaId: 100,
      mediaOverlay: { getClusterPage },
    };

    await navigateMediaPage.call(context as never, 1);

    expect(getClusterPage).toHaveBeenCalledWith(17, 200, 200, 500);
    expect(context.mediaNavOffset).toBe(0);
    expect(context.mediaNavList).toHaveLength(400);
    expect(context.mediaNavList.slice(200)).toEqual(nextPage);
    expect(context.mediaSheetMediaId).toBe(201);
  });

  it('selects the photo while centering the main map', () => {
    maplibreMock.MockMarker.instances.length = 0;
    const methods = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    });
    const prepareForFocus = vi.fn();
    const jumpTo = vi.fn();
    const context = {
      mediaOverlay: { prepareForFocus },
      overlayMap: { stop: vi.fn(), getZoom: vi.fn(() => 11), jumpTo },
      focusedMediaMarker: null,
      clearFocusedMediaMarker: methods.clearFocusedMediaMarker,
    };
    const point = { id: 44, lat: 47.5605, lng: 8.505778 };

    methods.focusMediaOnMainMap.call(context as never, point);

    expect(prepareForFocus).toHaveBeenCalledWith(point);
    expect(jumpTo).toHaveBeenCalledWith({ center: [8.505778, 47.5605], zoom: 15 });
    const marker = maplibreMock.MockMarker.instances[0];
    expect(marker.setLngLat).toHaveBeenCalledWith([8.505778, 47.5605]);
    expect(marker.addTo).toHaveBeenCalledWith(context.overlayMap);
    expect(marker.element.classList.contains('mtl-focused-media-marker')).toBe(true);
    expect(marker.element.getAttribute('aria-label')).toBe('Selected photo location');
    expect(marker.element.querySelector('.bi-camera-fill')).not.toBeNull();
    expect(context.focusedMediaMarker).toBe(marker);
  });

  it('replaces the previously selected photo marker', () => {
    const clearFocusedMediaMarker = useMediaAndHeatmap({
      mapSettingsStore: { setLayerEnabled: vi.fn() },
    }).clearFocusedMediaMarker;
    const previousMarker = { remove: vi.fn() };
    const context = { focusedMediaMarker: previousMarker };

    clearFocusedMediaMarker.call(context as never);

    expect(previousMarker.remove).toHaveBeenCalledOnce();
    expect(context.focusedMediaMarker).toBeNull();
  });
});
