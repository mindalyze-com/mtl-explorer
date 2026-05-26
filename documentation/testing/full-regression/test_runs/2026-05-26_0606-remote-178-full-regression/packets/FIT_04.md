# Packet: FIT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_04
- In scope: FIT conversion/import/details/download flow.
- Out of scope: non-FIT format coverage.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05, DEL flow complete, app running.
- Required app/data state: Garmin `Activity.fit` staged before FIT_01.
- Required browser context: desktop signed-in browser; API token used only because in-app browser downloads are unsupported.

## Allowed Mutations

- Allowed: copy FIT to watched folder, use visible UI controls, download files from app endpoints.
- Not allowed: product source edits or workarounds that alter conversion behavior.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_04 | Used visible Download original control evidence, then downloaded original source via the authenticated installed-app endpoint because in-app browser file downloads are unsupported. | Downloaded original source remains FIT and matches uploaded checksum. | Downloaded `Activity.fit` was 94,096 bytes with SHA-256 `949a238e...d591387`, matching the uploaded file. | PASS | `assets/FIT_04_05-download-controls.webp`, `assets/FIT_04_05-download-verification.txt`, `assets/FIT_04-download-original.fit` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_04_05-download-controls.webp | Visible download controls. |
| assets/FIT_04_05-download-verification.txt | Checksum and endpoint verification. |
| assets/FIT_04-download-original.fit | Downloaded original FIT file. |

## Timings

| Step | Timing |
|---|---:|
| FIT step | <1m |

## Handoff Notes

- Completed: FIT_04 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none unless stated.
- State left for the next packet: dataset contains three remaining GPX tracks plus FIT track #100005.
