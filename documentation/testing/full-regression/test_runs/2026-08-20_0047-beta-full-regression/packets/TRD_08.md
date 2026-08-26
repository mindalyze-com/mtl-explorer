# Packet: TRD_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_08
- In scope: Original-source download and byte-for-byte comparison.
- Out of scope: Converted GPX download.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_04 and TRD_01.
- Required app/data state: Official Activity.fit retained as track 100005.
- Required browser context: Same-run visible FIT Overview download.

## Allowed Mutations

- Allowed: Reuse the direct same-run FIT_04 download and checksum evidence.
- Not allowed: Substitute or modify the downloaded payload.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_08 | Evaluate the same-run visible Download original action for track 100005 against the uploaded FIT checksum. | Source-format payload downloads and matches the upload. | The 94,096-byte FIT payload has the `.FIT` signature and exactly matches the uploaded SHA-256. | PASS | [assets/TRD_08-original-download.txt](../assets/TRD_08-original-download.txt); [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_08-original-download.txt](../assets/TRD_08-original-download.txt) | TRD acceptance mapping and checksum summary. |
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Direct visible action and complete payload validation. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the downloaded payload was validated directly.

## Timings

| Step | Timing |
|---|---:|
| Evidence mapping from completed same-run action | <1 min |

## Handoff Notes

- Completed: Original FIT download is byte-identical to the upload.
- Remaining unfinished coverage: None for TRD_08.
- Blocked or not applicable: None.
- State left for the next packet: Original and converted-download evidence retained read-only.
