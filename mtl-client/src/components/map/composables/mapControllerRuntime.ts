import type { ComputedRef, Ref, ToRefs } from 'vue';
import type * as maplibregl from 'maplibre-gl';
import type { ColorPalette } from '@/components/filter/ColorPalette';
import type { DrawnCircle, DrawnPolygon, DrawnRectangle, DrawnShape, GeoShapeType } from '@/layers/GeoDrawingOverlay';
import type { MAP_OVERLAYS } from '@/utils/mapStyle';
import type { MapCameraState, MapRendererMode } from '@/components/map/mapRendererTypes';
import type { MapSourceMode } from '@/stores/mapSettingsStore';
import type { ReplayCameraRail, ReplayCameraViewport } from '@/components/replay/replayCameraRailPlanner';
import type { ReplayCameraScreenGuard } from '@/components/replay/replayCameraScreenGuard';
import type { ReplayCameraPresetId, ReplayPath } from '@/components/replay/trackReplayPath';
import type { TrackReplayController, ReplayPlaybackFrame } from '@/components/replay/trackReplayController';
import type { TrackReplayLayer } from '@/components/replay/TrackReplayLayer';
import type { ReplayViewportOcclusionLayout, ReplayViewportPadding } from '@/components/replay/replayViewportOcclusion';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import type {
  GpsTrack,
  GpsTrackDataPoint,
  LocationSearchResultDto,
  MapServerStatusDto,
  NearbyTrackMediaDto,
  ParamDefinition,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type { ToastService } from '@/types/ui';
import type { MediaOverlaySelection } from '@/layers/MediaOverlay';

export type TrackId = number;
export type Coordinates = [number, number];
export type MapPoint = { x: number; y: number };
export type MapCenter = { lat: number; lng: number };
export type ToastLike = ToastService;
export type MapControllerEmit = {
  (event: 'tracks-loaded'): void;
  (event: 'load-failed'): void;
  (event: 'syncing', value: boolean): void;
};
export type MapControllerProps = { fromLogin?: boolean };
export type MapControllerTemplateRefs = Record<string, { readonly value: unknown } | undefined>;

export type MapControllerMap = maplibregl.Map & {
  getSource: (id: string) => ({ setData?: (data: unknown) => void } & Record<string, unknown>) | undefined;
};

export type MapTheme = {
  name: string;
  code: string;
  thumbnail: string;
  badgeLabel?: string;
  badgeTone?: 'preferred' | 'swiss';
};

export type MapToolDefinition = {
  id: string;
  icon: string;
  label: string;
  alertIcon?: string;
  driftedIcon?: string;
};

export type LayerOpacities = Record<string, number> & {
  basemap: number;
  terrain: number;
  tracks: number;
  media: number;
  trackpoints: number;
  heatmap: number;
};

export type LayerPanelState = {
  enabled: boolean;
  opacity: number;
};

export type LayerPanelStates = Record<string, LayerPanelState>;

export type LegendEntry = {
  group: string;
  color: string;
  count: number;
  label?: string;
};

export type MapServerStatus = MapServerStatusDto;

export type TrackFeatureProperties = Record<string, unknown> & {
  id?: number | string;
  trackId?: number | string;
  gpsTrackId?: number | string;
  trackName?: string;
  trackDescription?: string;
  activityType?: string;
  color?: string;
};

export type TrackFeature = GeoJSON.Feature<GeoJSON.Geometry, TrackFeatureProperties>;
export type TrackFeatureCollection = GeoJSON.FeatureCollection<GeoJSON.Geometry, TrackFeatureProperties>;

export type TrackPopupMeta = {
  id: number;
  name: string;
  displayName: string;
  description: string;
  activityType: string;
  date: string;
  distanceMeters?: number;
  matchedMediaCount?: number;
};

export type SwissMobilityRoute = {
  icon: string;
  type: string;
  name: string;
  number?: string | number | null;
};

export type SwissMobilityPopupState = {
  visible: boolean;
  pos: MapPoint;
  routes: SwissMobilityRoute[];
};

export type TrackDetailsInfo = {
  id?: number | null;
  name: string;
  description: string;
  activityType?: string;
};

export type TrackDetailsDetent = {
  id: string;
  height: string;
};

export type TrackDetailsInitialTab = 'overview' | 'photos';
export type TrackSelectionPurpose = 'details' | 'photos';

export type MapToolNavigationTarget = {
  toolId: string;
  state?: unknown;
};

export type MediaPoint = Record<string, unknown> & {
  id?: number | null;
  lat: number;
  lng: number;
};

export type MediaNavigationScope = 'photo' | 'location' | 'cluster' | 'viewport';

export type GeoDrawingParamDef = ParamDefinition & {
  type?: string;
};

export type GeoDrawingOverlay = {
  canUndo?: () => boolean;
  canFinish?: () => boolean;
  startDrawing: (type: GeoShapeType, callback: (shape: DrawnShape) => void, onStateChange?: () => void) => void;
  getPointCount: () => number;
  undoLastPoint: () => void;
  finishPolygon: () => void;
  cancelDrawing: () => void;
  clearAll: () => void;
  renderCircle: (circle: DrawnCircle, color?: string, name?: string) => string;
  renderRectangle: (rectangle: DrawnRectangle, color?: string, name?: string) => string;
  renderPolygon: (polygon: DrawnPolygon, color?: string, name?: string) => string;
  refreshMeasurementLabels: () => void;
  destroy: () => void;
  renderExisting?: () => void;
};

export type TrackPointLayerEvent = maplibregl.MapMouseEvent & {
  features?: Array<{
    properties?: {
      trackId?: number | string;
      pointIndex?: number | string;
    };
  }>;
};

export type TrackPointLayerHandlers = {
  click: (event: TrackPointLayerEvent) => void;
  mouseenter: () => void;
  mouseleave: () => void;
};

export type TrackPointPopupContext = {
  lngLat: MapCenter | Coordinates;
  trackId: number;
  point: GpsTrackDataPoint;
  canonical?: GpsTrackDataPoint | null;
};

export type GeoDrawingShapes = {
  labels?: Record<string, string>;
  circles: Record<string, DrawnCircle | null | undefined>;
  rectangles: Record<string, DrawnRectangle | null | undefined>;
  polygons: Record<string, DrawnPolygon | null | undefined>;
};

export type MapControllerRefs = Record<
  string,
  | {
      close?: () => void;
      open?: () => void;
      toggle?: () => void;
      getNavigationState?: () => unknown;
      restoreNavigationState?: (state: unknown) => void;
      renderExistingGeoShapes?: () => void;
      onGeoDrawingComplete?: (paramDef: ParamDefinition, shape: DrawnShape) => void;
      getGeoShapes?: () => GeoDrawingShapes;
    }
  | null
  | undefined
> & {
  mapContainer?: HTMLElement;
};

export type MapPadding = { top: number; right: number; bottom: number; left: number };
export type MapCameraStateWithPadding = MapCameraState & { padding?: MapPadding };
export type TrackReplayStartPayload = {
  trackId?: number | null;
  gpsTrack?: GpsTrack | null;
  coordinates?: number[][];
  chartPoints?: ChartPoint[];
  renderedShapePoints?: GpsTrackDataPoint[];
};
export type TrackReplayRestoreState = {
  camera: MapCameraState;
  selectedTrackId: number | null;
  terrainEnabled: boolean;
};
export type TrackReplayControlsLayout = ReplayViewportOcclusionLayout & {
  width?: number;
  height?: number;
  top?: number;
  right?: number;
  bottom?: number;
  left?: number;
};

export type TrackReplayInteractionEvent = { originalEvent?: Event };
export type TrackReplayInteractionHandlers = {
  canvas: HTMLCanvasElement;
  clearUserPointerActive: () => void;
  disableAutoFollow: (event?: TrackReplayInteractionEvent) => void;
  disableAutoFollowFromWheel: (event: WheelEvent) => void;
  markUserPointerActive: () => void;
};
export type ToggleableOverlay = {
  destroy: () => void;
  hide: () => void;
  show: (data?: unknown) => Promise<void> | void;
  isVisible: () => boolean;
  refresh?: () => Promise<void>;
  updateData?: (data: TrackFeatureCollection) => void;
  prepareForFocus?: (point: MediaPoint) => void;
  getClusterPage?: (
    clusterId: number,
    offset: number,
    limit: number,
    totalMediaCount: number
  ) => Promise<{
    clusterId: number;
    offset: number;
    totalMediaCount: number;
    mediaPoints: MediaPoint[];
  }>;
};

export type TerrainControl = maplibregl.IControl & {
  setActive: (active: boolean) => void;
};

export type MapControllerState = {
  overlayMap: MapControllerMap | undefined;
  mapConfig: Record<string, unknown> | null;
  mapServerStatus: MapServerStatus | null;
  mapStatusPollTimer: ReturnType<typeof setInterval> | null;
  mapArchiveStaleReloading: boolean;
  baseMapStyleMode: string;
  baseMapRuntimeFallbackApplied: boolean;
  zoom: number;
  geojson: TrackFeatureCollection | undefined;
  globeMode: boolean;
  globeUserDisabled: boolean;
  mapCenter: Coordinates;
  gpsMarker: maplibregl.Marker | null;
  gpsLocation: Coordinates | null;
  gpsDeviceEnabledDisabled: boolean;
  gpsFollowing: boolean;
  showLoader: boolean;
  loadingTrackBatches: boolean;
  loadingTracks10m: boolean;
  mapThemes: MapTheme[];
  mapThemeSelected: string;
  mapSourceMode: MapSourceMode;
  visibleTrackCount: number;
  totalTrackCount: number;
  basemapEnabled: boolean;
  terrainEnabled: boolean;
  terrainExaggeration: number;
  terrainTracksReady: boolean;
  terrainTrackDataDirty: boolean;
  flatTrackSourceDirty: boolean;
  flatTrackSourceReduced: boolean;
  tracksEnabled: boolean;
  layerOpacities: LayerOpacities;
  filterActive: boolean;
  colorPalette: ColorPalette;
  legendEntries: LegendEntry[];
  legendMode: 'categorical' | 'gradient';
  legendGradientColors: string[];
  legendGradientBucketCount: number;
  legendCollapsed: boolean;
  hiddenGroups: Set<string>;
  renderedFilterConfigId: number | null;
  gpsTracksById: Map<number, GpsTrack>;
  gpsTrackIdToFeature: Map<number, TrackFeature>;
  selectedTrackId: number | null;
  selectedFeature: TrackFeature | null;
  trackSelectionSheetVisible: boolean;
  selectionPopupTrackIds: number[];
  selectionPopupMediaOptions: NearbyTrackMediaDto[];
  trackSelectionPurpose: TrackSelectionPurpose;
  swissMobilityPopup: SwissMobilityPopupState;
  proximityAbortController: AbortController | null;
  trackDetailsVisible: boolean;
  trackDetailsBackgroundDetent: TrackDetailsDetent;
  trackDetailsDetents: TrackDetailsDetent[];
  trackDetailsInitialDetent: string;
  trackDetailsInitialTab: TrackDetailsInitialTab;
  trackDetailsSelectedDetent: string | number | undefined;
  trackDetailsId: number | null;
  trackDetailsInfo: TrackDetailsInfo;
  trackDetailsReturnTarget: MapToolNavigationTarget | null;
  trackReplayActive: boolean;
  trackReplayLoading: boolean;
  trackReplayPlaying: boolean;
  trackReplayAutoFollow: boolean;
  trackReplayShowContextTracks: boolean;
  trackReplayShowTelemetry: boolean;
  trackReplayTrackId: number | null;
  trackReplayTrackLabel: string;
  trackReplayProgress: number;
  trackReplayDurationSeconds: number;
  trackReplaySpeedFactorLabel: string;
  trackReplayCameraPreset: ReplayCameraPresetId;
  trackReplayCameraSmoothness: number;
  trackReplayDistanceLabel: string;
  trackReplayElapsedLabel: string;
  trackReplayTotalLabel: string;
  _trackReplayPath: ReplayPath | null;
  _trackReplayController: TrackReplayController | null;
  _trackReplayLayer: TrackReplayLayer | null;
  _trackReplayRestoreState: TrackReplayRestoreState | null;
  _trackReplayCameraRail: ReplayCameraRail | null;
  _trackReplayCameraViewportKey: string;
  _trackReplayCameraViewportFrame: number | null;
  _trackReplayViewportOcclusionObserver: { disconnect: () => void } | null;
  _trackReplayInteractionHandlers: TrackReplayInteractionHandlers | null;
  _trackReplayApplyingCamera: boolean;
  _trackReplayUserPointerActive: boolean;
  _trackReplayScreenGuard?: ReplayCameraScreenGuard | null;
  trackReplayControlsLayout: TrackReplayControlsLayout | null;
  locationSearchVisible: boolean;
  locationSearchMarker: maplibregl.Marker | null;
  measureToolActive: boolean;
  plannerToolActive: boolean;
  activeToolId: string | null;
  toolDefs: MapToolDefinition[];
  mediaOverlay: ToggleableOverlay | null;
  mediaVisible: boolean;
  mediaBusy: boolean;
  focusedMediaMarker: maplibregl.Marker | null;
  mediaSelectionSheetVisible: boolean;
  mediaPendingSelection: MediaOverlaySelection | null;
  mediaSelectionTrackOptions: NearbyTrackMediaDto[];
  mediaSelectionTracksLoading: boolean;
  mediaSelectionAbortController: AbortController | null;
  mediaSelectionRequestToken: number;
  mediaSheetVisible: boolean;
  mediaSheetMediaId: number | null;
  mediaLoadedPoints: MediaPoint[];
  mediaNavList: MediaPoint[];
  mediaNavTotal: number;
  mediaNavOffset: number;
  mediaNavClusterId: number | null;
  mediaNavPageSize: number;
  mediaNavLoading: boolean;
  mediaNavRequestToken: number;
  mediaNavScope: MediaNavigationScope;
  heatmapOverlay: ToggleableOverlay | null;
  heatmapVisible: boolean;
  isOffline: boolean;
  cachedTracksLoaded: boolean;
  initialLoadDone: boolean;
  freshLoginAutoFreshenDone: boolean;
  retryTimeoutId: ReturnType<typeof setTimeout> | null;
  retryCount: number;
  bulk10mController: AbortController | null;
  trackPrecisions: Map<number, number>;
  activeTrackFilterResult: unknown;
  detailAbortController: AbortController | null;
  detailDebounceTimer: ReturnType<typeof setTimeout> | null;
  activeOverlays: string[];
  _scaleControl: maplibregl.ScaleControl | null;
  _terrainControl: TerrainControl | null;
  _terrainTrackLayer: maplibregl.CustomLayerInterface | null;
  _syncingToolRoute: boolean;
  trackPointsVisible: boolean;
  trackPointsDetailsCache: Map<string, GpsTrackDataPoint[]>;
  trackPointsCanonicalCache: Map<number, GpsTrackDataPoint[]>;
  trackPointsPopup: maplibregl.Popup | null;
  trackPointsPopupContext: TrackPointPopupContext | null;
  trackPointLayerHandlers: TrackPointLayerHandlers | null;
  geoDrawingOverlay: GeoDrawingOverlay | null;
  geoDrawingParamDef: GeoDrawingParamDef | null;
  geoDrawPointCount: number;
  _mapReadyResolve: (() => void) | null;
  _mapReadyPromise: Promise<void> | null;
  _onOnline?: (() => void) | null;
  _resizeObserver?: ResizeObserver | null;
  _attributionControl?: maplibregl.IControl | null;
  _attributionLinkCleanup?: (() => void) | null;
  _globeControl?: maplibregl.IControl | null;
};

export type MapControllerSetupBindings = {
  isIndexing: ComputedRef<boolean>;
  activeFilterIdentity: ComputedRef<string>;
  serverFreshnessToken: Ref<string | null | undefined>;
  dataFreshnessLastChecked: Ref<unknown>;
  refreshDataFreshness: () => Promise<unknown> | unknown;
  isFreshnessPollingHealthy: Ref<boolean>;
  appliedFreshnessToken: Ref<string | null | undefined>;
  freshnessReloading: Ref<boolean>;
  mapMode: Ref<MapRendererMode>;
};

export type MapControllerSetupValues = {
  isIndexing: boolean;
  activeFilterIdentity: string;
  serverFreshnessToken: string | null | undefined;
  dataFreshnessLastChecked: unknown;
  refreshDataFreshness: () => Promise<unknown> | unknown;
  isFreshnessPollingHealthy: boolean;
  appliedFreshnessToken: string | null | undefined;
  freshnessReloading: boolean;
  mapMode: MapRendererMode;
};

export type MapControllerMagic = {
  $refs: MapControllerRefs;
  $emit: MapControllerEmit;
  $toast: ToastLike;
  $nextTick: (typeof import('vue'))['nextTick'];
};

export type MapControllerComputedValues = {
  selectionPopupTracks: TrackPopupMeta[];
  layerStatesForPanel: LayerPanelStates;
  mapThemesForPanel: MapTheme[];
  isMediaVisible: boolean;
  mediaCurrentIndex: number;
  mediaCanGoPrev: boolean;
  mediaCanGoNext: boolean;
  mediaPrevId: number | null;
  mediaNextId: number | null;
  mediaNavigationIds: number[];
  showLocationSearchFab: boolean;
  locationSearchMapCenter: { lon: number; lat: number };
  trackBrowserTracks: GpsTrack[];
  alertToolIds: string[];
  driftedToolIds: string[];
  geoDrawToolbarIcon: string;
  geoDrawToolbarLabel: string;
  geoDrawToolbarHint: string;
  geoDrawIsPolygon: boolean;
  geoDrawCanUndo: boolean;
  geoDrawCanFinish: boolean;
  showDataFreshnessBanner: boolean;
};

export type MapControllerComputedDefinitions = {
  [Key in keyof MapControllerComputedValues]: (this: MapControllerRuntime) => MapControllerComputedValues[Key];
} & ThisType<MapControllerRuntime>;

export type MapControllerComputedRefs = {
  [Key in keyof MapControllerComputedValues]: ComputedRef<MapControllerComputedValues[Key]>;
};

export type MapLayerFilterExpression = unknown[] | null;

export type MapToolsMethods = {
  openLocationSearch(): void;
  onLocationSearchSelect(result: LocationSearchResultDto): void;
  setLocationSearchMarker(lon: number, lat: number): void;
  clearLocationSearchMarker(): void;
  locationSearchTargetZoom(result: LocationSearchResultDto): number;
  onLocationUpdate(geolocationPosition: GeolocationPosition): void;
  onGPSDeviceEnabledDisabled(deviceEnabled: boolean): void;
  onAnimationStartEvent(): void;
  onAnimationFinishedEvent(): void;
  onAnimationStopEvent(): void;
  onAnimateEvent(event: unknown): void;
  onMeasureShowTrackDetails(id: number | string): void;
  onMeasureActiveChanged(isActive: boolean): void;
  onPlannerActiveChanged(isActive: boolean): void;
  closeAllToolsExcept(skipRefName?: string | null): void;
  closeTransientOverlaysForToolSwitch(): void;
  captureActiveToolNavigation(): void;
  restoreToolNavigation(toolId: string): void;
  onToolSelect(toolId: string | null): void;
  syncToolToRoute(toolId: string | null): void;
  syncTrackDetailRoute(trackId: number | string | null): void;
  onToolOpened(refName: string): void;
  onToolClosed(): void;
  closeSwissMobilityPopup(): void;
  identifySwissMobilityRoutes(lngLat: MapCenter, point: MapPoint): Promise<void>;
  showTrackSelectionPopup(point: MapPoint, trackIds: number[]): void;
  closeSelectionPopup(): void;
  onPopupTrackSelect(id: number): void;
  openTrackDetails(trackId?: number | string | null, initialDetent?: string, initialTab?: TrackDetailsInitialTab): void;
  onTrackDetailsSheetClosed(): void;
  onTrackDetailsLoaded(metadata: TrackDetailsInfo): void;
};

export type MapOverlay = (typeof MAP_OVERLAYS)[number];
export type TrackLineColor = string | maplibregl.ExpressionSpecification;
export type MapOverlayPaint = {
  'raster-opacity': number;
  'raster-saturation': number;
  'raster-brightness-max': number;
  'raster-hue-rotate'?: number;
};

export type MapLayerSettingsMethods = {
  captureBasemapLayers(): void;
  applyBasemapAppearance(): void;
  resolveTrackLineColor(): Promise<TrackLineColor>;
  updateTrackStyle(): Promise<void>;
  orderLegendEntriesByFilterResult<TEntry extends { group: string }>(
    entries: TEntry[],
    groupOrder?: string[]
  ): TEntry[];
  _overlayPaintForSlider(slider: number, hueRotate?: number): MapOverlayPaint;
  _overlayBeforeId(): string | undefined;
  _addOverlay(overlay: MapOverlay, opacity: number, beforeId?: string): void;
  applyActiveOverlays(): void;
  removeAllOverlays(): void;
  onResetMapSettings(): Promise<void>;
  syncMapSettingsFromStore(): void;
  onToggleLayer(layerId: string): Promise<void>;
  onLayerOpacityChange(layerId: string, value: number): void;
  applyLayerOpacity(layerId: string): void;
  applyAllLayerOpacities(): void;
  onToggleOverlay(overlayId: string): void;
  onMapThemeChangeEvent(themeCode: string): Promise<void>;
  onMapSourceModeChangeEvent(sourceMode: MapSourceMode): Promise<void>;
  onToggleTrackPoints(): void;
  onLegendCollapsed(val: boolean): void;
  onHiddenGroupsChanged(groups: string[] | Set<string>): void;
  applyGroupFilter(): void;
};

export type TerrainModeMethods = {
  _applyHillshade(): void;
  setTerrainModeEnabled(enabled: boolean, options?: { animate?: boolean }): void;
  toggleTerrainMode(options?: { animate?: boolean }): void;
  onToggleTerrainMode(): void;
  onSetTerrainModeEnabled(enabled: boolean): void;
  onTerrainExaggerationChange(exaggeration: number): void;
  jumpMapCamera(view: MapCameraStateWithPadding): void;
  handleTerrainUnavailable(detail: string, notify: boolean): void;
  applyTerrainPreference(options?: { animate?: boolean }): void;
};

export type TrackLayersMethods = {
  currentTrackReplayTrackId(): number | null;
  hiddenGroupTrackFilter(): MapLayerFilterExpression;
  replayContextTrackFilter(): MapLayerFilterExpression;
  trackLayerFilter(layerId: string): MapLayerFilterExpression;
  applyTrackLayerFilters(): void;
  selectedTrackGeojson(): TrackFeatureCollection;
  updateSelectedTrackSource(): void;
  markFlatTrackSourceDirty(): void;
  flatTrackSourceGeojson(): TrackFeatureCollection;
  syncFlatTrackSource(options?: { force?: boolean }): void;
  applySelectedTrackHighlight(): void;
  applyTrackRenderFilters(): void;
  applyTracksVisibility(): void;
  updateTrackLineWidth(): void;
  fitToTrackBounds(geojson: TrackFeatureCollection): void;
  updateTracksSource(): void;
  addTracksToMap(): Promise<void>;
  selectTrackById(trackId: number): void;
  selectTrack(trackId: number, feature?: TrackFeature | null): void;
  deselectTrack(): void;
  getTrackPopupMeta(id: number): TrackPopupMeta;
  onTrackBrowserSelect(trackId: number | string): void;
  onTrackBrowserOpenDetails(trackId: number | string): void;
  onTrackBrowserOpenPhotos(trackId: number | string): void;
};

export type TrackPointLayerMethods = {
  closeTrackPointPopup(): void;
  detachTrackPointLayerHandlers(): void;
  attachTrackPointLayerHandlers(): void;
  updateTrackPointsSource(): void;
  showTrackPointPopup(lngLat: MapCenter | Coordinates, trackId: number, pointIndex: number): Promise<void>;
  showTrackLinePointPopup(event: { point: MapPoint; lngLat: MapCenter }, trackId: number): Promise<boolean>;
  renderTrackPointPopup(
    lngLat: MapCenter | Coordinates,
    trackId: number,
    point: GpsTrackDataPoint,
    canonical?: GpsTrackDataPoint | null
  ): void;
  refreshTrackPointPopupMeasurementLabels(): void;
};

export type TrackReplayMethods = {
  start3dTrackReplay(payload?: TrackReplayStartPayload): Promise<void>;
  prepare3dTrackReplayMap(trackId: number, path: ReplayPath): void;
  captureTrackReplayRestoreState(): TrackReplayRestoreState | null;
  dimTracksForReplay(): void;
  addTrackReplayLayer(path: ReplayPath): void;
  removeTrackReplayLayer(): void;
  onTrackReplayFrame(frame: ReplayPlaybackFrame): void;
  refreshTrackReplayMeasurementLabels(): void;
  rebuildTrackReplayCameraRail(): ReplayCameraRail | null;
  trackReplayCameraViewport(): ReplayCameraViewport | undefined;
  trackReplayCameraViewportPadding(baseMarginPx?: number): ReplayViewportPadding;
  trackReplayMapPadding(baseMarginPx?: number): MapPadding;
  trackReplayCameraViewportKey(viewport?: ReplayCameraViewport): string;
  onTrackReplayControlsLayoutChange(layout: TrackReplayControlsLayout | null): void;
  installTrackReplayViewportOcclusionObserver(): void;
  scheduleTrackReplayCameraViewportRebuild(): void;
  applyTrackReplayCamera(progress: number, force?: boolean, elapsedReplaySeconds?: number | null): void;
  toggle3dTrackReplayPlayback(): void;
  reset3dTrackReplayPlayback(): void;
  seek3dTrackReplay(progress: number): void;
  set3dTrackReplayShowContextTracks(value: boolean): void;
  set3dTrackReplayShowTelemetry(value: boolean): void;
  set3dTrackReplayDuration(seconds: number): void;
  set3dTrackReplayCameraPreset(preset: ReplayCameraPresetId): void;
  set3dTrackReplayCameraSmoothness(value: number): void;
  recenter3dTrackReplayCamera(): void;
  installTrackReplayInteractionHandlers(): void;
  removeTrackReplayInteractionHandlers(): void;
  stop3dTrackReplay(options?: { restore?: boolean }): void;
};

export type GeoDrawingMethods = {
  onStartGeoDrawing(paramDef: GeoDrawingParamDef): void;
  onGeoDrawUndo(): void;
  onGeoDrawFinish(): void;
  onGeoDrawCancel(): void;
  onClearGeoShape(paramDef?: GeoDrawingParamDef): void;
  renderExistingGeoShapes(): void;
};

export type MediaAndHeatmapMethods = {
  restoreMediaLayerPreference(enabled: boolean): Promise<void>;
  onToggleMediaLayer(): Promise<void>;
  onToggleHeatmapLayer(): void;
  openMediaSelection(selection: MediaOverlaySelection): Promise<void>;
  chooseMediaCollection(scope: 'primary' | 'viewport'): void;
  openMediaSelectionActivities(): void;
  closeMediaSelection(): void;
  navigateMediaTo(id: number | null): void;
  navigateMediaRelative(delta: -1 | 1): Promise<void>;
  navigateMediaPage(direction: -1 | 1): Promise<void>;
  closeMediaSheet(): void;
  clearFocusedMediaMarker(): void;
  focusMediaOnMainMap(point: MediaPoint): void;
  _buildMediaNavList(originId?: number | null, selectedIds?: number[]): void;
};

export type MapDataLoadingMethods = {
  shouldAutoFreshenAfterLogin(): boolean;
  maybeAutoFreshenAfterLogin(): void;
  captureAppliedFreshnessToken(): Promise<void>;
  clearTrackCacheWhenServerFreshnessChanged(): Promise<boolean>;
  onDataFreshnessReload(options?: { silent?: boolean }): Promise<boolean>;
  onDataFreshnessDismiss(): void;
  currentCollectionPrecision(): number;
  maybeLoadBackgroundTracks(filterResult?: unknown): boolean;
  loadMapData(fetchResult: unknown): Promise<void>;
  publishGpsTrackMetadataChanges(): void;
  mergeTrackFeatures(fetchResult: unknown): {
    changed: boolean;
    incomingIds: Set<number>;
    trackDataChanged: boolean;
  };
  mergeTrackResult(fetchResult: unknown, options?: { pruneMissing?: boolean }): Promise<void>;
  mergeTrackPage(fetchResult: unknown): Promise<void>;
  showCachedTrackFallback(cached: unknown, loadMessage: string, emittedMessage: string): Promise<void>;
  onMapFreshnessBrowserReload(): Promise<boolean>;
  onAdminReloadTracks(done?: (success?: boolean, message?: string) => void): Promise<void>;
  onAdminRefreshFreshnessData(done?: (success?: boolean) => void): Promise<void>;
  fetchTracksAndFallback(): Promise<void>;
  _backgroundSync(timer?: unknown): Promise<void>;
  applyBackgroundTrackPage(pageData: unknown, signal: AbortSignal): boolean;
  loadAllTracksAt10m(filterResult?: unknown): Promise<void>;
  onBrowserOnline(): void;
  scheduleRetry(): void;
  performBackgroundRetry(): Promise<void>;
  scheduleDetailCheck(): void;
  checkViewportPrecision(): void;
  processDetailQueue(trackIds: number[], targetPrecision: number, signal: AbortSignal): Promise<void>;
  onFilterApplied(): Promise<void>;
  onFilterStyleChanged(): Promise<void>;
};

export type MapRendererLifecycleMethods = {
  startMapStatusPolling(): void;
  refreshMapStatusPolling(force?: boolean): void;
  stopMapStatusPolling(): void;
  disposeRendererMaps(): void;
  applyRuntimeRasterBasemapFallback(errorEvent?: unknown, message?: string, tileId?: string): void;
  setOverlayAttributionControl(attributions?: string[]): void;
  handleMapArchiveStale(event?: Event): Promise<void>;
  reloadMap(): Promise<void>;
  initMap(): Promise<void>;
  updateGlobeState(): void;
  applyGlobeProjection(): void;
  toggleGlobeMode(): void;
};

export type MapControllerMethods = MapToolsMethods &
  MapLayerSettingsMethods &
  TerrainModeMethods &
  TrackLayersMethods &
  TrackPointLayerMethods &
  TrackReplayMethods &
  GeoDrawingMethods &
  MediaAndHeatmapMethods &
  MapDataLoadingMethods &
  MapRendererLifecycleMethods;

export type MapControllerRuntime = MapControllerState &
  MapControllerComputedValues &
  MapControllerSetupValues &
  MapControllerMagic &
  MapControllerProps &
  MapControllerMethods;

export type MapControllerMethodDefinitions<TMethods extends object = MapControllerMethods> = TMethods &
  ThisType<MapControllerRuntime>;

export type MapControllerBoundMethods = {
  [Key in keyof MapControllerMethods]: MapControllerMethods[Key];
};

export type MapControllerStateRefs = Omit<ToRefs<MapControllerState>, 'overlayMap'> & {
  overlayMap: Ref<MapControllerMap>;
};

export type MainMapControllerReturn = MapControllerStateRefs &
  MapControllerSetupBindings &
  MapControllerComputedRefs &
  MapControllerBoundMethods;
