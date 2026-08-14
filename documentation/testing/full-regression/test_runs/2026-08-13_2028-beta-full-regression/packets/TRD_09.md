# Packet: TRD_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_09.
- In scope: Track Details GPX export from a non-GPX source.
- Out of scope: original-source integrity.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_08 and FIT_05.
- Required app/data state: FIT-backed #100005 with completed conversion.
- Required browser context: Download GPX action already exercised in this fresh run.

## Allowed Mutations

- Allowed: reuse the exact browser GPX artifact and validation evidence.
- Not allowed: create a redundant second local download or edit the XML.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_09 | Reconciled the executed #100005 Download GPX action with XML validation and element counts. | A valid GPX downloads even though source is FIT. | The 479,844-byte output parsed as GPX 1.1 and contained 3,601 timestamped `trkpt` elements and zero waypoint-only fallback elements. | PASS | [GPX validation](../assets/FIT_05-gpx-download.txt), [detail action](../assets/FIT_02-detail.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Exact artifact, valid XML result, source hash, and GPX element counts. |
| [assets/FIT_02-detail.webp](../assets/FIT_02-detail.webp) | User-facing Download GPX context. |

## Screenshot Evidence

The compact detail screenshot is below 85 KB; parsed output evidence proves validity.

## Timings

| Step | Timing |
|---|---:|
| FIT-to-GPX download and validation | < 1 min |

## Handoff Notes

- Completed: TRD_09.
- Remaining unfinished coverage: TRD_10 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Overview remains open; validated GPX artifact retained only for cleanup.

