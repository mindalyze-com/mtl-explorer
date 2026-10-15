# Motion and break detection

MTL Explorer classifies the accepted denoised canonical observations once during import. Classification does not delete points, move coordinates, or rewrite elevation. Isolated-spike cleanup remains an earlier, separate operation.

Kalman smoothing preserves runs of at least three identical accepted XYZ positions within each continuous recording section. This prevents its velocity model from introducing movement into a recorded plateau. Missing altitude stays missing. Preserving these positions does not establish a stop by itself: the timing, recording continuity and stationary-support rules below still apply.

## Stored timeline

Each canonical point owns the interval `[previous timestamp, this timestamp)`. `motionState` is `MOVING`, `STOPPED`, or `UNKNOWN`; `motionReason` records the evidence or missing support. The existing `intervalStatus` separately describes geometry and recording continuity. Uncertain motion does not erase supported geometry.

The first point has no elapsed interval. Repeated timestamps supply no positive elapsed time and do not invalidate supported totals elsewhere. Precision reversals of at most one second use a temporary clamped clock for motion analysis, retaining stored observations and avoiding double-counted elapsed time. Missing or genuinely reversed clocks have `PARTIAL` or `UNAVAILABLE` motion status and null track-level duration totals. For a complete supported timeline, moving + stopped + unknown equals the recorded elapsed duration. `COMPLETE` means duration accounting is available; it can include unknown motion.

## Detection and retrospective boundaries

1. Reject motion inference across source recording breaks, invalid positions/times, or adjacent observations more than 60 seconds apart. Nearby or identical endpoints alone do not establish a pause.
2. Use horizontal displacement and, when available, vertical displacement over elapsed time. At least 0.5 km/h is movement evidence and cannot be absorbed into a pause. At most 0.3 km/h is potential stationary core evidence. Intermediate motion starts as unknown.
3. Require at least two supported positive-time intervals, hence three distinct recorded timestamps. A core must remain within 5 m of its time-weighted center, within a 10 m bounding diameter, and below 5 m net horizontal progress. Sample counts beyond this minimum do not determine event duration.
4. If very slow progress invalidates a larger candidate, examine its quieter portions (at most 0.05 km/h) for a compact core. This preserves a real pause after a slow approach without dividing a slow walk into artificial pauses. Unsupported slow progress stays unknown.
5. After confirming a core, revisit both boundaries. Nearby ambiguous intervals may be included within 5 m of the core and at most 60 seconds before/after it. The pass cannot cross observed movement or unsupported intervals. Only touching stationary ranges consolidate; movement or unresolved unknown time is never filled by proximity alone.
6. Store the original point indexes, an estimated start/end, confirmation time, and boundary brackets. Brackets include the ambiguous refinement span and adjacent observations. Track and recording edges are marked explicitly because the true boundary may lie outside the observed section. Brackets describe observation limits, not calibrated statistical confidence.

Supported short pauses contribute to stopped time. Only final ranges of at least 60 seconds become visible break events. Categories are `SHORT_STOP` from 60 seconds, `BREAK` from 180 seconds, and `LONG_BREAK` from 600 seconds. `trackDurationShortStoppedSecs` is the subset of stopped time in episodes below 60 seconds.

Break confirmation and estimated start are distinct. For example, observations showing a pause from 10 to 130 seconds can confirm a visible break at 70 seconds while retaining its estimated beginning at 10 seconds. This is offline retrospective boundary refinement; no live-stream latency is introduced.

## Consumers

- Track moving/stopped/unknown summaries and detected events use the same import analysis.
- Moving heart rate uses stored `MOVING` coverage with half-open boundaries, without another speed threshold or an event-based classification. Ordinary track and segment heart rate still include supported stationary time.
- Moving average power uses both the mechanical energy and elapsed time of the same moving intervals. Total mechanical energy continues to use supported geometry; motion uncertainty alone does not remove that geometry or its energy estimate.
- Segment stopped time clips the stationary intervals, including short pauses. Break count and longest visible break use detected events.
- Event replacement preserves user events. Detected events carry `OBSERVED_STATIONARY` evidence, version, boundary brackets, and confirmation time. Numeric `confidence` is null; a cluster inlier ratio is not a probability that a stop occurred.
- The overview explains short pauses and unknown motion. Break rows explain approximate boundaries and recording/track edges. Canonical revision hashes include motion state and analysis version.

## Limits and data lifecycle

The thresholds are conservative defaults, not calibrated probabilities. GPS cannot reveal unobserved movement between sparse fixes or determine a sub-sample physical stop time exactly. Residual jitter and very slow movement near the thresholds can remain ambiguous. This stage assumes denoising is already correct; it does not attempt to identify raw indoor GPS drift.

Schema change 083 adds nullable motion and boundary fields. It contains no historical backfill or migration-only service. New imports and ordinary reimports calculate `motion-v2`; existing derived records need reimport/recreation to receive the new timeline. This change does not deploy or reprocess existing servers.

[Motion tests](../../../mtl-server/src/test/java/com/x8ing/mtl/server/mtlserver/logic/motion/TrackMotionAnalyzerTest.java) cover supported pauses, movement, and unknown intervals. Dated outcomes remain internal verification records.
