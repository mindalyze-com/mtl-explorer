# Packet: MED_25

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_25
- In scope: Production deletion of a correlated activity, queue survival, and fallback.
- Out of scope: Deliberate worker failure behavior.

## Prerequisites

- Required previous coverage IDs or run packets: MED_20 and MED_24.
- Required app/data state: Original activity 100016 plus correlated alternate 100022.
- Required browser context: Activity timeline before/after deletion.

## Allowed Mutations

- Allowed: Delete a disposable correlated activity through the normal watched-source production path.
- Not allowed: Direct database deletes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_25 | Removed alternate source; observed production deletion, subsequent correlation job, persistence fallback, and browser state. | Photos are requeued; queue survives track deletion; recalculation falls back to another activity, EXIF, or none. | GPXStoreService deleted 100022; 156 ms later the worker processed six media; correlations/resolved positions fell back to 100016 and UI ambiguity cleared. | PASS | [assets/MED_25-26-queue-audit.txt](../assets/MED_25-26-queue-audit.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_25-26-queue-audit.txt](../assets/MED_25-26-queue-audit.txt) | Ordered deletion/job sequence and exact fallback state. |

## Screenshot Evidence

Exact ordered service logs and persistence rows provide direct queue-survival evidence; browser timeline confirms the resulting user state.

## Timings

| Step | Timing |
|---|---:|
| Deletion to correlation completion | About 8.2 s |
| Track deletion log to correlation completion | 156 ms |

## Handoff Notes

- Completed: Production deletion fallback passed.
- Remaining unfinished coverage: None for MED_25.
- Blocked or not applicable: None.
- State left for the next packet: Alternate deleted; six media settled on original activity.
