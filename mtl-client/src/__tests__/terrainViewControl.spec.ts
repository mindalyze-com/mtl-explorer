import { describe, expect, it, vi } from 'vitest';
import { TerrainViewControl } from '@/components/map/TerrainViewControl';

describe('TerrainViewControl', () => {
  it('exposes an accessible pressed state and delegates clicks to the terrain mode handler', () => {
    const onToggle = vi.fn();
    const control = new TerrainViewControl(onToggle);

    const container = control.onAdd();
    const button = container.querySelector('button');

    expect(button).not.toBeNull();
    expect(button?.getAttribute('aria-pressed')).toBe('false');
    expect(button?.getAttribute('title')).toBe('Enable 3D terrain');
    expect(button?.getAttribute('aria-label')).toBe('Enable 3D terrain');

    button?.click();
    expect(onToggle).toHaveBeenCalledTimes(1);

    control.setActive(true);
    expect(button?.getAttribute('aria-pressed')).toBe('true');
    expect(button?.getAttribute('title')).toBe('Disable 3D terrain');
    expect(button?.getAttribute('aria-label')).toBe('Disable 3D terrain');
    expect(button?.classList.contains('mtl-terrain-active')).toBe(true);

    control.setActive(false);
    expect(button?.getAttribute('aria-pressed')).toBe('false');
    expect(button?.getAttribute('title')).toBe('Enable 3D terrain');
    expect(button?.getAttribute('aria-label')).toBe('Enable 3D terrain');
    expect(button?.classList.contains('mtl-terrain-active')).toBe(false);
  });
});
