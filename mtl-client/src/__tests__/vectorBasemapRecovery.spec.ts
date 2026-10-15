import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { probeVectorMapArchive } from '@/utils/mapArchiveHealth';
import { VectorBasemapRecovery } from '@/utils/vectorBasemapRecovery';

vi.mock('@/utils/mapArchiveHealth', () => ({ probeVectorMapArchive: vi.fn() }));

const tileError = { sourceId: 'protomaps', error: new Error('Bad response code: 503'), tile: {} };

function setup(fallbackApplied = false) {
  const refreshTiles = vi.fn();
  const setUrl = vi.fn();
  const onUnavailable = vi.fn();
  const onRestored = vi.fn();
  const onAuthFailure = vi.fn();
  let current = true;
  const recovery = new VectorBasemapRecovery({
    map: { refreshTiles, getSource: () => ({ url: 'pmtiles:///synthetic.pmtiles', setUrl }) } as unknown as MapLibreMap,
    archiveUrl: '/synthetic.pmtiles',
    isCurrent: () => current,
    fallbackApplied,
    onUnavailable,
    onRestored,
    onAuthFailure,
  });
  return {
    recovery,
    refreshTiles,
    setUrl,
    onUnavailable,
    onRestored,
    onAuthFailure,
    replaceMap: () => {
      current = false;
    },
  };
}

describe('vector basemap recovery', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(probeVectorMapArchive).mockReset().mockResolvedValue(undefined);
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('keeps a healthy vector archive and retries tiles once despite a burst of errors', async () => {
    const ctx = setup();
    for (let i = 0; i < 20; i++) ctx.recovery.handleError(tileError);
    await vi.advanceTimersByTimeAsync(0);
    ctx.recovery.handleError(tileError);
    expect(probeVectorMapArchive).toHaveBeenCalledOnce();
    expect(ctx.refreshTiles).toHaveBeenCalledExactlyOnceWith('protomaps');
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
    ctx.recovery.dispose();
  });

  it('retries failed source metadata while retaining vector style', async () => {
    const ctx = setup();
    ctx.recovery.handleError({ sourceId: 'protomaps', error: new Error('Failed to fetch') });
    await vi.advanceTimersByTimeAsync(0);
    expect(ctx.setUrl).toHaveBeenCalledWith('pmtiles:///synthetic.pmtiles');
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
  });

  it('retains vector after a transient archive failure followed by a successful check', async () => {
    vi.mocked(probeVectorMapArchive).mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const ctx = setup();
    ctx.recovery.handleError(tileError);
    await vi.advanceTimersByTimeAsync(1000);
    expect(probeVectorMapArchive).toHaveBeenCalledTimes(2);
    expect(ctx.refreshTiles).toHaveBeenCalledOnce();
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
  });

  it('requires three spaced failures before falling back and restores vector after recovery', async () => {
    vi.mocked(probeVectorMapArchive).mockRejectedValue(new Error('Bad response code: 503'));
    const ctx = setup();
    ctx.recovery.handleError(tileError);
    await vi.advanceTimersByTimeAsync(3999);
    expect(probeVectorMapArchive).toHaveBeenCalledTimes(2);
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(probeVectorMapArchive).toHaveBeenCalledTimes(3);
    expect(ctx.onUnavailable).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(30_000);
    expect(ctx.onRestored).not.toHaveBeenCalled();
    vi.mocked(probeVectorMapArchive).mockResolvedValue(undefined);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(ctx.onRestored).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(30_000);
    expect(probeVectorMapArchive).toHaveBeenCalledTimes(5);
  });

  it('continues recovery across a renderer rebuild that already applied temporary raster', async () => {
    const ctx = setup(true);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(ctx.onRestored).toHaveBeenCalledOnce();
  });

  it.each([
    { sourceId: 'tracks', error: new Error('Failed to fetch') },
    { sourceId: 'protomaps', error: new DOMException('Aborted', 'AbortError') },
    { sourceId: 'protomaps', error: new Error('Map archive changed; refreshing map config.') },
  ])('ignores cancellations, application sources and archive refreshes: %o', async (event) => {
    const ctx = setup();
    expect(ctx.recovery.handleError(event)).toBe(false);
    await vi.advanceTimersByTimeAsync(4000);
    expect(probeVectorMapArchive).not.toHaveBeenCalled();
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
  });

  it('leaves authentication failures to login recovery', async () => {
    vi.mocked(probeVectorMapArchive).mockRejectedValue(new Error('Bad response code: 401'));
    const ctx = setup();
    ctx.recovery.handleError(tileError);
    await vi.advanceTimersByTimeAsync(4000);
    expect(ctx.onAuthFailure).toHaveBeenCalledOnce();
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
  });

  it('cancels pending checks and recovery when the map is disposed', async () => {
    vi.mocked(probeVectorMapArchive).mockRejectedValue(new Error('Bad response code: 503'));
    const ctx = setup();
    ctx.recovery.handleError(tileError);
    await vi.advanceTimersByTimeAsync(0);
    const signal = vi.mocked(probeVectorMapArchive).mock.calls[0][1];
    ctx.recovery.dispose();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(signal.aborted).toBe(true);
    expect(probeVectorMapArchive).toHaveBeenCalledOnce();
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
    expect(ctx.onRestored).not.toHaveBeenCalled();
  });

  it('ignores stale results after a theme or source change replaces the map', async () => {
    let resolveProbe!: () => void;
    vi.mocked(probeVectorMapArchive).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveProbe = resolve;
        })
    );
    const ctx = setup();
    ctx.recovery.handleError(tileError);
    ctx.replaceMap();
    resolveProbe();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(ctx.refreshTiles).not.toHaveBeenCalled();
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
  });

  it('does not treat a tile refresh exception as archive unavailability', async () => {
    const ctx = setup();
    ctx.refreshTiles.mockImplementation(() => {
      throw new Error('Map is changing style');
    });
    ctx.recovery.handleError(tileError);
    await vi.advanceTimersByTimeAsync(4000);
    expect(probeVectorMapArchive).toHaveBeenCalledOnce();
    expect(ctx.onUnavailable).not.toHaveBeenCalled();
  });
});
