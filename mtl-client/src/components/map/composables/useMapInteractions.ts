import { markRaw, watch } from 'vue';
import { Marker, type MapMouseEvent, type Map as MapLibreMap } from 'maplibre-gl';
import { MediaOverlay, type MediaOverlayCandidate } from '@/layers/MediaOverlay';
import { fetchTrackIdsWithinDistanceOfPoint, fetchTrackMediaOptionsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
import { warnSanitized } from '@/utils/safeLogging';
import { haversineDistance } from '../mapGeometry';
import { projectClickToTrackLine } from '../trackLineHitTest';
import { needsTrackProcessingNotice } from '../trackProximitySelection';
import { identifyWaymarkedTrails, type WaymarkedTrailActivity } from '../waymarkedTrailProvider';
import { buildSwissMapUrl, fetchSwissRouteDetails } from '../swissRouteDetails';
import { isAbortLikeError } from '@/utils/errors';
import { toValidDateMs } from '@/utils/Utils';
import {
  MapInteractionCoordinator,
  type MapInteractionState,
  type MapInteractionConsumer,
  type MapInteractionContext,
  MAP_IDENTIFICATION_TIMEOUT_MS,
} from '../mapInteractionCoordinator';
import {
  mapInteractionTolerance,
  mapTargetKey,
  type MapTargetCandidate,
  type MapInteractionInputKind,
} from '../mapInteractionDecision';
import type { PoiMapCandidate } from './useMapPois';
import type { TrackPointMapCandidate } from './useTrackPointLayer';
import type {
  MapControllerMethodDefinitions,
  MapControllerRuntime,
  MapPoint,
  TrackFeature,
} from './mapControllerRuntime';
import type { useFilterStore } from '@/stores/filterStore';
import type { useMediaFilterStore } from '@/stores/mediaFilterStore';
import type { useMapSettingsStore } from '@/stores/mapSettingsStore';
import type { NearbyTrackMediaDto } from 'x8ing-mtl-api-typescript-fetch';

export type MapInteractionMethods = {
  registerMapInteractionConsumer(consumer: MapInteractionConsumer): () => void;
  installMapInteractions(): void;
  disposeMapInteractions(): void;
  endMapInteraction(closeContent?: boolean): void;
  selectMapTarget(key: string): void;
  showMapTargetChooser(): void;
  backFromMapTargetChooser(): void;
  openMapInteractionTrackDetails(): void;
};

const IDENTIFIABLE_ROUTE_LAYERS = ['wanderland', 'veloland', 'mountainbikeland', 'wanderwege'];
const WAYMARKED_CONSUMERS: Array<{ activity: WaymarkedTrailActivity; layer: string; label: string }> = [
  { activity: 'hiking', layer: 'wmt-hiking', label: 'Waymarked Trails · Hiking' },
  { activity: 'cycling', layer: 'wmt-cycling', label: 'Waymarked Trails · Cycling' },
  { activity: 'mtb', layer: 'wmt-mtb', label: 'Waymarked Trails · Mountain biking' },
];
export const MAP_TRACK_MEDIA_OPTIONS_TIMEOUT_MS = 3_000;
// MapLibre recognizes double taps up to 500 ms apart. Confirm selection after that window.
export const MAP_CLICK_CONFIRMATION_DELAY_MS = 500;

/** Equivalent settings copies do not change a click's eligible target context. */
export function watchMapInteractionContext(
  values: () => readonly unknown[],
  invalidate: () => void
): ReturnType<typeof watch> {
  return watch(
    () => JSON.stringify(values(), (_key, value) => (value instanceof Set ? [...value].sort() : value)),
    invalidate,
    { flush: 'sync' }
  );
}

function nonnegativeFact(value: number | undefined): number | undefined {
  return Number.isFinite(value) && value! >= 0 ? value : undefined;
}

function renderedTrackDistancePx(
  map: MapLibreMap,
  feature: TrackFeature | undefined,
  context: MapInteractionContext
): number | undefined {
  const geometry = feature?.geometry;
  if (!geometry) return undefined;
  const clickPoint = map.project(context.lngLat);
  const distanceTo = (coordinates: [number, number]) => {
    const point = map.project(coordinates);
    return Math.hypot(point.x - clickPoint.x, point.y - clickPoint.y);
  };
  if (geometry.type === 'Point')
    return nonnegativeFact(distanceTo(geometry.coordinates.slice(0, 2) as [number, number]));
  const lines =
    geometry.type === 'LineString'
      ? [geometry.coordinates]
      : geometry.type === 'MultiLineString'
        ? geometry.coordinates
        : [];
  let bestDistance = Number.POSITIVE_INFINITY;
  for (const line of lines) {
    const projection = projectClickToTrackLine({
      map,
      clickPoint,
      lngLat: context.lngLat,
      coordinates: line.map((coordinate) => coordinate.slice(0, 2) as [number, number]),
      pixelTolerance: Number.POSITIVE_INFINITY,
    });
    if (projection) bestDistance = Math.min(bestDistance, distanceTo(projection.anchor));
  }
  return nonnegativeFact(bestDistance);
}

/** Optional photo facts cannot discard successful GPS discovery or outlive its click. */
async function loadNearbyTrackMediaFacts(
  context: MapInteractionContext,
  distanceInMeters: number,
  signal: AbortSignal,
  controller: AbortController
): Promise<NearbyTrackMediaDto[] | null> {
  if (!context.eligibility.photos || signal.aborted) return null;
  let settleInterrupted!: (value: null) => void;
  const interrupted = new Promise<null>((resolve) => {
    settleInterrupted = resolve;
  });
  const interrupt = () => settleInterrupted(null);
  controller.signal.addEventListener('abort', interrupt, { once: true });
  const cancel = () => {
    controller.abort();
    settleInterrupted(null);
  };
  signal.addEventListener('abort', cancel, { once: true });
  const timeout = setTimeout(() => {
    warnSanitized('Nearby GPS track photo facts timed out', new Error('Track photo facts timed out'), {
      topic1: 'map',
      topic2: 'useMapInteractions',
      topic3: 'trackMediaFacts',
    });
    cancel();
  }, MAP_TRACK_MEDIA_OPTIONS_TIMEOUT_MS);
  try {
    const operation = fetchTrackMediaOptionsWithinDistanceOfPoint(
      context.lngLat.lng,
      context.lngLat.lat,
      distanceInMeters,
      controller.signal
    );
    return await Promise.race([operation, interrupted]);
  } catch (error: unknown) {
    if (!signal.aborted && !controller.signal.aborted) {
      warnSanitized('Nearby GPS track photo facts are unavailable', error, {
        topic1: 'map',
        topic2: 'useMapInteractions',
        topic3: 'trackMediaFacts',
      });
    }
    return null;
  } finally {
    clearTimeout(timeout);
    signal.removeEventListener('abort', cancel);
    controller.signal.removeEventListener('abort', interrupt);
  }
}

/** Map/UI adapters only. The dispatcher itself has no dependency on Vue or MapLibre. */
export function useMapInteractions(deps: {
  filterStore: ReturnType<typeof useFilterStore>;
  mediaFilterStore: ReturnType<typeof useMediaFilterStore>;
  mapSettingsStore: ReturnType<typeof useMapSettingsStore>;
  collectPois: (point: MapPoint, radius: number) => PoiMapCandidate[];
}): MapControllerMethodDefinitions<MapInteractionMethods> {
  let coordinator: MapInteractionCoordinator | null = null;
  let cleanup: (() => void) | null = null;
  const additionalConsumers = new Map<string, { consumer: MapInteractionConsumer; unregister: (() => void) | null }>();
  let marker: Marker | null = null;
  let selectedDisplayGeneration = 0;
  let selectedDetailsController: AbortController | null = null;
  let selectedDetailsTimer: ReturnType<typeof setTimeout> | null = null;
  let pendingClickTimer: ReturnType<typeof setTimeout> | null = null;
  const mediaCandidates = new Map<string, MediaOverlayCandidate>();
  const pointCandidates = new Map<string, TrackPointMapCandidate>();

  function cancelPendingClick() {
    if (pendingClickTimer !== null) clearTimeout(pendingClickTimer);
    pendingClickTimer = null;
  }

  function cancelSelectedDetails() {
    selectedDetailsController?.abort();
    selectedDetailsController = null;
    if (selectedDetailsTimer !== null) clearTimeout(selectedDetailsTimer);
    selectedDetailsTimer = null;
  }

  function closeContent(runtime: MapControllerRuntime) {
    selectedDisplayGeneration += 1;
    cancelSelectedDetails();
    runtime.mapInteractionOverlayDetails = null;
    runtime.closeTrackPointPopup();
    runtime.trackDetailsVisible = false;
    runtime.trackDetailsId = null;
    runtime.closeMediaSelection();
    runtime.mediaSheetVisible = false;
    runtime.closeMediaSheet();
    runtime.closeSelectionPopup();
    runtime.mapInteractionDetailKind = null;
    runtime.mapInteractionDetailLoading = false;
    runtime.mapInteractionDetailError = null;
  }

  async function openTarget(runtime: MapControllerRuntime, target: MapTargetCandidate, state: MapInteractionState) {
    closeContent(runtime);
    if (target.kind !== 'track' && target.kind !== 'track-point') runtime.deselectTrack();
    const displayGeneration = selectedDisplayGeneration;
    const key = mapTargetKey(target);
    const isCurrent = () =>
      displayGeneration === selectedDisplayGeneration &&
      coordinator?.isCurrent(state.id) === true &&
      !!coordinator.state?.selected &&
      mapTargetKey(coordinator.state.selected) === key;
    runtime.mapInteractionDetailLoading = true;
    try {
      if (target.kind === 'track') {
        if (runtime.selectedTrackId !== target.trackId) runtime.selectTrackById(target.trackId);
        runtime.mapInteractionDetailKind = 'point';
        const pointShown = await runtime.showTrackLinePointPopup(
          { lngLat: state.context.lngLat, point: runtime.overlayMap!.project(state.context.lngLat) },
          target.trackId,
          { isCurrent, presentation: 'context' }
        );
        if (!isCurrent()) return;
        if (!pointShown) {
          runtime.mapInteractionDetailKind = 'track';
          runtime.openTrackDetails(target.trackId, 'compact');
        }
      } else if (target.kind === 'track-point') {
        runtime.mapInteractionDetailKind = 'point';
        if (runtime.selectedTrackId !== target.trackId) runtime.selectTrackById(target.trackId);
        const candidate = pointCandidates.get(target.id);
        if (candidate) await runtime.openTrackPointCandidate(candidate, isCurrent, { presentation: 'context' });
        if (isCurrent() && !runtime.trackPointsPopupContext) throw new Error('Track point details are unavailable');
      } else if (target.kind === 'photo') {
        runtime.mapInteractionDetailKind = 'photo';
        const candidate = mediaCandidates.get(target.id);
        const overlay = runtime.mediaOverlay as MediaOverlay | null;
        const scope = target.collectionScope ?? 'primary';
        const selection = candidate ? await overlay?.selectCandidate(candidate, isCurrent, scope) : null;
        if (!isCurrent()) return;
        if (!selection) throw new Error('Photo collection is no longer available');
        runtime.mediaPendingSelection = selection;
        runtime.chooseMediaCollection(scope);
      } else {
        runtime.mapInteractionDetailKind = 'simple';
        if (target.kind === 'overlay') {
          runtime.mapInteractionOverlayDetails = target.route;
          if (target.source.startsWith('ch.') && target.route.featureId) {
            const query = {
              layerBodId: target.source,
              featureId: target.route.featureId,
              lngLat: state.context.lngLat,
            };
            runtime.mapInteractionOverlayDetails = { ...target.route, swissMapUrl: buildSwissMapUrl(query) };
            const controller = new AbortController();
            selectedDetailsController = controller;
            let timedOut = false;
            selectedDetailsTimer = setTimeout(() => {
              timedOut = true;
              controller.abort();
            }, MAP_IDENTIFICATION_TIMEOUT_MS);
            try {
              const details = await fetchSwissRouteDetails(query, controller.signal);
              if (!isCurrent() || controller.signal.aborted) return;
              runtime.mapInteractionOverlayDetails = { ...target.route, ...details };
            } catch (error) {
              if (!isCurrent() || (isAbortLikeError(error, controller.signal) && !timedOut)) return;
              runtime.mapInteractionDetailError =
                'Additional route information is unavailable. You can still open the Swiss map.';
              warnSanitized('Swiss route details could not be loaded', error, {
                topic1: 'map',
                topic2: 'useMapInteractions',
                topic3: 'swissRouteDetails',
              });
            } finally {
              if (selectedDetailsController === controller) {
                selectedDetailsController = null;
                if (selectedDetailsTimer !== null) clearTimeout(selectedDetailsTimer);
                selectedDetailsTimer = null;
              }
            }
          }
        }
      }
    } catch (error) {
      if (!isCurrent()) return;
      runtime.mapInteractionDetailError = 'Details are unavailable. Select another target or close this panel.';
      warnSanitized('Map target details could not be opened', error, {
        topic1: 'map',
        topic2: 'useMapInteractions',
        topic3: 'openTarget',
      });
    } finally {
      if (isCurrent()) runtime.mapInteractionDetailLoading = false;
    }
  }

  const methods: MapControllerMethodDefinitions<MapInteractionMethods> = {
    registerMapInteractionConsumer(consumer) {
      this.endMapInteraction();
      additionalConsumers.get(consumer.id)?.unregister?.();
      const entry = { consumer, unregister: coordinator?.registerConsumer(consumer) ?? null };
      additionalConsumers.set(consumer.id, entry);
      return () => {
        if (additionalConsumers.get(consumer.id) !== entry) return;
        this.endMapInteraction();
        entry.unregister?.();
        additionalConsumers.delete(consumer.id);
      };
    },
    installMapInteractions() {
      this.disposeMapInteractions();
      const map = this.overlayMap as MapLibreMap | undefined;
      if (!map) return;
      // eslint-disable-next-line @typescript-eslint/no-this-alias -- Registered consumers retain this map runtime.
      const runtime = this;
      coordinator = new MapInteractionCoordinator({
        onChange: (state) => {
          runtime.mapInteraction = state;
        },
        onSelect: (target, state) => {
          runtime.previewTrackById?.(null);
          void openTarget(runtime, target, state);
        },
        onEnd: () => {
          runtime.previewTrackById?.(null);
          cancelSelectedDetails();
          marker?.remove();
          marker = null;
          (runtime.mediaOverlay as MediaOverlay | null)?.releaseCandidateContext?.();
          mediaCandidates.clear();
          pointCandidates.clear();
        },
        onProviderError: (error, source) =>
          warnSanitized(`Map identification unavailable: ${source.label}`, error, {
            topic1: 'map',
            topic2: 'useMapInteractions',
            topic3: 'provider',
          }),
      });
      // Each layer registers a small consumer. No layer owns exploration presentation.
      const unregister = [
        coordinator.registerConsumer({
          id: 'pois',
          label: 'Points of interest',
          kind: 'local',
          isEligible: (context) => !!context.eligibility.pois,
          collect: (context) =>
            deps.collectPois(context.point, mapInteractionTolerance(context.inputKind)).map((candidate) => ({
              id: candidate.id,
              kind: 'poi' as const,
              source: candidate.sourceId,
              sourceLabel: 'Points of interest',
              label: candidate.label,
              detail: candidate.detail,
              coordinates: candidate.coordinates,
              properties: candidate.payload,
              match: 'symbol' as const,
              distancePx: candidate.distancePx,
            })),
        }),
        coordinator.registerConsumer({
          id: 'points',
          label: 'Track points',
          kind: 'local',
          isEligible: (context) => !!context.eligibility.points,
          collect: (context) =>
            runtime
              .collectTrackPointCandidates(context.point, mapInteractionTolerance(context.inputKind))
              .map((candidate) => {
                pointCandidates.set(candidate.id, candidate);
                return {
                  id: candidate.id,
                  kind: 'track-point' as const,
                  source: candidate.sourceId,
                  sourceLabel: 'Track points',
                  label: candidate.label,
                  detail: candidate.detail,
                  trackId: candidate.trackId,
                  pointIndex: candidate.pointIndex,
                  match: 'symbol' as const,
                  distancePx: candidate.distancePx,
                };
              }),
        }),
        coordinator.registerConsumer({
          id: 'photos',
          label: 'Photos',
          kind: 'local',
          isEligible: (context) => !!context.eligibility.photos,
          collect: (context) => {
            const hits =
              (runtime.mediaOverlay as MediaOverlay | null)?.collectCandidates(
                context.point,
                mapInteractionTolerance(context.inputKind)
              ) ?? [];
            const collections: MapTargetCandidate[] = hits.map((candidate) => {
              mediaCandidates.set(candidate.key, candidate);
              return {
                id: candidate.key,
                kind: 'photo' as const,
                source: 'media',
                sourceLabel: 'Photos',
                label: candidate.kind === 'cluster' ? 'This cluster' : 'At this location',
                count: candidate.totalMediaCount,
                collectionScope: 'primary' as const,
                viewportBounds: candidate.viewportBounds,
                mediaId: candidate.mediaPoints[0]?.id ?? undefined,
                clusterId: candidate.clusterId ?? undefined,
                match: 'symbol' as const,
              };
            });
            const viewportOrigin = hits[0];
            const viewportIds = new Set(viewportOrigin?.viewportMediaPoints.map((point) => point.id));
            // A partly known cluster cannot prove that its complete collection equals the viewport.
            const matchesKnownPrimary = hits.some((candidate) => {
              const primaryIds = new Set(candidate.mediaPoints.map((point) => point.id));
              return (
                primaryIds.size === candidate.totalMediaCount &&
                primaryIds.size === viewportIds.size &&
                [...primaryIds].every((id) => viewportIds.has(id))
              );
            });
            if (viewportOrigin && viewportIds.size > 0 && !matchesKnownPrimary) {
              const id = 'media-viewport';
              mediaCandidates.set(id, viewportOrigin);
              collections.push({
                id,
                kind: 'photo',
                source: 'media',
                sourceLabel: 'Photos',
                label: 'Map view at tap',
                count: viewportIds.size,
                collectionScope: 'viewport',
                viewportBounds: viewportOrigin.viewportBounds,
                match: 'identified',
              });
            }
            return collections;
          },
        }),
        coordinator.registerConsumer({
          id: 'tracks',
          kind: 'async',
          isEligible: (context) => !!context.eligibility.tracks,
          provider: {
            id: 'tracks',
            label: 'Your tracks',
            kind: 'tracks',
            run: async (context, signal, publish) => {
              const radius = mapInteractionTolerance(context.inputKind);
              const edge = map.unproject([context.point.x + radius, context.point.y]);
              const meters = haversineDistance(context.lngLat.lat, context.lngLat.lng, edge.lat, edge.lng);
              const trackLookup = fetchTrackIdsWithinDistanceOfPoint(
                context.lngLat.lng,
                context.lngLat.lat,
                meters,
                signal
              );
              const mediaController = new AbortController();
              // If GPS discovery fails, its optional facts have no target to enrich.
              void trackLookup.catch(() => mediaController.abort());
              let mediaFactsReady = false;
              const mediaFactsLookup = loadNearbyTrackMediaFacts(context, meters, signal, mediaController).then(
                (options) => {
                  mediaFactsReady = true;
                  return options;
                }
              );
              const response = await trackLookup;
              const baseResult = {
                processing: needsTrackProcessingNotice(response.processing) ? response.processing : null,
                candidates: response.trackIds
                  .filter((trackId) => {
                    const feature = runtime.gpsTrackIdToFeature.get(trackId);
                    const track = runtime.gpsTracksById.get(trackId);
                    return (
                      !feature?.properties?.reviewMapExcluded &&
                      !track?.reviewMapExcluded &&
                      !runtime.hiddenGroups.has(String(feature?.properties?.filterGroup ?? ''))
                    );
                  })
                  .map((trackId): MapTargetCandidate => {
                    const metadata = runtime.getTrackPopupMeta(trackId);
                    const startDate = runtime.gpsTracksById.get(trackId)?.startDate;
                    const recordedAt = metadata.recordedAt ?? toValidDateMs(startDate) ?? undefined;
                    return {
                      id: String(trackId),
                      kind: 'track',
                      source: 'tracks',
                      sourceLabel: 'Your tracks',
                      trackId,
                      label: metadata.displayName || metadata.name || `Track #${trackId}`,
                      detail: metadata.date || undefined,
                      recordedAt: Number.isFinite(recordedAt) ? recordedAt : undefined,
                      lengthMeters: nonnegativeFact(metadata.lengthMeters),
                      ascentMeters: nonnegativeFact(metadata.ascentMeters),
                      distancePx: renderedTrackDistancePx(map, runtime.gpsTrackIdToFeature.get(trackId), context),
                      activityType: metadata.activityType || undefined,
                      description: metadata.description || undefined,
                      match: 'line',
                    };
                  }),
              };
              if (!mediaFactsReady) publish?.(baseResult);
              const mediaOptions = await mediaFactsLookup;
              const mediaFactsByTrack = new Map((mediaOptions ?? []).map((option) => [option.trackId, option]));
              return {
                ...baseResult,
                candidates: baseResult.candidates.map((candidate) => {
                  if (candidate.kind !== 'track') return candidate;
                  const facts = mediaFactsByTrack.get(candidate.trackId);
                  const count = nonnegativeFact(facts?.matchedMediaCount);
                  return {
                    ...candidate,
                    matchedMediaCount: count == null ? undefined : Math.floor(count),
                    distanceMeters: nonnegativeFact(facts?.distanceMeters),
                  };
                }),
              };
            },
          },
        }),
        coordinator.registerConsumer({
          id: 'routes',
          kind: 'async',
          isEligible: (context) => !!context.eligibility.overlays,
          provider: {
            id: 'routes',
            label: 'Trail overlay',
            kind: 'overlay',
            run: async (context, signal) => ({
              candidates: await runtime.identifySwissMobilityRoutes(
                context.lngLat,
                context.point,
                signal,
                mapInteractionTolerance(context.inputKind)
              ),
            }),
          },
        }),
      ];
      for (const descriptor of WAYMARKED_CONSUMERS) {
        unregister.push(
          coordinator.registerConsumer({
            id: `waymarked:${descriptor.activity}`,
            kind: 'async',
            isEligible: (context) => !!context.eligibility[descriptor.layer],
            provider: {
              id: `waymarked:${descriptor.activity}`,
              kind: 'overlay',
              label: descriptor.label,
              run: async (context, signal) => ({
                candidates: await identifyWaymarkedTrails(
                  descriptor.activity,
                  {
                    point: context.point,
                    tolerancePx: mapInteractionTolerance(context.inputKind),
                    unproject: (point) => map.unproject(point),
                  },
                  signal
                ),
              }),
            },
          })
        );
      }
      for (const entry of additionalConsumers.values()) entry.unregister = coordinator.registerConsumer(entry.consumer);
      let lastInput: MapInteractionInputKind = 'pointer';
      let gestureInteraction: number | null = null;
      let gestureSequence = 0;
      let confirmedGesture: number | null = null;
      let doubleClickGesture: number | null = null;
      const pointerDown = (event: PointerEvent) => {
        lastInput = event.pointerType === 'touch' ? 'touch' : event.pointerType === 'pen' ? 'pen' : 'pointer';
      };
      const touchStart = () => {
        lastInput = 'touch';
      };
      const explorationBlocked = (event: MapMouseEvent) =>
        event.defaultPrevented ||
        runtime.measureToolActive ||
        runtime.plannerToolActive ||
        !!runtime.geoDrawingParamDef;
      const confirmClick = (event: MapMouseEvent, inputKind: MapInteractionInputKind, sequence: number) => {
        pendingClickTimer = null;
        if (sequence !== gestureSequence || explorationBlocked(event)) return;
        runtime.endMapInteraction(true);
        runtime.clearFocusedMediaMarker();
        if (runtime.activeToolId) {
          runtime.captureActiveToolNavigation();
          runtime.closeAllToolsExcept(null);
          runtime.activeToolId = null;
        }
        const settings = deps.mapSettingsStore;
        const eligibility: Record<string, boolean | string | number> = {
          tracks: runtime.tracksEnabled && runtime.layerOpacities.tracks > 0 && !!map.getLayer('tracks-layer'),
          photos: runtime.mediaVisible && runtime.layerOpacities.media > 0,
          points: runtime.trackPointsVisible && runtime.layerOpacities.trackpoints > 0,
          pois: settings.poiEnabled && settings.poiOpacity > 0,
          overlays: runtime.activeOverlays
            .filter(
              (id) =>
                IDENTIFIABLE_ROUTE_LAYERS.includes(id) &&
                runtime.layerOpacities[id] > 0 &&
                !!map.getLayer(`${id}-overlay`)
            )
            .join(','),
        };
        for (const descriptor of WAYMARKED_CONSUMERS) {
          eligibility[descriptor.layer] =
            runtime.activeOverlays.includes(descriptor.layer) &&
            runtime.layerOpacities[descriptor.layer] > 0 &&
            !!map.getLayer(`${descriptor.layer}-overlay`);
        }
        confirmedGesture = sequence;
        gestureInteraction = coordinator!.start({
          lngLat: { lng: event.lngLat.lng, lat: event.lngLat.lat },
          point: { x: event.point.x, y: event.point.y },
          inputKind,
          trackSetRevision: deps.filterStore.trackSetRevision,
          mediaScope: deps.mediaFilterStore.scope,
          eligibility,
        });
        if (!coordinator!.isCurrent(gestureInteraction)) return;
        const element = document.createElement('div');
        element.className = 'mtl-map-interaction-marker';
        element.setAttribute('role', 'img');
        element.setAttribute('aria-label', 'Clicked location');
        marker = markRaw(new Marker({ element }).setLngLat(event.lngLat).addTo(map));
      };
      const onClick = (event: MapMouseEvent) => {
        cancelPendingClick();
        if (explorationBlocked(event)) return;
        // MapLibre still owns double-click zoom; its second click must not dispatch exploration.
        if (event.originalEvent.detail > 1) {
          doubleClickGesture = gestureSequence;
          return;
        }
        const inputKind =
          'pointerType' in event.originalEvent
            ? (event.originalEvent as PointerEvent).pointerType === 'touch'
              ? 'touch'
              : (event.originalEvent as PointerEvent).pointerType === 'pen'
                ? 'pen'
                : 'pointer'
            : lastInput;
        const sequence = ++gestureSequence;
        pendingClickTimer = setTimeout(() => confirmClick(event, inputKind, sequence), MAP_CLICK_CONFIRMATION_DELAY_MS);
      };
      const onDoubleClick = () => {
        const sequence = doubleClickGesture ?? gestureSequence;
        doubleClickGesture = null;
        if (sequence !== gestureSequence) return;
        cancelPendingClick();
        if (confirmedGesture !== sequence) return;
        const interactionId = gestureInteraction;
        if (interactionId == null || !coordinator?.isCurrent(interactionId)) return;
        coordinator.end(interactionId);
        closeContent(runtime);
        runtime.deselectTrack();
        gestureInteraction = null;
      };
      const onManualMove = (event: { originalEvent?: Event }) => {
        if (event.originalEvent) {
          cancelPendingClick();
          coordinator?.cancelDiscovery();
        }
      };
      const onStyleChange = () => runtime.endMapInteraction();
      const canvas = map.getCanvas();
      canvas.addEventListener('pointerdown', pointerDown);
      canvas.addEventListener('touchstart', touchStart, { passive: true });
      map.on('click', onClick);
      map.on('dblclick', onDoubleClick);
      map.on('movestart', onManualMove);
      map.on('style.load', onStyleChange);
      cleanup = () => {
        cancelPendingClick();
        for (const entry of additionalConsumers.values()) {
          entry.unregister?.();
          entry.unregister = null;
        }
        for (const dispose of unregister) dispose();
        canvas.removeEventListener('pointerdown', pointerDown);
        canvas.removeEventListener('touchstart', touchStart);
        map.off('click', onClick);
        map.off('dblclick', onDoubleClick);
        map.off('movestart', onManualMove);
        map.off('style.load', onStyleChange);
      };
    },
    disposeMapInteractions() {
      this.endMapInteraction(Boolean(coordinator?.state || this.mapInteraction));
      cleanup?.();
      cleanup = null;
      coordinator = null;
    },
    endMapInteraction(closeSelectedContent = false) {
      cancelPendingClick();
      const hiddenTrackContent =
        (coordinator?.state ?? this.mapInteraction)?.presentation === 'chooser' &&
        this.mapInteractionDetailKind === 'track';
      const hadPhotoContent =
        coordinator?.state?.selected?.kind === 'photo' && this.mapInteractionDetailKind !== 'track';
      coordinator?.end();
      this.mapInteraction = null;
      if (closeSelectedContent || hiddenTrackContent) {
        // Retained details belong to these results; invalidation must not reopen them as standalone GPS.
        closeContent(this);
        this.deselectTrack();
      } else if (hadPhotoContent) {
        // Collection pagination depends on the interaction's retained source tree.
        this.closeMediaSelection();
        this.mediaSheetVisible = false;
        this.closeMediaSheet();
      }
    },
    selectMapTarget(key) {
      coordinator?.select(key);
    },
    showMapTargetChooser() {
      coordinator?.showChooser();
    },
    backFromMapTargetChooser() {
      coordinator?.back();
    },
    openMapInteractionTrackDetails() {
      const target = coordinator?.state?.selected;
      if (target?.kind !== 'track-point' && target?.kind !== 'track') return;
      this.mapInteractionDetailKind = 'track';
      this.openTrackDetails(target.trackId, 'compact');
    },
  };
  return methods;
}
