# Packet: FIT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_04
- In scope: Download original through the UI and prove it remains FIT with matching checksum.
- Out of scope: GPX conversion download.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_03.
- Required app/data state: FIT-backed track 100005 and preserved source checksum.
- Required browser context: Track Details Overview with download-artifact capture.

## Allowed Mutations

- Allowed: Trigger inbound download and inspect downloaded bytes.
- Not allowed: Equate server API output alone with complete end-user browser evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_04 | Clicked `Download original`, waited for the native download event, tried the documented media-download method, and independently fetched the exposed source-file endpoint for byte validation. | Browser download yields a FIT file matching the uploaded SHA-256. | The UI action caused no visible error, but the selected browser exposed neither a download event nor artifact path. The same authenticated endpoint returned `Activity.fit`, 94,096 bytes, a valid `.FIT` header, and an exact source checksum match. End-user artifact verification remains blocked by browser tooling. | BLOCKED | [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt); [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | UI attempts, browser constraint, and exact server byte/checksum corroboration. |
| [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) | Uploaded source checksum. |

## Screenshot Evidence

Blocked by ACC_04; the separate download-artifact channel is also unavailable.

## Timings

| Step | Timing |
|---|---:|
| UI download event wait | 15 s |
| API byte corroboration | About 2 s |

## Handoff Notes

- Completed: UI control exercised and server output proven correct.
- Remaining unfinished coverage: None; terminally blocked for end-user browser artifact checksum capture.
- Blocked or not applicable: Requires a browser surface that exposes completed download artifacts.
- State left for the next packet: FIT Overview remains open; server temp copy retained only for this run.
