# Source quality and review

Source diagnosis and canonical processing have different responsibilities. Diagnosis reads source evidence and records findings. Processing persists cleaning actions and supported geometry in the same import transaction. Review reuses evidence for the matching source revision.

Reassessment does not reimport or run a second cleaning algorithm. Missing or mismatched evidence remains unavailable. Raw observation inspection keeps original source order and timestamps.

User inclusion decisions control map and statistical use. They cannot turn estimated timing into measured duration, speed, energy, or a time-based Health/media match.

## Review thresholds

`GpsReviewThresholds` owns manual-review thresholds. `REVIEW` creates a pending
source issue; warnings and routine findings remain informational. Assess each
original source section, counting affected observations once. Coverage requires
both the stated percentage and minimum count.

| Evidence | Manual review threshold |
| --- | --- |
| Equal timestamps | Informational regardless of count; independently supported spatial anomalies are separate findings. |
| Backward clock | Any timestamp more than 1 second behind the latest earlier usable timestamp. This includes cumulative reversals and a reversal across missing timestamps. |
| Recording gaps | At least 12 hours; or a detected gap with at least 1 km endpoint displacement; or cumulative gap displacement of at least 1 km and 5% of supported recorded distance. Short-gap detection remains `max(5 min, 10 × median positive sampling interval)`. |
| Position or altitude smoothing | Any correction at least 200 m; or corrections at least 50 m at 1% of applicable observations and at least 10 points. |
| Removed position spikes | Jump at least 1 km; or rejection of 1% of source observations and at least 10 points. |
| Removed altitude spikes | Spike at least 300 m; or rejection of 1% of altitude observations and at least 10 points. |
| Weak source fix | At least 10% of position observations, or a supported contiguous weak period of at least 5 minutes. Horizontal and vertical findings at the same point count once. |
| Missing/invalid activity timestamps | At least 1% of source observations and 10 points, or all activity timestamps missing. Intentional untimed routes remain informational. |
| Invalid coordinates | At least 1% of source observations and 10 points, or no usable positions. |
| Empty/unreadable source | Zero observations, zero bytes, or whole-source read/conversion/parsing failure. Assessment resource limits alone remain informational. |

Retained abrupt position discontinuities keep their contextual detector: at
least 200 m and 50 m/s, exceeding surrounding supported speed by more than six
times. Edges require at least 125 m/s and stronger available-side support. An
unresolved detected discontinuity requires review. Missing optional fields and
fragment count alone do not create a manual review task. Generated timing and
inconsistent processing results retain their safeguards.

A backward-clock alert alone does not disable a section's timing. Whole-section
containment requires at least three independent backward transitions greater
than one second covering at least 1% of source observations, a backward lag of
at least 12 hours, or an unresolved large date excursion. Affected observations
below an earlier clock maximum are not counted as independent transitions.

An isolated forward clock spike requires review as one incident. Import excludes
only its derived timestamp when two neighbouring supported intervals establish
a consistent return: each interval is positive and at most 120 seconds; the
return spans at most 240 seconds and three times their maximum cadence; the
spike exceeds both neighbours by more than one second and ten times that
cadence. Missing context or smaller ambiguous peaks remain source evidence.
Original timestamps remain in RAW. Persisted containment evidence keeps timing
unsupported after reassessment until a successful reimport rebuilds it.
Unsupported local intervals still make aggregate motion partial; retaining
other source timestamps does not invent complete motion totals.

Aggregate changes in distance, ascent or descent after processing do not create
review findings. Routine denoising can substantially change those totals.
Individual correction magnitudes, rejection coverage and retained
discontinuities remain assessed. Reassessment ignores stored aggregate-impact
summaries, resolving their stored issues even when assessment is incomplete.
Other unchecked findings remain active. It classifies current cleaning actions against source observations;
it does not rerun the cleaner. New imports record the thresholds and processing
version. Existing motion totals and missing cleaning evidence require ordinary
reimport to rebuild.

Equal timestamps retain geometry within the same 200 m support bound as untimed
observations. Their instantaneous speed remains unknown. The motion analyzer
retains supported totals and uses a temporary monotonic timeline for clock
precision reversals of at most one second, without modifying stored observations.

Diagnostics is a named module; the repair module has been removed. The Spring Boot application remains the assembly. General multi-module extraction is a separate proposal.

User procedures are in [Source review](../../user/tracks/source-review.md); launch requirements are in [GPS quality modules](../../development/gps-quality-modules.md).

## Assessment limits and clock alerts

Assessment has limits of 64 MiB and one million observations. The streaming
parser retains observations and supported fields without building a document
tree for device extensions. Nesting, field length, and retained review data have
additional limits; unused extension elements do not consume the retained-data
budget. An incomplete assessment is reported without treating the file as
healthy or automatically excluding it. Original downloads are streamed without
this limit; converted GPX downloads are bounded by it. All diagnosis runs on the
server. Import also checks each source section for conflicting clocks when full
assessment cannot finish.

A backward-time alert keeps the section's supported timing. Systemic clock
problems disable timing for the affected section while retaining original
observations, timestamps, and geometry. A clearly isolated forward clock spike
excludes only that point's derived timestamp and appears as one incident;
original timestamps remain available in RAW. Recognized timing problems remain
visible after reassessment.

Abrupt retained relocations are checked against the surrounding pace. Endpoints
use a stricter check with support from the available side. Unsupported
connections become separate spans before smoothing; their connecting distance
and speed do not contribute to statistics. Sustained fast travel alone does not
trigger this check. Changes in total distance, ascent, or descent after smoothing
do not trigger review; large individual corrections and substantial point
rejection do.

Updating the server does not reassess existing findings. **Reassess current
file** applies current criteria, removes earlier aggregate-impact findings, and
reuses stored cleaning evidence only when its source revision matches. It
preserves existing timing containment; an ordinary reimport is required to
rebuild timing, motion totals, and missing processing evidence. Without import
evidence, cleaning remains **Not assessed**.

Review does not edit, replace, or back up source files. Backup files from earlier
versions remain on disk and can be managed outside the app.
