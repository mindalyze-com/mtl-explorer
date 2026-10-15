import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { observeElementResize } from '@/utils/observeElementResize';

let deliver: ResizeObserverCallback;
const disconnect = vi.fn();
const observer = { disconnect } as unknown as ResizeObserver;
const entry = (width: number) => ({ contentRect: { width } }) as ResizeObserverEntry;

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal(
    'ResizeObserver',
    class {
      constructor(callback: ResizeObserverCallback) {
        deliver = callback;
      }
      observe() {}
      disconnect = disconnect;
    }
  );
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('element resize delivery', () => {
  it('defers layout writes and uses the latest size during rapid resizing', () => {
    const resize = vi.fn();
    const stop = observeElementResize(document.createElement('div'), resize);
    deliver([entry(900)], observer);
    deliver([entry(390)], observer);
    expect(resize).not.toHaveBeenCalled();
    vi.advanceTimersToNextFrame();
    expect(resize).toHaveBeenCalledExactlyOnceWith(entry(390));
    stop();
  });

  it('cancels pending chart work when its element is removed', () => {
    const resize = vi.fn();
    const stop = observeElementResize(document.createElement('div'), resize);
    deliver([entry(390)], observer);
    stop();
    vi.advanceTimersToNextFrame();
    expect(resize).not.toHaveBeenCalled();
    expect(disconnect).toHaveBeenCalledOnce();
  });
});
