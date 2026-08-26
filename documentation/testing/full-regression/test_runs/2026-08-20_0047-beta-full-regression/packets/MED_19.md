# Packet: MED_19

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_19
- In scope: Two eligible activities, Ambiguous UI, all stored candidates, deterministic selected winner after recalculation/restart, and cleanup.
- Out of scope: Activity add/replace/delete queue bounds, covered by MED_20.

## Prerequisites

- Required previous coverage IDs or run packets: MED_18 and the original track/media baseline.
- Required app/data state: Track 100013 and eight unambiguous selected media correlations.
- Required browser context: Track 100013 Media timeline.

## Allowed Mutations

- Allowed: Add/replace/remove one disposable fully synthetic overlapping GPX and restart the disposable app.
- Not allowed: Use private tracks or alter original media metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_19 | Index a unique overlapping activity, inspect UI/SQL, replace it to force recalculation, restart, compare winner, then remove it. | UI says Ambiguous; all candidates persist; the same deterministic winner remains after recalculation/restart. | Every media stored two candidates and showed Ambiguous (2). Track 100013 remained the selected winner with exact row values after alternate reimport and app restart. Removing the fixture restored the exact unambiguous baseline. | PASS | [assets/MED_19-ambiguity.txt](../assets/MED_19-ambiguity.txt); [assets/MED_19-overlap-track.gpx](../assets/MED_19-overlap-track.gpx) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_19-ambiguity.txt](../assets/MED_19-ambiguity.txt) | Candidate rows, UI equality, recalculation/restart result, and cleanup verification. |
| [assets/MED_19-overlap-track.gpx](../assets/MED_19-overlap-track.gpx) | Fully synthetic overlapping activity fixture. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact UI rows and all persisted candidate selections are linked above.

## Timings

| Step | Timing |
|---|---:|
| Fixture index and initial ambiguity | 4 min |
| Recalculation and exact comparison | 3 min |
| App restart and comparison | 2 min |
| Remove fixture and verify baseline | 3 min |

## Handoff Notes

- Completed: Ambiguous UI, all eligible correlations, deterministic winner across recalculation/restart, and cleanup.
- Remaining unfinished coverage: None for MED_19.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Alternate GPX is outside the watched folder; track 100020 is deleted; eight unambiguous selected correlations and baseline UI restored.
