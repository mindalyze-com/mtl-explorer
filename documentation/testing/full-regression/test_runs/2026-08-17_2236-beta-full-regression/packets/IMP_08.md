# Packet: IMP_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_08
- In scope: Statistics count increase and source-to-track split accounting.
- Out of scope: Other statistics metrics.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 and IMP_06.
- Required app/data state: Import loaded after freshness action.
- Required browser context: Statistics Overview/Tracks.

## Allowed Mutations

- Allowed: Compare recorded counts and mappings.
- Not allowed: Infer count without per-source IDs.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compared the pre-import Statistics count with the post-reload count and audited source-to-track IDs. | Count increases by five unless a source legitimately splits, in which case the mapping explains it. | Statistics increased from 0 to 5. Five sources map one-to-one to unique IDs 100000-100004; no source split occurred. | PASS | [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt); [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt); [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Pre-import 0-track statistics baseline. |
| [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) | One-to-one source/ID mapping. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Post-import five-track Statistics count. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM count evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Count and mapping comparison | <1 s |

## Handoff Notes

- Completed: Exact +5 count and no-split mapping verified.
- Remaining unfinished coverage: None for IMP_08.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Five imported tracks remain included in statistics.
