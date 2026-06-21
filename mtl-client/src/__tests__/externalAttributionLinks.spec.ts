import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { configureExternalAttributionLinks } from '@/utils/externalAttributionLinks';

describe('configureExternalAttributionLinks', () => {
  const originalOpen = window.open;

  beforeEach(() => {
    document.body.innerHTML = '';
  });

  afterEach(() => {
    window.open = originalOpen;
    vi.restoreAllMocks();
  });

  it('marks MapLibre attribution links as external browser links', () => {
    const container = document.createElement('div');
    container.innerHTML =
      '<div class="maplibregl-ctrl-attrib"><a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a></div>';

    const cleanup = configureExternalAttributionLinks(container);
    const link = container.querySelector<HTMLAnchorElement>('a');

    expect(link?.target).toBe('_blank');
    expect(link?.rel).toBe('noopener noreferrer');

    cleanup();
  });

  it('opens clicked attribution links in a separate browser surface', () => {
    const container = document.createElement('div');
    container.innerHTML =
      '<div class="maplibregl-ctrl-attrib"><a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a></div>';
    const openExternal = vi.fn(() => true);
    configureExternalAttributionLinks(container, { openExternal });

    const link = container.querySelector<HTMLAnchorElement>('a')!;
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    link.dispatchEvent(event);

    expect(openExternal).toHaveBeenCalledWith('https://www.openstreetmap.org/copyright');
    expect(event.defaultPrevented).toBe(true);
  });

  it('notifies when the platform blocks the external browser surface', () => {
    const container = document.createElement('div');
    container.innerHTML =
      '<div class="maplibregl-ctrl-attrib"><a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a></div>';
    const onBlocked = vi.fn();
    configureExternalAttributionLinks(container, {
      openExternal: () => false,
      onBlocked,
    });

    container.querySelector<HTMLAnchorElement>('a')!.click();

    expect(onBlocked).toHaveBeenCalledWith('https://www.openstreetmap.org/copyright');
  });
});
