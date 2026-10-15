import type { PointOfInterest } from '@/planner/types';
import { PLANNER_POI_ICON_IMAGE_PREFIX } from '@/planner/constants/PlannerConstants';

export type PlannerPoiIconType = NonNullable<PointOfInterest['iconType']>;
export const DEFAULT_PLANNER_POI_ICON_TYPE: PlannerPoiIconType = 'pin';

/** Stable API keys mapped to the existing Bootstrap icon set. */
const ICONS: Record<PlannerPoiIconType, { label: string; icon: string }> = {
  pin: { label: 'Pin', icon: 'geo-alt-fill' },
  viewpoint: { label: 'Viewpoint', icon: 'binoculars-fill' },
  food: { label: 'Food and drink', icon: 'cup-hot-fill' },
  water: { label: 'Water', icon: 'droplet-fill' },
  accommodation: { label: 'Accommodation', icon: 'house-door-fill' },
  camping: { label: 'Camping', icon: 'fire' },
  parking: { label: 'Parking', icon: 'p-circle-fill' },
  information: { label: 'Information', icon: 'info-circle-fill' },
  photo: { label: 'Photo', icon: 'camera-fill' },
  peak: { label: 'Peak', icon: 'triangle-fill' },
};

export function poiIconOption(type?: string | null) {
  const value = type && Object.hasOwn(ICONS, type) ? (type as PlannerPoiIconType) : DEFAULT_PLANNER_POI_ICON_TYPE;
  return { value, ...ICONS[value] };
}

export const PLANNER_POI_ICON_OPTIONS = (Object.keys(ICONS) as PlannerPoiIconType[]).map(poiIconOption);

export function poiIconImageId(type?: string | null): string {
  return `${PLANNER_POI_ICON_IMAGE_PREFIX}${poiIconOption(type).value}`;
}
