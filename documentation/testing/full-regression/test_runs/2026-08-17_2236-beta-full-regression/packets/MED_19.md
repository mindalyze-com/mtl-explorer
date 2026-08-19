# Packet: MED_19

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_19
- In scope: Ambiguous UI, all eligible stored correlations, deterministic selection, recalculation, and restart.
- Out of scope: Add/replace/delete queue breadth, covered by MED_20.

## Prerequisites

- Required previous coverage IDs or run packets: MED_18.
- Required app/data state: A second distinct synthetic activity covering the six photo times.
- Required browser context: Activity 100016 Photos tab.

## Allowed Mutations

- Allowed: Add disposable synthetic activities and restart the app.
- Not allowed: Direct database writes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_19 | Added a distinct overlapping activity, observed repeated recalculation, inspected all alternatives, reloaded UI, restarted, and rechecked. | UI shows Ambiguous, stores all eligible correlations, and selects the same deterministic winner after recalculation/restart. | Six Ambiguous (2) badges/marker labels appeared; both alternatives persisted; selected winner 100016 remained stable through repeated passes and restart. | PASS | [assets/MED_19-ambiguity.txt](../assets/MED_19-ambiguity.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_19-ambiguity.txt](../assets/MED_19-ambiguity.txt) | Exact alternatives, UI state, and deterministic restart result. |
| [assets/MED_19-overlap-activity-b.gpx](../assets/MED_19-overlap-activity-b.gpx) | Fully synthetic distinct overlapping activity. |

## Screenshot Evidence

Live viewport capture was available; accessible timeline and mini-map labels provide exact Ambiguous (2) evidence.

## Timings

| Step | Timing |
|---|---:|
| Watcher ingest plus correlation | About 25 s |
| App restart to HTTP 200 | About 22 s |
| Post-restart UI verification | About 4 s |

## Handoff Notes

- Completed: Ambiguous matching and deterministic selection passed.
- Remaining unfinished coverage: None for MED_19.
- Blocked or not applicable: None.
- State left for the next packet: Original 100016 and alternate 100022 are eligible; duplicate 100021 is excluded.
