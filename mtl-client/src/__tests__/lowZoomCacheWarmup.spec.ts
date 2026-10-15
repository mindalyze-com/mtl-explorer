import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { scheduleLowZoomCacheWarmup } from '@/utils/lowZoomCacheWarmup';

function makeMap(loaded = true, moving = false) {
  const listeners = new Map<string, Set<() => void>>();
  const map = {
    loaded: vi.fn(() => loaded),
    isMoving: vi.fn(() => moving),
    on: vi.fn((event: string, callback: () => void) => {
      if (!listeners.has(event)) listeners.set(event, new Set());
      listeners.get(event)!.add(callback);
      return map;
    }),
    off: vi.fn((event: string, callback: () => void) => {
      listeners.get(event)?.delete(callback);
      return map;
    }),
    emit: (event: string) => [...(listeners.get(event) ?? [])].forEach((callback) => callback()),
    listeners,
  };
  return map;
}

describe('offline map cache warmup', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('protects initial rendering, then starts once for an already settled map', () => {
    const map = makeMap();
    const start = vi.fn();
    scheduleLowZoomCacheWarmup(map as never, start, () => true);
    map.emit('idle');
    vi.advanceTimersByTime(2999);
    expect(start).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(start).toHaveBeenCalledOnce();
    map.emit('idle');
    vi.advanceTimersByTime(20000);
    expect(start).toHaveBeenCalledOnce();
    expect(map.listeners.get('idle')?.size).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('waits for tile loading and camera movement to settle after the minimum delay', () => {
    const map = makeMap(false, true);
    const start = vi.fn();
    scheduleLowZoomCacheWarmup(map as never, start, () => true);
    vi.advanceTimersByTime(3000);
    map.emit('idle');
    expect(start).not.toHaveBeenCalled();
    map.loaded.mockReturnValue(true);
    map.emit('idle');
    expect(start).not.toHaveBeenCalled();
    map.isMoving.mockReturnValue(false);
    map.emit('idle');
    expect(start).toHaveBeenCalledOnce();
  });

  it('starts at the deadline even if continued navigation prevents idle', () => {
    const map = makeMap(false, true);
    const start = vi.fn();
    scheduleLowZoomCacheWarmup(map as never, start, () => true);
    vi.advanceTimersByTime(14999);
    expect(start).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(start).toHaveBeenCalledOnce();
  });

  it('waits for initial track loading even on a slow connection, then honors the elapsed deadline', () => {
    const map = makeMap(false, true);
    const start = vi.fn();
    let tracksReady = false;
    scheduleLowZoomCacheWarmup(
      map as never,
      start,
      () => true,
      () => tracksReady
    );
    vi.advanceTimersByTime(20000);
    expect(start).not.toHaveBeenCalled();
    tracksReady = true;
    vi.advanceTimersByTime(500);
    expect(start).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each(['cancel', 'remove'])('cleans up before download on %s', (action) => {
    const map = makeMap(false);
    const start = vi.fn();
    const cancel = scheduleLowZoomCacheWarmup(map as never, start, () => true);
    if (action === 'cancel') cancel();
    else map.emit('remove');
    map.emit('idle');
    vi.advanceTimersByTime(20000);
    expect(start).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    expect(map.listeners.get('idle')?.size).toBe(0);
    expect(map.listeners.get('remove')?.size).toBe(0);
  });

  it.each([3000, 15000])('skips obsolete or offline map configuration at %s ms', (time) => {
    const map = makeMap(time === 3000);
    const start = vi.fn();
    scheduleLowZoomCacheWarmup(map as never, start, () => false);
    vi.advanceTimersByTime(time);
    expect(start).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
});
