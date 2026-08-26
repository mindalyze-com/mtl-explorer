# Packet: LOC_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_04
- In scope: Zero, very large, negative-gain, and null-elevation boundary rendering.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_03 plus IMP_01, PLN_05, MCT_04, and MED_28 boundary evidence.
- Required app/data state: Track 100039 available; de-DE/Metric selected.
- Required browser context: Track Details and same-run prior surfaces.

## Allowed Mutations

- Allowed: Temporarily replace three numeric fields on track 100039 with exact backed-up restoration; increment row version normally.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_04 | Consolidated zero/large/missing cases, injected exact negative/null boundaries into one real track, inspected details, then restored and reloaded. | Boundary values are sensible and never NaN/blank. | Zero/large/missing states stayed explicit; negative ascent and null-altitude fields rendered bounded numeric values with no NaN/blank/error; exact values were restored and 8 Tracks returned. | PASS | [assets/LOC_04-boundaries.txt](../assets/LOC_04-boundaries.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_04-boundaries.txt](../assets/LOC_04-boundaries.txt) | Existing and controlled boundary matrix plus restoration. |
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Zero statistics baseline. |
| [assets/PLN_05-live-stats.txt](../assets/PLN_05-live-stats.txt) | Zero/unavailable planner states. |
| [assets/MCT_04-comparison.txt](../assets/MCT_04-comparison.txt) | Missing sensor-value handling. |
| [assets/MED_28-paging.txt](../assets/MED_28-paging.txt) | Very-large count/page formatting. |

## Screenshot Evidence

Direct formatted-text evidence is durable; ACC_04 prevents screenshots.

## Timings

| Step | Timing |
|---|---:|
| Boundary detail load | About 0.5 s |
| Restoration reload/cache settlement | About 2.7 s |

## Handoff Notes

- Completed: Zero, large, negative, null/missing, no-NaN checks and exact value restoration.
- Remaining unfinished coverage: None for LOC_04.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Track 100039 restored; Track Details open; 8 Tracks; de-DE/Metric.
