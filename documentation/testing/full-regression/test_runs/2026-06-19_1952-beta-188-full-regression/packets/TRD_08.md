# Packet: TRD_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_08
- In scope: Verify Download original source file returns the original GPX/FIT/etc. bytes.
- Out of scope: GPX export conversion, covered by TRD_09.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_04 and FMT_02.
- Required app/data state: FIT and non-GPX format tracks indexed.
- Required browser context: authenticated app/API session.

## Allowed Mutations

- Allowed: Reuse completed download verification evidence from this run.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_08 | Reused FIT_04 and FMT_02 download-original actions. | Download original source file returns the uploaded/source file bytes for FIT/other formats. | FIT original download returned `Activity.fit` with matching SHA-256; FMT_02 verified original downloads for seven non-GPX formats matched source checksums. | PASS | [assets/FIT_04-source-download.txt](../assets/FIT_04-source-download.txt); [assets/DAT_05-fit-file.txt](../assets/DAT_05-fit-file.txt); [assets/FMT_02-detail-download-summary.txt](../assets/FMT_02-detail-download-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_04-source-download.txt](../assets/FIT_04-source-download.txt) | FIT original download response and checksum comparison. |
| [assets/DAT_05-fit-file.txt](../assets/DAT_05-fit-file.txt) | Uploaded FIT source checksum. |
| [assets/FMT_02-detail-download-summary.txt](../assets/FMT_02-detail-download-summary.txt) | Original download checksum results for seven non-GPX formats. |

## Screenshot Evidence

No screenshot required; byte/checksum evidence is recorded in text assets.

## Timings

| Step | Timing |
|---|---:|
| Original source download verification | Covered in FIT_04 and FMT_02 |

## Handoff Notes

- Completed: TRD_08.
- Remaining unfinished coverage: TRD_09 onward.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
