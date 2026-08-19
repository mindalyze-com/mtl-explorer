export type MediaPositionMarkerKind = 'gps' | 'estimated' | 'manual' | 'unknown';

export type MediaPositionMarkerStyle = {
  kind: MediaPositionMarkerKind;
  fill: string;
  foreground: string;
  border: string;
};

export const MEDIA_POSITION_MARKER_STYLES: Record<MediaPositionMarkerKind, MediaPositionMarkerStyle> = {
  gps: {
    kind: 'gps',
    fill: '#2563eb',
    foreground: '#ffffff',
    border: '#ffffff',
  },
  estimated: {
    kind: 'estimated',
    fill: '#fdba74',
    foreground: '#431407',
    border: '#fff7ed',
  },
  manual: {
    kind: 'manual',
    fill: '#7c3aed',
    foreground: '#ffffff',
    border: '#ede9fe',
  },
  unknown: {
    kind: 'unknown',
    fill: '#d1d5db',
    foreground: '#1f2937',
    border: '#f9fafb',
  },
};

export function resolveMediaPositionMarkerStyle(
  positionOrigin: string | null | undefined,
  positionEstimated = false
): MediaPositionMarkerStyle {
  if (positionOrigin === 'USER_ASSIGNED') return MEDIA_POSITION_MARKER_STYLES.manual;
  if (positionOrigin === 'TRACK_INTERPOLATED' || positionEstimated) return MEDIA_POSITION_MARKER_STYLES.estimated;
  if (positionOrigin === 'EXIF_EMBEDDED') return MEDIA_POSITION_MARKER_STYLES.gps;
  return MEDIA_POSITION_MARKER_STYLES.unknown;
}
