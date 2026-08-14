export type MapThemeOption = {
  code: string;
  badgeLabel?: string;
  badgeTone?: 'preferred' | 'swiss';
  name: string;
  thumbnail: string;
};

export type MapSourceMode = 'auto' | 'remote';

export type MapLayerState = {
  enabled: boolean;
  opacity: number;
};

export type MapSettingsLayerStates = Record<string, MapLayerState>;

export type MapLayerSheetProps = {
  layerStates: MapSettingsLayerStates;
  modelValue: boolean;
};

export type MapLayerSheetEmit = {
  (event: 'update:modelValue', value: boolean): void;
  (event: 'toggle-layer', layer: string): void;
  (event: 'change-layer-opacity', layer: string, opacity: number): void;
};

export const DATA_LAYER_IDS = ['tracks', 'media', 'trackpoints', 'heatmap'] as const;

export const ROUTE_LAYER_IDS = [
  'wmt-hiking',
  'wmt-cycling',
  'wmt-mtb',
  'wanderland',
  'veloland',
  'mountainbikeland',
  'wanderwege',
] as const;
