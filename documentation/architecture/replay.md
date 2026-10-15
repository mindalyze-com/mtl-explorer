# Replay architecture

Single-track replay requires loaded route geometry. Recording gaps do not block replay; positions between available points are interpolated. Supported canonical timing drives playback when available, with distance-based playback for missing, unreliable or estimated timing. Distance-based playback does not derive an activity duration from unsupported timing. `trackReplayController.ts` owns the playback clock; `trackReplayPath.ts` supplies sampled positions.

`replayCameraRailPlanner.ts` prepares a duration-aware camera rail. `trackReplayCamera.ts` samples it, while viewport and screen guards keep the route subject visible around UI occlusion. Playback consumes that plan instead of rebuilding analytical metrics in the browser.

Segment replay uses server-resolved exact attempt geometry and timing. Comparison and replay share attempt identities, labels, colors, and supported inspected positions. Recording breaks and unsupported timing remain unavailable.

Archive animation is a separate date-based display of activities. It does not replay point timing along a single track.

See [single-track source](../../mtl-client/src/components/replay), [segment replay](../../mtl-client/src/components/measure/SegmentReplay.vue), and [the server contract](analysis/segments.md).
