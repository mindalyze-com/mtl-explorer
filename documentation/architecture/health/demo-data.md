# Google Health demo generator

Technical notes for the synthetic health data used by MTL Explorer demos.
Account connection and live Google synchronization are unavailable in demo mode.

The generator requires both `mtl.demo-mode=true` and `mtl.demo-health.enabled=true`.
Demo deployment templates enable it for every image channel; ordinary
installations leave it disabled.
On startup it waits for indexing and track calculations, then asynchronously
checks the ordinary Health tables and fills missing records. **Settings →
Processing** shows its progress. INFO logs show startup, waiting, missing-day
counts, generation batches and completion. Failed batches are retried.

Tracks and the daily overview share one fictional wearable timeline. Overlapping
tracks use the same samples; the strongest effort at each timestamp supplies
the workload without counting overlapping activity twice. Heart rate follows
speed, available elevation and cycling power, with delayed effort response,
recovery, daily variation and small correlated fluctuations. Missing elevation
means flat terrain. Recording gaps
are not filled. Motorized tracks generate modest heart rates and no exercise
steps or active calories.

Three mathematical walking, running and cycling GPX routes provide exercise
examples. Daily records include steps, distance, active calories, sleep stages,
resting heart rate and weight. Daily rollups cover the demo track history and
at least the recent period; detailed daily heart rate is generated for the last
90 days by default, with older samples generated along tracks. Samples and raw
records identify their synthetic source. No account, tokens or Google
synchronization are simulated.

## Configuration

| `mtl.demo-health` setting | Default | Purpose |
| --- | --- | --- |
| `enabled` | `false` | Explicit demo-only opt-in |
| `track-percentage` | `100` | Stable selection from 0–100%; approximate below 100% |
| `seed` | `20261003` | Reproducible model and selection |
| `sample-interval-seconds` | `15` | Shared timeline sample spacing, 5–60 seconds |
| `batch-size` | `25` | Maximum days per pass and track IDs per query |
| `recent-days` | `90` | Minimum daily history and detailed sample generation |
| `run-schedule` | `PT1S` | Delay between active passes |

The percentage selects tracks that influence the fictional profile. Shared
samples can also match unselected tracks when their timestamps fall within
the detailed daily history or overlap a selected track.

## Implementation

The Java code lives in `googlehealth.demo`. It uses the regular GPS and Health
tables and the same typed JSON payloads as imported records, so shared Health
queries can read generated metrics. It adds no schema, tables or persisted job checkpoints. Existing Health
records determine which days need completion; a daily heart-rate summary includes
the sample count so missing samples can be detected. Completeness checks group
stored sample counts by day once across the requested period. Workloads are combined in
memory, with cleaned points loaded one track at a time. Completed days are left
alone, and the startup job stops when all required records exist.

Generated metric payloads use the shared `HealthMetricPayload` Java records and
the same per-data-type JSON envelope as imported measurements. The metric reader
uses these records too. The demo's missing-sample check reads the nested
`heartRate.sampleCount` field.

Health queries and calculations have no generator dependency. Screen labels use
the general demo status. Configuration changes affect newly filled records;
recreate the demo database to regenerate a complete dataset with new settings.

## Screenshots

See the [capture notes](../../../docs/assets/feature-showcase/google-health-sources.md)
for data sources and image preparation.
