# Packet: MED_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_13
- In scope: Persisted activity-photo timeline order, positions, and origins across reload and restart.
- Out of scope: Photo correction and location mutations.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08 and MED_06.
- Required app/data state: Correlated six-photo activity 100016.
- Required browser context: Track Details Photos tab.

## Allowed Mutations

- Allowed: Browser reload and controlled app-container restart.
- Not allowed: Media or correlation mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_13 | Recorded the six-row baseline, reloaded, restarted the app container, reloaded again, and compared browser and read-only persistence state. | Same persisted rows, positions, origins, and capture-time order survive reload/restart. | Exact six-row order, distances, origin labels, six markers, selected correlations, and resolved origins survived both boundaries. | PASS | [assets/MED_13-persistence.txt](../assets/MED_13-persistence.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_13-persistence.txt](../assets/MED_13-persistence.txt) | Exact timeline order and reload/restart persistence result. |

## Screenshot Evidence

Unavailable under ACC_04. Exact accessible rows plus read-only persistence state provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Browser reload and Photos load | About 3.9 s |
| Controlled app restart to HTTP 200 | About 24 s |
| Post-restart reload and Photos load | About 4.3 s |

## Handoff Notes

- Completed: Persisted timeline reload/restart flow passed.
- Remaining unfinished coverage: None for MED_13.
- Blocked or not applicable: Screenshot evidence only, tracked by ACC_04.
- State left for the next packet: Activity 100016 Photos open; server healthy.
