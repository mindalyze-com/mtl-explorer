# Packet: IMP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_08
- In scope: Verify statistics count increased by five unless a source file legitimately split into multiple tracks.
- Out of scope: Detailed statistic direction checks; covered by IMP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 through IMP_07.
- Required app/data state: five GPX tracks imported and mapped.
- Required browser context: none.

## Allowed Mutations

- Allowed: review existing baseline, post-import count, and mapping evidence.
- Not allowed: import/delete additional files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compared baseline count with post-import count and reviewed source-to-track mapping. | Statistics track count increases by five unless a source file legitimately splits into multiple tracks; source-to-track mapping is recorded. | PASS: baseline count was 0; post-import count is 5; all five GPX source files map to one displayed imported track each. The Lannion GPX contains two source `trk` names but appears as one displayed imported track. | PASS | [assets/IMP_08-count-mapping.txt](../assets/IMP_08-count-mapping.txt); [assets/DAT_03-imported-mapping.txt](../assets/DAT_03-imported-mapping.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_08-count-mapping.txt](../assets/IMP_08-count-mapping.txt) | Baseline and post-import count comparison plus split note. |
| [assets/DAT_03-imported-mapping.txt](../assets/DAT_03-imported-mapping.txt) | Source file to imported GPX track ID/name mapping. |

## Screenshot Evidence

No new screenshot required; see IMP_05 and IMP_06 screenshots for the visible count evidence.

## Timings

| Step | Timing |
|---|---:|
| Count and mapping audit | <1 minute |

## Handoff Notes

- Completed: IMP_08 is terminal.
- Remaining unfinished coverage: IMP_09 onward.
- Blocked or not applicable: none.
- State left for the next packet: five GPX tracks remain imported and counted.
