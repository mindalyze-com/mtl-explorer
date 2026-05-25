export const TRACK_DETAILS_CHART_POINTS_MIN = 3;
export const TRACK_DETAILS_CHART_POINTS_MAX = 3000;
export const TRACK_DETAILS_CHART_POINTS_DEFAULT = 350;
export const TRACK_DETAILS_CHART_POINT_SLIDER_MIN = 0;
export const TRACK_DETAILS_CHART_POINT_SLIDER_MAX = 300;
export const TRACK_DETAILS_CHART_POINT_SLIDER_STEP = 1;

const TRACK_DETAILS_CHART_POINT_SLIDER_EASING = 5.0;
const TRACK_DETAILS_CHART_POINT_SLIDER_RANGE =
  TRACK_DETAILS_CHART_POINT_SLIDER_MAX - TRACK_DETAILS_CHART_POINT_SLIDER_MIN;
const TRACK_DETAILS_CHART_POINT_COUNT_RANGE = TRACK_DETAILS_CHART_POINTS_MAX - TRACK_DETAILS_CHART_POINTS_MIN;
const TRACK_DETAILS_CHART_POINT_EASING_RANGE = Math.exp(TRACK_DETAILS_CHART_POINT_SLIDER_EASING) - 1;

const TRACK_DETAILS_CHART_POINT_ROUNDING_STEPS = [
  { below: 30, step: 1 },
  { below: 100, step: 5 },
  { below: 250, step: 10 },
  { below: 500, step: 25 },
  { below: 1000, step: 50 },
  { below: 2000, step: 100 },
  { below: Number.POSITIVE_INFINITY, step: 250 },
] as const;

export function clampTrackDetailsChartPointCount(value: number): number {
  if (!Number.isFinite(value)) {
    return TRACK_DETAILS_CHART_POINTS_DEFAULT;
  }
  return Math.min(TRACK_DETAILS_CHART_POINTS_MAX, Math.max(TRACK_DETAILS_CHART_POINTS_MIN, Math.round(value)));
}

export function clampTrackDetailsChartPointSliderValue(value: number): number {
  if (!Number.isFinite(value)) {
    return trackDetailsChartPointCountToSliderValue(TRACK_DETAILS_CHART_POINTS_DEFAULT);
  }
  return Math.min(
    TRACK_DETAILS_CHART_POINT_SLIDER_MAX,
    Math.max(TRACK_DETAILS_CHART_POINT_SLIDER_MIN, Math.round(value))
  );
}

export function roundToNiceTrackDetailsChartPointCount(value: number): number {
  const clamped = clampTrackDetailsChartPointCount(value);
  const roundingStep = TRACK_DETAILS_CHART_POINT_ROUNDING_STEPS.find(({ below }) => clamped < below)?.step ?? 1;
  return clampTrackDetailsChartPointCount(Math.round(clamped / roundingStep) * roundingStep);
}

export function trackDetailsChartPointSliderValueToCount(value: number): number {
  const sliderValue = clampTrackDetailsChartPointSliderValue(value);
  const sliderRatio = (sliderValue - TRACK_DETAILS_CHART_POINT_SLIDER_MIN) / TRACK_DETAILS_CHART_POINT_SLIDER_RANGE;
  const easedRatio =
    (Math.exp(TRACK_DETAILS_CHART_POINT_SLIDER_EASING * sliderRatio) - 1) / TRACK_DETAILS_CHART_POINT_EASING_RANGE;
  const rawCount = TRACK_DETAILS_CHART_POINTS_MIN + easedRatio * TRACK_DETAILS_CHART_POINT_COUNT_RANGE;
  return roundToNiceTrackDetailsChartPointCount(rawCount);
}

export function trackDetailsChartPointCountToSliderValue(value: number): number {
  const count = roundToNiceTrackDetailsChartPointCount(value);
  const countRatio = (count - TRACK_DETAILS_CHART_POINTS_MIN) / TRACK_DETAILS_CHART_POINT_COUNT_RANGE;
  const sliderRatio =
    Math.log(1 + countRatio * TRACK_DETAILS_CHART_POINT_EASING_RANGE) / TRACK_DETAILS_CHART_POINT_SLIDER_EASING;
  return clampTrackDetailsChartPointSliderValue(
    TRACK_DETAILS_CHART_POINT_SLIDER_MIN + sliderRatio * TRACK_DETAILS_CHART_POINT_SLIDER_RANGE
  );
}
