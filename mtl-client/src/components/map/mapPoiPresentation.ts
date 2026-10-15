import type { MapTargetCandidate } from './mapInteractionDecision';
import { poiCategoryForKind } from './poiCategories';

export type MapPoiCandidate = Extract<MapTargetCandidate, { kind: 'poi' }>;

export interface MapPoiPresentation {
  typeLabel: string;
  icon: string;
  color: string;
  categoryLabel: string | null;
}

const FALLBACK_PLACE_LABEL = 'Place';
const FALLBACK_PLACE_ICON = 'bi bi-geo-alt';
const FALLBACK_PLACE_COLOR = 'var(--text-muted)';

export function mapPoiPresentation(candidate: MapPoiCandidate): MapPoiPresentation {
  const kind =
    (typeof candidate.properties.kind === 'string' ? candidate.properties.kind.trim() : '') ||
    candidate.detail?.trim() ||
    '';
  const normalizedKind = kind
    .toLowerCase()
    .replace(/\s*[;·]\s*/g, ';')
    .replace(/\s+/g, '_');
  const category = poiCategoryForKind(normalizedKind);
  const typeLabel =
    normalizedKind
      .split(';')
      .map((part) => part.replaceAll('_', ' ').trim())
      .filter(Boolean)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' · ') || FALLBACK_PLACE_LABEL;

  return {
    typeLabel,
    icon: category ? `bi bi-${category.icon}` : FALLBACK_PLACE_ICON,
    color: category?.color ?? FALLBACK_PLACE_COLOR,
    categoryLabel: category?.label && category.label !== typeLabel ? category.label : null,
  };
}
