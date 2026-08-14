# Packet: FIT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FIT_04.
- In scope: download the FIT-backed track's original source and verify format/checksum.
- Out of scope: GPX conversion download, covered by FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_03 and DAT_05.
- Required app/data state: FIT-backed record #100005 with original-download action.
- Required browser context: signed-in in-app browser and local download directory.

## Allowed Mutations

- Allowed: click Download original and inspect only the resulting exact download artifact.
- Not allowed: alter the downloaded file or infer success from the button alone.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_04 | Clicked Download original from Activity.fit Overview; measured, identified, and hashed the resulting browser download. | The downloaded original remains FIT and matches the uploaded source checksum. | The browser wrote 94,096 bytes identified as FIT Map data. SHA-256 exactly matches the uploaded/staged Activity.fit. The in-app Chromium context retained a temporary `.crdownload` filename, but content completed byte-for-byte. | PASS | [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt); [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Browser artifact path, size, type, uploaded/downloaded checksums, and cleanup obligation. |
| [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) | Independent public source and FIT integrity validation. |

## Screenshot Evidence

The Download original action is visible in [FIT_02-detail.webp](../assets/FIT_02-detail.webp); file integrity is represented by the exact checksum evidence above.

## Timings

| Step | Timing |
|---|---:|
| Download and checksum verification | < 1 min |

## Handoff Notes

- Completed: original FIT content downloaded byte-for-byte and identified as FIT.
- Remaining unfinished coverage: FIT_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: FIT Overview open; one exact test artifact is in Downloads and must be removed during cleanup.
