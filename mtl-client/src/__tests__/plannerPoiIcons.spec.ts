import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { createPoiIconImage } from '@/components/map/poiImages';
import { installPlannerPoiIcons } from '@/planner/utils/plannerPoiIcons';
import { PLANNER_POI_ICON_OPTIONS, poiIconImageId } from '@/planner/utils/plannerPoiIconTypes';

vi.mock('@/components/map/poiImages', () => ({
  createPoiIconImage: vi.fn(async () => ({ width: 44, height: 44, data: new Uint8ClampedArray(44 * 44 * 4) })),
}));
beforeEach(() => vi.clearAllMocks());

describe('Planner map icon installation', () => {
  it('registers every bundled icon, reuses existing images, and restores images after a style reset', async () => {
    const images = new Set<string>();
    const map = {
      hasImage: (id: string) => images.has(id),
      addImage: vi.fn((id: string) => images.add(id)),
    };
    await expect(installPlannerPoiIcons(map as unknown as MapLibreMap, () => true)).resolves.toBe(true);
    expect(images).toEqual(new Set(PLANNER_POI_ICON_OPTIONS.map((option) => poiIconImageId(option.value))));
    await installPlannerPoiIcons(map as unknown as MapLibreMap, () => true);
    expect(map.addImage).toHaveBeenCalledTimes(PLANNER_POI_ICON_OPTIONS.length);
    images.clear();
    await installPlannerPoiIcons(map as unknown as MapLibreMap, () => true);
    expect(map.addImage).toHaveBeenCalledTimes(PLANNER_POI_ICON_OPTIONS.length * 2);
    expect(createPoiIconImage).toHaveBeenCalledWith('geo-alt-fill', expect.any(String));
  });

  it('does not mutate a map that was detached while its icons were loading', async () => {
    const map = { hasImage: vi.fn(), addImage: vi.fn() };
    await expect(installPlannerPoiIcons(map as unknown as MapLibreMap, () => false)).resolves.toBe(false);
    expect(map.addImage).not.toHaveBeenCalled();
  });
});
