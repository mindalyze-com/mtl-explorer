import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MapInteractionCoordinator,
  MAP_IDENTIFICATION_TIMEOUT_MS,
  type MapInteractionContext,
  type MapInteractionProvider,
  type MapInteractionProviderResult,
} from '@/components/map/mapInteractionCoordinator';
import { mapTargetKey, type MapTargetCandidate } from '@/components/map/mapInteractionDecision';
import { TrackProcessingStatusDtoStateEnum, type TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';

const context = (): MapInteractionContext => ({
  lngLat: { lng: 8, lat: 47 },
  point: { x: 120, y: 80 },
  inputKind: 'pointer',
  trackSetRevision: 6,
  mediaScope: 'allIndexed',
  eligibility: { tracks: true, media: true, overlay: true },
});
const track = (trackId: number): MapTargetCandidate => ({
  id: String(trackId),
  source: 'tracks',
  kind: 'track',
  trackId,
  label: `Track ${trackId}`,
});
const photo: MapTargetCandidate = { id: '5', source: 'media', kind: 'photo', mediaId: 5, count: 1, label: 'Photo' };
const poi: MapTargetCandidate = {
  id: '18',
  source: 'pois',
  kind: 'poi',
  coordinates: [8, 47],
  properties: { name: 'Spring' },
  label: 'Spring',
};
const point: MapTargetCandidate = {
  id: '1:4',
  source: 'track-points',
  kind: 'track-point',
  trackId: 1,
  pointIndex: 4,
  label: 'Point 5',
};
const overlay = (id: string): MapTargetCandidate => ({
  id,
  source: 'wanderwege',
  kind: 'overlay',
  label: `Trail ${id}`,
  route: { name: `Trail ${id}`, type: 'Trail', icon: 'bi-map' },
});

function controlledProvider(id: string, kind: MapInteractionProvider['kind'] = 'overlay') {
  let resolve!: (result: MapInteractionProviderResult) => void;
  let reject!: (error: unknown) => void;
  let signal!: AbortSignal;
  let publish!: (result: MapInteractionProviderResult) => void;
  const pending = new Promise<MapInteractionProviderResult>((accept, fail) => {
    resolve = accept;
    reject = fail;
  });
  const run = vi.fn(
    (
      _context: MapInteractionContext,
      requestSignal: AbortSignal,
      partial?: (result: MapInteractionProviderResult) => void
    ) => {
      signal = requestSignal;
      publish = partial!;
      return pending;
    }
  );
  const provider: MapInteractionProvider = { id, label: id, kind, run };
  return {
    provider,
    resolve,
    reject,
    run,
    publish: (result: MapInteractionProviderResult) => publish(result),
    get signal() {
      return signal;
    },
  };
}

async function settle(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('map interaction coordinator arrival order', () => {
  it('publishes partial targets promptly and refines matching rows without moving them or dispatching selection again', async () => {
    const source = controlledProvider('tracks', 'tracks');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [], [source.provider]);
    const processing = { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 1 };
    source.publish({ candidates: [track(1), track(2)], processing });
    const keys = coordinator.state!.candidates.map(mapTargetKey);
    const selected = coordinator.state!.candidates[0];
    coordinator.select(mapTargetKey(selected));
    source.publish({
      candidates: [
        { ...track(2), matchedMediaCount: 3, distanceMeters: 0 },
        { ...track(1), matchedMediaCount: 4, distanceMeters: 9 },
      ],
    });
    expect(coordinator.state!.candidates.map(mapTargetKey)).toEqual(keys);
    expect(coordinator.state?.candidates).toMatchObject([{ matchedMediaCount: 4 }, { matchedMediaCount: 3 }]);
    expect(coordinator.state?.selected).toBe(selected);
    expect(coordinator.state?.presentation).toBe('target');
    expect(coordinator.state?.processing).toEqual(processing);
    expect(coordinator.state?.sources[0].status).toBe('pending');
    expect(onSelect).toHaveBeenCalledOnce();
    source.resolve({
      candidates: [
        { ...track(2), matchedMediaCount: 3 },
        { ...track(1), matchedMediaCount: 4 },
      ],
    });
    await settle();
    expect(coordinator.state!.candidates.map(mapTargetKey)).toEqual(keys);
    expect(coordinator.state?.selected).toBe(selected);
    expect(coordinator.state?.presentation).toBe('target');
    expect(coordinator.state?.sources[0].status).toBe('complete');
    expect(onSelect).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('retains already published hits on movement and rejects late partial and final refinements', async () => {
    const source = controlledProvider('tracks', 'tracks');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(context(), [], [source.provider]);
    source.publish({ candidates: [track(1)] });
    coordinator.cancelDiscovery();
    expect(source.signal.aborted).toBe(true);
    expect(coordinator.state?.candidates).toEqual([track(1)]);
    source.publish({ candidates: [{ ...track(1), matchedMediaCount: 99 }, track(2)] });
    source.resolve({ candidates: [{ ...track(1), matchedMediaCount: 99 }, track(2)] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([track(1)]);
    expect(coordinator.state?.sources[0].status).toBe('cancelled');
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each(['dismissal', 'new click', 'timeout'])('rejects late partial results after %s', async (reason) => {
    const source = controlledProvider('tracks', 'tracks');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(context(), [], [source.provider]);
    source.publish({ candidates: [track(1)] });
    if (reason === 'dismissal') coordinator.end();
    if (reason === 'new click') coordinator.start(context(), [poi]);
    if (reason === 'timeout') vi.advanceTimersByTime(MAP_IDENTIFICATION_TIMEOUT_MS);
    const state = coordinator.state;
    source.publish({ candidates: [track(2)] });
    source.resolve({ candidates: [track(2)] });
    await settle();
    expect(coordinator.state).toBe(state);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('opens All immediately and retains an empty click for the empty-state UI', () => {
    const onChange = vi.fn();
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange, onSelect });
    const id = coordinator.start(context());
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toEqual([]);
    expect(coordinator.isCurrent(id)).toBe(true);
    expect(onChange).toHaveBeenCalledExactlyOnceWith(coordinator.state);
    expect(onSelect).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each([photo, poi, point, track(1), overlay('a')])(
    'offers a lone local $kind in All until explicitly selected',
    (candidate) => {
      const onSelect = vi.fn();
      const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
      coordinator.start(context(), [candidate]);
      expect(coordinator.state?.presentation).toBe('chooser');
      expect(coordinator.state?.selected).toBeNull();
      expect(onSelect).not.toHaveBeenCalled();
      expect(coordinator.select(mapTargetKey(candidate))).toBe(true);
      expect(coordinator.state?.presentation).toBe('target');
      expect(onSelect).toHaveBeenCalledExactlyOnceWith(candidate, coordinator.state);
    }
  );

  it('keeps a clicked photo in All while independent discoveries add other targets', async () => {
    const onChange = vi.fn();
    const onSelect = vi.fn();
    const tracks = controlledProvider('tracks', 'tracks');
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange, onSelect });
    coordinator.start(context(), [photo], [tracks.provider, routes.provider]);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
    expect(onChange).toHaveBeenCalled();
    expect(tracks.run).toHaveBeenCalledTimes(1);
    expect(routes.run).toHaveBeenCalledTimes(1);
    tracks.resolve({ candidates: [track(1)] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(coordinator.state?.candidates).toEqual([photo, track(1)]);
    routes.resolve({ candidates: [overlay('a'), overlay('b')] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toHaveLength(4);
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it.each(['tracks', 'routes'])(
    'keeps one normalized photo in All after %s is the final completed discovery',
    async (last) => {
      const tracks = controlledProvider('tracks', 'tracks');
      const routes = controlledProvider('routes');
      const onChange = vi.fn();
      const onSelect = vi.fn();
      const coordinator = new MapInteractionCoordinator({ onChange, onSelect });
      coordinator.start(context(), [photo, { ...photo }], [tracks.provider, routes.provider]);
      expect(coordinator.state?.candidates).toEqual([photo]);
      expect(coordinator.state?.presentation).toBe('chooser');
      (last === 'tracks' ? routes : tracks).resolve({ candidates: [] });
      await settle();
      expect(coordinator.state?.presentation).toBe('chooser');
      expect(coordinator.state?.selected).toBeNull();
      expect(onSelect).not.toHaveBeenCalled();
      (last === 'tracks' ? tracks : routes).resolve({ candidates: [] });
      await settle();
      expect(coordinator.state?.presentation).toBe('chooser');
      expect(coordinator.state?.selected).toBeNull();
      expect(onSelect).not.toHaveBeenCalled();
      coordinator.cancelDiscovery();
      expect(onSelect).not.toHaveBeenCalled();
    }
  );

  it.each([
    ['tracks', track(1)],
    ['overlay', overlay('a')],
  ] as const)('offers a final lone %s provider result without selecting it', async (kind, candidate) => {
    const source = controlledProvider('source', kind);
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [], [source.provider]);
    expect(coordinator.state?.presentation).toBe('chooser');
    source.resolve({ candidates: [candidate] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([candidate]);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('preserves an explicit photo selection while pending discovery adds alternatives', async () => {
    const tracks = controlledProvider('tracks', 'tracks');
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [photo], [tracks.provider, routes.provider]);
    expect(coordinator.select(mapTargetKey(photo))).toBe(true);
    expect(coordinator.state?.presentation).toBe('target');
    tracks.resolve({ candidates: [track(1)] });
    await settle();
    expect(coordinator.state?.selected).toEqual(photo);
    expect(coordinator.state?.presentation).toBe('target');
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([photo, track(1), overlay('a')]);
    expect(coordinator.state?.presentation).toBe('target');
    expect(coordinator.state?.selected).toEqual(photo);
    expect(onSelect).toHaveBeenCalledOnce();
  });

  it('collects every local overlap before choosing an initial target', () => {
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [photo, { ...photo, id: '6', mediaId: 6 }]);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('keeps a discovered track in All while an external source is pending', async () => {
    const tracks = controlledProvider('tracks', 'tracks');
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [], [tracks.provider, routes.provider]);
    tracks.resolve({ candidates: [track(1)] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(coordinator.state?.sources[1].status).toBe('pending');
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('keeps a discovered overlay in All when a later track adds alternatives', async () => {
    const tracks = controlledProvider('tracks', 'tracks');
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [], [tracks.provider, routes.provider]);
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(coordinator.state?.sources[0].status).toBe('pending');
    tracks.resolve({ candidates: [track(1)] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([overlay('a'), track(1)]);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it.each(['tracks', 'routes'])(
    'makes two tracks and two features accessible when %s finishes first',
    async (first) => {
      const tracks = controlledProvider('tracks', 'tracks');
      const routes = controlledProvider('routes');
      const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
      coordinator.start(context(), [], [tracks.provider, routes.provider]);
      const completeTracks = () => tracks.resolve({ candidates: [track(1), track(2)] });
      const completeRoutes = () => routes.resolve({ candidates: [overlay('a'), overlay('b')] });
      (first === 'tracks' ? completeTracks : completeRoutes)();
      await settle();
      const existing = coordinator.state!.candidates.map(mapTargetKey);
      (first === 'tracks' ? completeRoutes : completeTracks)();
      await settle();
      expect(coordinator.state?.presentation).toBe('chooser');
      expect(coordinator.state?.candidates).toHaveLength(4);
      expect(coordinator.state!.candidates.slice(0, 2).map(mapTargetKey)).toEqual(existing);
    }
  );

  it('preserves indexing notices and permits other sources to continue', async () => {
    const tracks = controlledProvider('tracks', 'tracks');
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(context(), [], [tracks.provider, routes.provider]);
    const processing = { state: TrackProcessingStatusDtoStateEnum.Processing } as TrackProcessingStatusDto;
    tracks.resolve({ candidates: [], processing });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.processing).toEqual(processing);
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.processing).toEqual(processing);
    expect(coordinator.state?.candidates).toEqual([overlay('a')]);
  });
});

describe('map loading, failures, and cancellation', () => {
  it('opens All immediately for a pending empty lookup and retains confirmed empty results', async () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    const id = coordinator.start(context(), [], [routes.provider]);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.sources[0].status).toBe('pending');
    expect(vi.getTimerCount()).toBe(1);
    routes.resolve({ candidates: [] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toEqual([]);
    expect(coordinator.state?.sources[0].status).toBe('complete');
    expect(coordinator.isCurrent(id)).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('keeps successful content and source failure distinct from an empty result', async () => {
    const routes = controlledProvider('routes');
    const onProviderError = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError });
    coordinator.start(context(), [photo], [routes.provider]);
    coordinator.select(mapTargetKey(photo));
    const error = new Error('Service unavailable');
    routes.reject(error);
    await settle();
    expect(coordinator.state?.selected).toEqual(photo);
    expect(coordinator.state?.sources[0].status).toBe('failed');
    expect(onProviderError).toHaveBeenCalledWith(error, coordinator.state?.sources[0], coordinator.state?.context);
  });

  it('retains All with an unavailable status distinct from confirmed empty', async () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(context(), [], [routes.provider]);
    routes.reject(new Error('Unavailable'));
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toEqual([]);
    expect(coordinator.state?.sources[0].status).toBe('failed');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('bounds each provider independently and rejects late results from an operation ignoring abort', async () => {
    const tracks = controlledProvider('tracks', 'tracks');
    const routes = controlledProvider('routes');
    const onProviderError = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError });
    coordinator.start(context(), [], [tracks.provider, { ...routes.provider, timeoutMs: 500 }]);
    vi.advanceTimersByTime(500);
    expect(routes.signal.aborted).toBe(true);
    expect(coordinator.state?.sources.map((source) => source.status)).toEqual(['pending', 'timed-out']);
    tracks.resolve({ candidates: [track(1)] });
    routes.resolve({ candidates: [overlay('stale')] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([track(1)]);
    expect(coordinator.state?.selected).toBeNull();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(onProviderError).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('uses the default timeout and distinguishes timeout from confirmed no hits', () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(context(), [], [routes.provider]);
    vi.advanceTimersByTime(MAP_IDENTIFICATION_TIMEOUT_MS);
    expect(coordinator.state?.sources[0].status).toBe('timed-out');
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toEqual([]);
  });

  it('handles a synchronous provider failure while another source still resolves', async () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError: vi.fn() });
    coordinator.start(
      context(),
      [],
      [
        {
          id: 'tracks',
          label: 'Tracks',
          kind: 'tracks',
          run: () => {
            throw new Error('Unavailable');
          },
        },
        routes.provider,
      ]
    );
    expect(routes.run).toHaveBeenCalledTimes(1);
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.sources.map((source) => source.status)).toEqual(['failed', 'complete']);
  });

  it('manual movement retains targets and selected content, stopping discovery even if abort is ignored', async () => {
    const routes = controlledProvider('routes');
    const onChange = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange });
    const click = context();
    const id = coordinator.start(click, [photo], [routes.provider]);
    coordinator.select(mapTargetKey(photo));
    coordinator.showChooser();
    coordinator.cancelDiscovery();
    const retained = coordinator.state;
    expect(coordinator.isCurrent(id)).toBe(true);
    expect(retained?.presentation).toBe('chooser');
    expect(retained?.selected).toEqual(photo);
    expect(retained?.context).toEqual(click);
    expect(retained?.sources[0].status).toBe('cancelled');
    expect(routes.signal.aborted).toBe(true);
    routes.resolve({ candidates: [overlay('late')] });
    await settle();
    expect(coordinator.state).toBe(retained);
    expect(coordinator.back()).toBe(true);
    expect(coordinator.state?.selected).toEqual(photo);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('manual movement retains All with cancelled status rather than reporting completed empty results', () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(context(), [], [routes.provider]);
    coordinator.cancelDiscovery();
    vi.advanceTimersByTime(MAP_IDENTIFICATION_TIMEOUT_MS);
    expect(coordinator.state?.sources[0].status).toBe('cancelled');
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('rejects an old click result and double-click cleanup scoped to that click', async () => {
    const old = controlledProvider('routes');
    const latest = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    const firstId = coordinator.start(context(), [], [old.provider]);
    const nextId = coordinator.start({ ...context(), lngLat: { lng: 9, lat: 48 } }, [photo], [latest.provider]);
    expect(nextId).toBeGreaterThan(firstId);
    expect(old.signal.aborted).toBe(true);
    coordinator.end(firstId);
    old.resolve({ candidates: [overlay('stale')] });
    await settle();
    expect(coordinator.isCurrent(nextId)).toBe(true);
    expect(coordinator.state?.candidates).toEqual([photo]);
    latest.resolve({ candidates: [overlay('current')] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([photo, overlay('current')]);
  });

  it('dismissal or context invalidation removes alternatives and cannot reopen from a late result', async () => {
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    const id = coordinator.start(context(), [photo], [routes.provider]);
    coordinator.select(mapTargetKey(photo));
    coordinator.end(id);
    routes.resolve({ candidates: [overlay('late')] });
    await settle();
    expect(coordinator.state).toBeNull();
    expect(coordinator.isCurrent(id)).toBe(false);
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('captures filter, eligibility, and location values independently of mutable caller state', () => {
    const click = context();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.start(click, [photo]);
    click.lngLat.lng = 30;
    click.point.x = 900;
    click.eligibility = { tracks: false };
    click.trackSetRevision = 10;
    expect(coordinator.state?.context).toEqual(context());
  });
});

describe('map chooser navigation and consumer registration', () => {
  it('contains a local collector failure while other targets remain in All until discovery settles', async () => {
    const tracks = controlledProvider('tracks', 'tracks');
    const onProviderError = vi.fn();
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect, onProviderError });
    const error = new Error('Style no longer has this layer');
    coordinator.registerConsumer({
      id: 'pois',
      label: 'Points of interest',
      kind: 'local',
      collect: () => {
        throw error;
      },
    });
    coordinator.registerConsumer({ id: 'photos', kind: 'local', collect: () => [photo] });
    coordinator.registerConsumer({ id: 'tracks', kind: 'async', provider: tracks.provider });
    const id = coordinator.start(context());
    expect(coordinator.isCurrent(id)).toBe(true);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
    expect(onProviderError).toHaveBeenCalledExactlyOnceWith(
      error,
      { id: 'pois', label: 'Points of interest', kind: 'local', status: 'failed' },
      coordinator.state?.context
    );
    tracks.resolve({ candidates: [track(1)] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([photo, track(1)]);
    expect(coordinator.state?.sources.map((source) => source.status)).toEqual(['failed', 'complete', 'complete']);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(onSelect).not.toHaveBeenCalled();
  });

  it.each(['local', 'async'] as const)(
    'contains a throwing %s eligibility check and marks that source unavailable',
    async (kind) => {
      const failed = controlledProvider('routes');
      const tracks = controlledProvider('tracks', 'tracks');
      const onProviderError = vi.fn();
      const local = vi.fn(() => [photo]);
      const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError });
      const common = {
        id: 'source',
        label: 'Photos',
        isEligible: () => {
          throw new Error('Cannot inspect map style');
        },
      };
      coordinator.registerConsumer(
        kind === 'local' ? { ...common, kind, collect: local } : { ...common, kind, provider: failed.provider }
      );
      coordinator.registerConsumer({ id: 'tracks', kind: 'async', provider: tracks.provider });
      coordinator.start(context());
      expect(local).not.toHaveBeenCalled();
      expect(failed.run).not.toHaveBeenCalled();
      expect(onProviderError).toHaveBeenCalledOnce();
      expect(coordinator.state?.sources[0]).toEqual(
        kind === 'local'
          ? { id: 'source', label: 'Photos', kind: 'local', status: 'failed' }
          : { id: 'routes', label: 'routes', kind: 'overlay', status: 'failed' }
      );
      tracks.resolve({ candidates: [track(1)] });
      await settle();
      expect(coordinator.state?.presentation).toBe('chooser');
      expect(coordinator.state?.selected).toBeNull();
      expect(coordinator.state?.sources[0].status).toBe('failed');
    }
  );

  it('retains All with unavailable status when the only local collector fails', () => {
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError: vi.fn() });
    coordinator.registerConsumer({
      id: 'photos',
      label: 'Photos',
      kind: 'local',
      collect: () => {
        throw new Error('Unavailable');
      },
    });
    coordinator.start(context());
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.sources).toEqual([{ id: 'photos', label: 'Photos', kind: 'local', status: 'failed' }]);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('reopens the selected row without reloading content and permits explicit alternative selection', async () => {
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [photo], [routes.provider]);
    coordinator.select(mapTargetKey(photo));
    coordinator.showChooser();
    expect(coordinator.select(mapTargetKey(photo))).toBe(true);
    expect(onSelect).toHaveBeenCalledTimes(1);
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    coordinator.showChooser();
    expect(coordinator.select(mapTargetKey(overlay('a')))).toBe(true);
    expect(coordinator.state?.selected).toEqual(overlay('a'));
    expect(coordinator.state?.presentation).toBe('target');
    expect(onSelect).toHaveBeenCalledTimes(2);
    expect(coordinator.select('missing')).toBe(false);
  });

  it('does not create a second selection just because more results arrive during alternative detail work', async () => {
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [photo, track(1)], [routes.provider]);
    coordinator.select(mapTargetKey(track(1)));
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.selected).toEqual(track(1));
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  it('keeps an explicit return to All visible when pending discovery finishes', async () => {
    const routes = controlledProvider('routes');
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onSelect });
    coordinator.start(context(), [photo], [routes.provider]);
    coordinator.select(mapTargetKey(photo));
    coordinator.showChooser();
    routes.resolve({ candidates: [] });
    await settle();
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toEqual(photo);
    expect(onSelect).toHaveBeenCalledOnce();
    expect(coordinator.back()).toBe(true);
    expect(coordinator.state?.presentation).toBe('target');
    expect(onSelect).toHaveBeenCalledOnce();
  });

  it('does not start providers when the initial chooser publication dismisses the interaction', () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({
      onChange: (state) => {
        if (state) coordinator.end(state.id);
      },
    });
    coordinator.start(context(), [], [routes.provider]);
    expect(coordinator.state).toBeNull();
    expect(routes.run).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not dispatch a stale selection when its publication starts a newer click', () => {
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({
      onChange: (state) => {
        if (state?.presentation === 'target') coordinator.start(context(), [poi]);
      },
      onSelect,
    });
    const id = coordinator.start(context(), [photo]);
    expect(coordinator.select(mapTargetKey(photo))).toBe(true);
    expect(coordinator.state?.id).toBeGreaterThan(id);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toEqual([poi]);
    expect(coordinator.state?.selected).toBeNull();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('dispatches only the latest nested selection within the same interaction', () => {
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({
      onChange: (state) => {
        if (state?.presentation === 'target' && state.selected === photo) coordinator.select(mapTargetKey(poi));
      },
      onSelect,
    });
    const id = coordinator.start(context(), [photo, poi]);
    coordinator.select(mapTargetKey(photo));
    expect(coordinator.state?.id).toBe(id);
    expect(coordinator.state?.selected).toEqual(poi);
    expect(onSelect).toHaveBeenCalledExactlyOnceWith(poi, coordinator.state);
  });

  it('does not open stale details when selection publication returns to All', () => {
    const onSelect = vi.fn();
    const coordinator = new MapInteractionCoordinator({
      onChange: (state) => {
        if (state?.presentation === 'target') coordinator.showChooser();
      },
      onSelect,
    });
    const id = coordinator.start(context(), [photo]);
    coordinator.select(mapTargetKey(photo));
    expect(coordinator.state?.id).toBe(id);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toEqual(photo);
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('records successful empty local lookups while omitting ineligible consumers', () => {
    const ineligible = vi.fn(() => []);
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    for (const [id, label] of [
      ['photos', 'Photos'],
      ['pois', 'Points of interest'],
      ['points', 'Track points'],
    ]) {
      coordinator.registerConsumer({ id, label, kind: 'local', collect: () => [] });
    }
    coordinator.registerConsumer({ id: 'disabled', kind: 'local', collect: ineligible, isEligible: () => false });
    coordinator.start(context());
    expect(coordinator.state?.sources).toEqual([
      { id: 'photos', label: 'Photos', kind: 'local', status: 'complete' },
      { id: 'pois', label: 'Points of interest', kind: 'local', status: 'complete' },
      { id: 'points', label: 'Track points', kind: 'local', status: 'complete' },
    ]);
    expect(coordinator.state?.candidates).toEqual([]);
    expect(ineligible).not.toHaveBeenCalled();
  });

  it.each(['complete', 'failed'] as const)(
    'keeps one source identity when an active provider overlaps a %s local collector',
    async (status) => {
      const provider = controlledProvider('photos');
      const onProviderError = vi.fn();
      const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError });
      coordinator.registerConsumer({
        id: 'photos',
        label: 'Photos',
        kind: 'local',
        collect: () => {
          if (status === 'failed') throw new Error('Unavailable');
          return [photo];
        },
      });
      coordinator.start(context(), [], [provider.provider, provider.provider]);
      expect(coordinator.state?.sources).toEqual([
        { id: 'photos', label: 'photos', kind: 'overlay', status: 'pending' },
      ]);
      expect(provider.run).toHaveBeenCalledOnce();
      expect(onProviderError).toHaveBeenCalledTimes(status === 'failed' ? 1 : 0);
      provider.resolve({ candidates: [overlay('a')] });
      await settle();
      expect(coordinator.state?.sources).toEqual([
        { id: 'photos', label: 'photos', kind: 'overlay', status: 'complete' },
      ]);
    }
  );

  it('lets components register local collectors and independent providers with per-click eligibility', async () => {
    const routes = controlledProvider('routes');
    const local = vi.fn(() => [photo]);
    const ineligible = vi.fn(() => [track(1)]);
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.registerConsumer({ id: 'photos', kind: 'local', collect: local });
    coordinator.registerConsumer({ id: 'tracks', kind: 'local', collect: ineligible, isEligible: () => false });
    coordinator.registerConsumer({
      id: 'routes',
      kind: 'async',
      provider: routes.provider,
      isEligible: (click) => click.eligibility.overlay === true,
    });
    coordinator.start(context());
    expect(local).toHaveBeenCalledTimes(1);
    expect(ineligible).not.toHaveBeenCalled();
    expect(routes.run).toHaveBeenCalledTimes(1);
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.selected).toBeNull();
    routes.resolve({ candidates: [overlay('a')] });
    await settle();
    expect(coordinator.state?.candidates).toEqual([photo, overlay('a')]);
  });

  it('invalidates active results when a consumer unregisters and ignores cleanup from a replaced registration', () => {
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    const oldCleanup = coordinator.registerConsumer({ id: 'photos', kind: 'local', collect: () => [photo] });
    const currentCleanup = coordinator.registerConsumer({ id: 'photos', kind: 'local', collect: () => [photo] });
    const id = coordinator.start(context());
    oldCleanup();
    expect(coordinator.isCurrent(id)).toBe(true);
    currentCleanup();
    expect(coordinator.state).toBeNull();
    coordinator.start(context());
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(coordinator.state?.candidates).toEqual([]);
  });

  it('dispatches duplicate async provider identities only once for an accepted click', () => {
    const routes = controlledProvider('routes');
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn() });
    coordinator.registerConsumer({ id: 'routes', kind: 'async', provider: routes.provider });
    coordinator.start(context(), [], [routes.provider, routes.provider]);
    expect(routes.run).toHaveBeenCalledTimes(1);
    expect(coordinator.state?.sources).toHaveLength(1);
  });
});
