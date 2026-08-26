# Packet: FIT_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_04
- In scope: Download the original FIT through the visible UI and verify format/checksum.
- Out of scope: Converted GPX download, covered by FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_03.
- Required app/data state: Track 100005 detail available with original download control.
- Required browser context: Track 100005 Overview tab.

## Allowed Mutations

- Allowed: Use visible Download original; read the downloaded payload.
- Not allowed: Rename, alter, or substitute the payload before validation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FIT_04 | Click Download original; identify format, size, header, and SHA-256. | Original remains FIT and matches uploaded checksum. | 94,096-byte payload is recognized as FIT, contains the FIT signature, and exactly matches the uploaded SHA-256. | PASS | [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Visible action, file identification, header, size, and checksum comparison. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the downloaded payload is validated directly.

## Timings

| Step | Timing |
|---|---:|
| Original download and validation | 1 min |

## Handoff Notes

- Completed: Original FIT payload is byte-identical and format-preserving.
- Remaining unfinished coverage: None for FIT_04.
- Blocked or not applicable: In-app browser kept a temporary `.crdownload` name, but the complete payload was available and valid.
- State left for the next packet: Track 100005 Overview remains open; original payload is read-only in Downloads.
