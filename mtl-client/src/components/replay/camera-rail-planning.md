# Track Replay Camera Rail Planning

This document describes the planned direction for the MTL Explorer track-detail
3D replay camera. It is intentionally implementation-oriented: the goal is to
capture the root issue, the design decision we agreed on, and a practical
handover plan for building the next camera system.

## Root Issue

The current 3D replay camera is too shaky because it is mostly a reactive
follower of the route. Each animation frame samples the current replay progress,
derives a target camera from the nearby track geometry, interpolates from the
previous camera frame, and immediately applies the result to the map.

That reduces some jitter, but it does not solve the core problem: the camera is
still discovering the route frame by frame instead of following a planned,
smooth camera motion.

The root issue is not only GPS noise. The replay camera currently couples the
camera too tightly to local track direction and local route position. A GPS
track can be the subject of the replay, but it should not directly define the
camera motion. The playhead may follow the track precisely; the camera should
follow a planned cinematic interpretation of the track.

## Current Implementation Summary

The current replay camera is split across these files:

- `trackReplayController.ts`: owns the playback clock and maps wall-clock time
  to normalized replay progress.
- `trackReplayPath.ts`: builds a distance-resampled replay path from track
  coordinates and samples positions by progress.
- `trackReplayCamera.ts`: builds the current camera frame from the replay path,
  camera preset, lookahead distance, and previous camera frame.
- `useMainMapController.js`: starts/stops replay, updates replay layer and
  marker, asks `buildReplayCameraFrame()` for the camera frame, then applies it
  with `jumpTo`.

The current camera presets are `follow`, `chase`, and `overview`. They differ by
pitch, zoom, fixed lookahead distance, and interpolation factor. They are not
separate camera planning algorithms.

Important current behavior:

- The replay route is sampled by distance, not by a camera plan.
- Camera smoothing is a per-frame low-pass interpolation from previous camera
  frame to current target.
- Lookahead is fixed in meters per preset.
- Replay duration controls how fast progress advances, but it does not redesign
  the camera route.
- A 15 second replay and a 90 second replay use the same lookahead and camera
  smoothing model.
- Camera center, bearing, pitch, and zoom are smoothed independently.

## Why Reactive Smoothing Is Not Enough

Reactive smoothing can damp movement after the target has changed, but it cannot
make global decisions:

- It cannot prepare the camera before a sharp turn.
- It cannot know whether a local bearing change is meaningful route structure or
  insignificant GPS wiggle.
- It cannot allocate the available camera motion budget across the full replay.
- It cannot decide that a short 15 second replay should become more overview-
  like while a 90 second replay can stay closer to the route.
- It cannot enforce proper limits for camera pan speed, rotation speed,
  acceleration, jerk, zoom rate, or pitch rate over the whole replay.

In video and animation tools, camera motion works because the motion curve is
planned and shaped. If the curve is bad, the result is bad even if every
individual keyframe is technically valid. The same principle applies here.

## Design Decision

Introduce a dedicated camera rail planner for track replay.

The camera planner should generate a complete camera rail before playback starts
or whenever a replay setting changes. Runtime playback should only sample this
precomputed rail.

The model should become:

1. The GPS route is the subject.
2. The playhead follows the route accurately.
3. The camera target is derived from the route using duration-aware lookahead and
   route interpretation.
4. The final camera rail is smoothed as a camera motion curve.
5. Playback samples the planned rail by replay time.

This changes the problem from:

```text
Every frame, where should the camera be based on the current GPS point?
```

to:

```text
Given this whole route and this replay duration, what is a beautiful, stable
camera move?
```

## Time Is The Primary Camera Domain

Meters are still required for stable geometry calculations, distance, curvature,
and local coordinate conversion. However, the viewer experiences camera motion in
time, not in meters.

The camera planner should reason primarily in replay seconds:

- How fast does the camera pan?
- How fast does it rotate?
- How quickly does zoom change?
- How abruptly does pitch change?
- Is the next route section understandable before the camera arrives there?
- Is the playhead readable while the camera moves?

Fixed meter lookahead is only a helper and should not be the main camera model.
A 20 meter lookahead means very different things in a 15 second replay and a 90
second replay. The new planner should use time-aware and duration-aware
lookahead.

Example:

- For a 15 second replay, the camera should be more overview-like, rotate less,
  ignore minor wiggles, and preserve broad route readability.
- For a 90 second replay, the camera can stay closer, react more to local route
  shape, and show more track detail.

## Proposed Package Structure

Add dedicated planning code under this package:

```text
mtl-client/src/components/replay/
  trackReplayController.ts
  trackReplayPath.ts
  trackReplayCamera.ts
  replayCameraRailPlanner.ts          // new
  replayCameraRailTypes.ts            // optional
  cameraRailSmoothers.ts              // optional
  cameraRailMath.ts                   // optional
  camera-rail-planning.md             // this document
```

Prefer a dedicated planner name that does not conflict with MTL Explorer's route
planner feature. Recommended name:

```text
ReplayCameraRailPlanner
```

Avoid naming it only `Planner`.

## Proposed Public API

The runtime code should move toward this shape:

```ts
const rail = ReplayCameraRailPlanner.build({
  path,
  durationSeconds,
  preset,
  smoothness,
  viewport,
});

const camera = rail.sample(elapsedSeconds);
```

Then `useMainMapController.js` would no longer ask
`buildReplayCameraFrame(path, progress, preset, previous, options)` every frame.
Instead it would keep a `ReplayCameraRail` object and sample by elapsed replay
time or progress.

Suggested interfaces:

```ts
export type ReplayCameraPlanOptions = {
  path: ReplayPath;
  durationSeconds: number;
  preset: ReplayCameraPresetId;
  smoothness: number;
  viewport?: {
    width: number;
    height: number;
  };
};

export type ReplayCameraFrame = {
  center: [number, number];
  bearing: number;
  pitch: number;
  zoom: number;
};

export type ReplayCameraRail = {
  durationSeconds: number;
  sample(elapsedSeconds: number): ReplayCameraFrame | null;
};
```

The exact interface may evolve, but it should preserve this separation:

- build once from whole route plus settings
- sample cheaply during playback
- avoid frame-to-frame reactive state inside the rail

## Coordinate Model

The planner should convert route coordinates to a local metric coordinate system
before doing camera planning.

Do not fit splines directly in longitude/latitude degrees. The same degree delta
means different physical distances depending on latitude, and angle/speed
calculations become harder to reason about.

Recommended model:

1. Pick a local origin near the route center or first point.
2. Convert lng/lat/elevation to local meters.
3. Build camera target points in local meters.
4. Smooth and sample in local meters.
5. Convert final camera center back to lng/lat for MapLibre.

The existing replay path can still remain the route/playhead source. The new
planner can wrap or derive from `ReplayPath`.

## Camera Rail Concepts

Separate these concepts explicitly:

### Route Path

The route path is the track geometry used for the replay subject and playhead.
It should remain accurate enough to represent the activity.

### Camera Target Path

The camera target path is a softened interpretation of the route. It may ignore
small wiggles, average direction over a future time window, and zoom out around
dense curves or switchbacks.

The camera target is not necessarily the playhead point.

### Camera Rail

The camera rail is the final smooth camera motion sampled during playback. It is
allowed to lag, anticipate, average, zoom out, or simplify if that produces a
more stable and readable replay.

## Target Generation

The planner should first generate raw camera targets over replay time.

Possible target sample density:

- Minimum every 0.25 to 0.5 seconds.
- Additional samples around high-curvature sections.
- Additional samples near start/end for controlled entrance and exit.
- Avoid generating targets directly for every GPS point.

For each target time `t`:

1. Convert `t / durationSeconds` to route progress.
2. Sample the route position for the playhead.
3. Analyze a future route window, preferably in time-equivalent distance.
4. Compute a stable route direction from that window.
5. Compute desired camera center, bearing, pitch, and zoom.

The future window should be duration-aware. For short replays, it should cover a
larger fraction of the route and ignore local detail. For long replays, it can be
more local.

## Duration-Aware Behavior

Replay duration should influence the camera plan directly.

Suggested behavior:

### 15 Seconds

- Use broader route framing.
- Zoom out more.
- Reduce number of meaningful bearing changes.
- Smooth aggressively.
- Prefer stable heading over exact local following.
- Increase target window size.
- Treat switchbacks as a region to frame, not turns to chase.

### 30 To 45 Seconds

- Balanced cinematic mode.
- Follow route direction but damp small wiggles.
- Moderate zoom.
- Moderate anticipation before curves.

### 60 To 90 Seconds

- Allow closer follow/chase behavior.
- Preserve more local route detail.
- Smaller target window.
- More responsive bearing, still with speed and jerk limits.

This means a preset is not just a fixed set of constants. It is a style that is
resolved together with replay duration.

## Camera Presets As Styles

The existing `follow`, `chase`, and `overview` presets should become styles for
the same rail-planning engine.

Suggested interpretations:

### Follow

Calm route-following camera. Keeps the playhead readable and follows general
route direction without chasing every wiggle.

### Chase

Closer and more dynamic. Allows more bearing change and slightly higher pitch,
but still uses rail smoothing and motion limits.

### Overview

High-level route presentation. Uses larger lookahead/windowing, lower rotation
rate, and more zoomed-out framing. Especially appropriate for short replays.

## Smoothing Strategy

The planner should support pluggable smoothing strategies internally, but the
first user-facing UI should probably remain simple. The current "Smooth" slider
can tune the chosen strategy without exposing algorithm names.

Recommended internal strategy interface:

```ts
export type CameraRailSmoother = {
  smooth(targets: CameraTargetKeyframe[], options: CameraSmoothingOptions): CameraCurve;
};
```

Suggested implementations:

### DebugNoSmoothingSmoother

Keeps target frames unchanged. Useful for tests and comparisons.

### BSplineCameraSmoother

Primary candidate. Cubic B-splines are a good practical fit for camera rail
planning because they produce smooth continuous curves from keyframes/control
points. Large creative tools expose similar ideas as keyframes, handles, motion
paths, and smooth curves. They may use Bezier, B-spline, NURBS-like, or related
curve models internally.

Use the spline on camera targets, not raw GPS points.

### MinimumJerkCameraSmoother

Strong candidate for later refinement. Minimum-jerk motion is well aligned with
camera work because it penalizes abrupt changes in acceleration. It may be more
complex than a first B-spline implementation, but it is a good target for "make
camera movement feel professional".

### CriticallyDampedCameraSmoother

Useful for interactive changes such as seeking, switching presets, or recentering
while playback is active. It should not be the main preplanned rail algorithm.

## B-Spline Notes

The first robust version should likely use cubic B-splines or Catmull-Rom style
interpolation converted to a controlled spline representation.

Important rules:

- Fit the spline to camera target keyframes, not every route point.
- Work in local meters.
- Treat bearing as circular data; do not linearly interpolate across 0/360.
- Smooth zoom and pitch as separate scalar curves.
- Keep endpoints controlled so the replay starts and ends predictably.
- Use duration and smoothness to change control-point density and tension.

For bearing/yaw:

- Unwrap bearing values before smoothing.
- Smooth in continuous degrees/radians.
- Re-wrap to 0..360 only when producing the final camera frame.

For camera center:

- Smooth x/y in local meters.
- Avoid a spline that overshoots too far outside the route context.
- Clamp or damp overshoot near tight switchbacks if it causes the subject to
  leave view.

## Motion Constraints

After building an initial rail, the planner should enforce or at least measure
camera motion constraints.

Useful constraints:

- Maximum pan speed in meters per second of replay time.
- Maximum pan acceleration.
- Maximum bearing/yaw speed in degrees per second.
- Maximum bearing/yaw acceleration.
- Maximum zoom rate.
- Maximum pitch rate.
- Maximum jerk where practical.

The first implementation can measure these and adjust smoothing/zoom/windowing
rather than solving a full optimization problem. The important architectural
point is that these constraints are evaluated against the whole rail, not only
against one frame.

## Route Analysis

The planner should derive route features before generating camera targets:

- Total distance.
- Replay speed in route meters per replay second.
- Curvature or heading-change density along the route.
- Dense switchback/zigzag sections.
- Long straight sections.
- Start and end direction.
- Elevation range if useful for pitch/zoom later.

Curvature can initially be approximate:

- Sample route every N meters.
- Compute heading deltas over a window.
- Store absolute heading change per meter or per route fraction.

High curvature should not automatically mean high camera rotation. Often it
means the opposite: zoom out and show the section calmly.

## Runtime Flow After Refactor

Expected runtime flow:

1. User starts 3D replay from track details.
2. Existing code fetches detail geometry and builds `ReplayPath`.
3. Build a `ReplayCameraRail` using the whole path, duration, preset, and
   smoothness.
4. Store the rail on the map controller, for example `_trackReplayCameraRail`.
5. Start `TrackReplayController`.
6. On each frame:
   - update progress
   - update replay layer
   - update playhead marker
   - sample camera rail by elapsed replay seconds
   - apply sampled camera frame
7. When duration, preset, or smoothness changes, rebuild the rail for the same
   route and current progress.

The controller can continue to own playback timing. The camera planner should
not own playback state.

## Interaction Behavior

Manual map interaction should continue to disable auto-follow as it does today.
When the user recenters replay camera, the controller should re-enable
auto-follow and sample the planned rail at the current elapsed time.

Seeking should not cause the camera to slowly recover from an old previous frame.
This is another advantage of a rail: seeking samples the correct planned camera
frame immediately.

Preset changes can either:

- rebuild the rail and jump to the sampled frame, or
- rebuild the rail and use a short critically damped transition into it.

The first implementation can jump, matching current behavior.

## Tests

Add focused tests near the existing replay tests.

Suggested files:

```text
mtl-client/src/__tests__/replayCameraRailPlanner.spec.ts
mtl-client/src/__tests__/cameraRailSmoothers.spec.ts
```

Useful test cases:

- Builds a rail for a simple straight route.
- Samples start, middle, and end camera frames.
- Keeps bearing stable on a straight route.
- Unwraps bearing across north without a 360 degree spin.
- Produces smoother bearing changes than raw target frames.
- Adapts camera behavior by duration.
- Uses more overview-like framing for 15s than 90s.
- Does not mutate the input `ReplayPath`.
- Returns null or a safe fallback for invalid/degenerate paths.
- Rebuilding a rail at the same options is deterministic.

Metrics-style tests:

- Maximum per-sample bearing delta is lower after smoothing.
- Maximum per-sample pan delta is lower after smoothing.
- Short replay has fewer significant bearing changes than long replay.

## Incremental Implementation Plan

### Phase 1: Introduce Rail Types And Planner Skeleton

- Add `replayCameraRailPlanner.ts`.
- Add basic `ReplayCameraRail` and options types.
- Build raw target keyframes using the current camera target logic as a baseline.
- Add tests for deterministic sampling.
- Keep current runtime behavior unchanged if needed.

### Phase 2: Local Meter Coordinate Conversion

- Add conversion helpers for route-local meters.
- Use local meters for target generation and smoothing.
- Add tests for conversion round trips.

### Phase 3: Time-Based Target Generation

- Generate camera target keyframes by replay time.
- Make target window/lookahead duration-aware.
- Add 15s/45s/90s behavior differences.
- Keep `follow`, `chase`, and `overview` as styles.

### Phase 4: First Spline Smoother

- Add a B-spline or Catmull-Rom based smoother for camera center.
- Add angle-aware smoothing for bearing.
- Add scalar smoothing for zoom and pitch.
- Add tests for bearing unwrap and reduced jitter.

### Phase 5: Runtime Integration

- Add `_trackReplayCameraRail` to `useMainMapController.js`.
- Build the rail when replay starts.
- Sample the rail in `onTrackReplayFrame`.
- Rebuild the rail when duration, preset, or smoothness changes.
- Keep manual interaction and recenter behavior intact.

### Phase 6: Constraint And Tuning Pass

- Measure pan speed, yaw speed, zoom speed, and pitch speed across the rail.
- Tune duration-specific limits.
- Add debug-only helpers if useful for comparing raw targets vs final rail.

### Phase 7: Visual Validation

- Test representative tracks:
  - straight/simple track
  - short city walk with GPS wiggles
  - mountain switchbacks
  - long cycling route
  - route crossing north bearing wrap
- Validate 15s, 45s, and 90s durations.
- Compare all three camera styles.

## Acceptance Criteria

The new camera planner should be considered successful when:

- A 15 second replay no longer tries to chase local route detail.
- A 90 second replay can still feel close and informative.
- Bearing changes are visually calm and do not twitch on GPS wiggles.
- Seeking samples a stable planned camera frame immediately.
- Changing duration changes the character of the camera plan.
- Manual map interaction still disables auto-follow.
- The implementation has focused unit tests for rail sampling and smoothing.
- The existing track replay layer/playhead can still follow the route accurately.

## Non-Goals

This plan does not require:

- Changing GPS import denoising.
- Changing route simplification on the server.
- Replacing `TrackReplayController`.
- Making a new public route planner feature.
- Exposing algorithm names in the UI.

The first implementation should focus on the camera rail architecture and
duration-aware smoothing. More advanced optimization can come later.
