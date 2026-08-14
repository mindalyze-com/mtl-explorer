import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';

export type TrackSelectionEvents = {
  'select-track': [value: number | string];
  'open-details': [value: number | string];
};

export type FilterMapInteractionEvents = TrackSelectionEvents & {
  'filter-style-changed': [];
  'start-geo-drawing': [paramDef: ParamDefinition];
  'clear-geo-shape': [paramDef: ParamDefinition];
};
