# Packet: FIT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_05
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
| FIT_05 | Used visible Download GPX control evidence, then downloaded GPX export via the authenticated installed-app endpoint because in-app browser file downloads are unsupported. | Downloaded GPX is valid and contains real `trkpt` trackpoints. | Downloaded `Activity.gpx` was 479,844 bytes and contained 3,601 `<trkpt>` points. | PASS | `assets/FIT_04_05-download-verification.txt`, `assets/FIT_05-download-as-gpx.gpx` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_04_05-download-verification.txt | GPX export size/checksum/trkpt count. |
| assets/FIT_05-download-as-gpx.gpx | Downloaded FIT-to-GPX export. |

## Timings

| Step | Timing |
|---|---:|
| FIT step | <1m |

## Handoff Notes

- Completed: FIT_05 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none unless stated.
- State left for the next packet: dataset contains three remaining GPX tracks plus FIT track #100005.
