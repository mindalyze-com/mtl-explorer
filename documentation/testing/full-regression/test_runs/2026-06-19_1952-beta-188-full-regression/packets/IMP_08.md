# Packet: IMP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_08
- In scope: Verify statistics/import count increased by five unless a source split into multiple tracks.
- Out of scope: Detailed totals and charts covered by IMP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 and IMP_06.
- Required app/data state: Baseline captured at zero tracks; five public GPX files imported.
- Required browser context: none.

## Allowed Mutations

- Allowed: Read installed API/mapping evidence.
- Not allowed: Add/delete files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compared IMP_01 baseline count to IMP_06 imported source-file mapping and API counts. | Track count increases by five unless a source file legitimately split into multiple tracks; if split, mapping is recorded. | Baseline track count was 0; after import, `/api/tracks/get` returned 5 tracks. Each of the five source GPX files maps to one successful imported track ID, so the expected count increase is exactly +5. | PASS | [assets/IMP_01-baseline-api.txt](../assets/IMP_01-baseline-api.txt); [assets/IMP_06-imported-track-mapping.txt](../assets/IMP_06-imported-track-mapping.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_01-baseline-api.txt](../assets/IMP_01-baseline-api.txt) | Baseline zero-track count. |
| [assets/IMP_06-imported-track-mapping.txt](../assets/IMP_06-imported-track-mapping.txt) | Post-import five-track mapping. |

## Screenshot Evidence

No additional screenshot required; screenshots are linked in IMP_05/IMP_06.

## Timings

| Step | Timing |
|---|---:|
| Count comparison | <1 min |

## Handoff Notes

- Completed: IMP_08.
- Remaining unfinished coverage: IMP_09 onward.
- Blocked or not applicable: none.
- State left for the next packet: five imported GPX tracks remain loaded.
