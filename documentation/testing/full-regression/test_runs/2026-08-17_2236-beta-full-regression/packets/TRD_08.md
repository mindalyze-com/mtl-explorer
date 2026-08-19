# Packet: TRD_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_08
- In scope: End-user original-source download and byte-for-byte source match for GPX, FIT, and other imported formats.
- Out of scope: Converted GPX download.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_04 and FMT_02.
- Required app/data state: Preserved imported source files and checksums.
- Required browser context: Track Details download controls.

## Allowed Mutations

- Allowed: Trigger inbound downloads and independently corroborate exposed server bytes.
- Not allowed: Treat server-only retrieval as complete browser artifact proof.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_08 | Exercised original-source download controls for FIT and other imported formats, waited for browser downloads, and validated the authenticated source endpoint bytes against preserved inputs. | A browser artifact downloads and exactly matches each uploaded source. | The UI actions produced no visible error and authenticated source bytes matched the imported FIT checksum exactly, but the selected browser exposes neither completed download events nor artifact paths. End-user artifact matching cannot be completed in this environment. | BLOCKED | [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt), [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt), [packets/FMT_02.md](FMT_02.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | UI attempts, unavailable artifact channel, FIT header, byte count, and exact checksum corroboration. |
| [assets/FIT_01-copy.txt](../assets/FIT_01-copy.txt) | Preserved uploaded FIT source checksum. |
| [packets/FMT_02.md](FMT_02.md) | Cross-format UI download coverage and the same browser-artifact constraint. |

## Screenshot Evidence

Blocked by ACC_04; completed download artifacts are independently unavailable from the selected browser.

## Timings

| Step | Timing |
|---|---:|
| Reused UI download-event waits | 25 s |
| Server byte and checksum corroboration | About 2 s |

## Handoff Notes

- Completed: Download controls exercised and server output matched preserved source bytes.
- Remaining unfinished coverage: None; terminally blocked for completed browser artifact access.
- Blocked or not applicable: A browser download-artifact channel is required to prove the file received by the end user.
- State left for the next packet: Imported source records are unchanged.
