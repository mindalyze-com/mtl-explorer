# Packet: TRD_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_08.
- In scope: original-source download and byte-for-byte match.
- Out of scope: converted GPX download, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_07 and FIT_04.
- Required app/data state: FIT-backed #100005 retains the uploaded original.
- Required browser context: Track Details original-download action already exercised in this fresh run.

## Allowed Mutations

- Allowed: reuse the exact frozen-run download artifact and checksum evidence.
- Not allowed: create a redundant second local download.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_08 | Reconciled the previously executed #100005 Download original action with the uploaded public FIT source. | Original source downloads and matches the upload. | The 94,096-byte browser download was identified as FIT and its SHA-256 matched the uploaded Activity.fit exactly. | PASS | [original download](../assets/FIT_04-original-download.txt), [source validation](../assets/DAT_05-public-fit.txt), [detail action](../assets/FIT_02-detail.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Exact browser artifact type, byte size, and matching SHA-256 values. |
| [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) | Uploaded public FIT integrity baseline. |
| [assets/FIT_02-detail.webp](../assets/FIT_02-detail.webp) | User-facing detail action context. |

## Screenshot Evidence

The compact FIT detail screenshot is below 85 KB; checksum evidence proves the download result.

## Timings

| Step | Timing |
|---|---:|
| Original download and checksum | < 1 min |

## Handoff Notes

- Completed: TRD_08.
- Remaining unfinished coverage: TRD_09 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Overview remains open; exact original-download artifact retained only for cleanup.
