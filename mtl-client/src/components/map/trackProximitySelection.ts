import {
  TrackProcessingStatusDtoStateEnum,
  type TrackProcessingStatusDto,
  type TrackProximityResponseDto,
} from 'x8ing-mtl-api-typescript-fetch';

export function needsTrackProcessingNotice(processing: TrackProcessingStatusDto): boolean {
  return processing.state !== TrackProcessingStatusDtoStateEnum.Ready || processing.failedGpsFiles > 0;
}

type SelectionActions = {
  deselect: () => void;
  showResults: (trackIds: number[], processing: TrackProcessingStatusDto) => void;
  showPoint: (trackId: number) => Promise<boolean>;
  select: (trackId: number) => void;
  openDetails: (trackId: number) => void;
};

export async function applyTrackProximityResponse(
  response: TrackProximityResponseDto,
  signal: AbortSignal,
  actions: SelectionActions
): Promise<void> {
  if (signal.aborted) return;
  const { trackIds, processing } = response;
  if (trackIds.length === 0) actions.deselect();
  if (needsTrackProcessingNotice(processing) || trackIds.length > 1) {
    actions.showResults(trackIds, processing);
    return;
  }
  if (trackIds.length === 1) {
    const trackId = trackIds[0];
    const pointShown = await actions.showPoint(trackId);
    if (signal.aborted) return;
    actions.select(trackId);
    if (!pointShown) actions.openDetails(trackId);
  }
}
