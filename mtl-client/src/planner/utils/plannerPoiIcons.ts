import type { Map as MapLibreMap } from 'maplibre-gl';
import { createPoiIconImage } from '@/components/map/poiImages';
import { POI_ICON_PIXEL_RATIO } from '@/components/map/poiLayers';
import { PLANNER_POI_COLOR } from '@/planner/constants/PlannerConstants';
import { PLANNER_POI_ICON_OPTIONS, poiIconImageId } from './plannerPoiIconTypes';

/** Install the same bundled vector symbols used by the editor and POI list. */
export async function installPlannerPoiIcons(map: MapLibreMap, isCurrent: () => boolean): Promise<boolean> {
  const images = await Promise.all(
    PLANNER_POI_ICON_OPTIONS.map(async (option) => ({
      id: poiIconImageId(option.value),
      image: await createPoiIconImage(option.icon, PLANNER_POI_COLOR),
    }))
  );
  if (!isCurrent()) return false;
  for (const { id, image } of images) {
    if (!map.hasImage(id)) map.addImage(id, image, { pixelRatio: POI_ICON_PIXEL_RATIO });
  }
  return true;
}
