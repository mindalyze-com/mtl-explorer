# Packet: ADM_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_02
- In scope: Track file upload availability, accepted formats, success, unsupported-format errors, and empty-file errors.
- Out of scope: Native OS file-picker automation, which is not exposed by the current browser runtime.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_01
- Required app/data state: Admin dialog open on Upload.
- Required browser context: Desktop browser; authenticated API token for upload endpoint checks.

## Allowed Mutations

- Allowed: Upload a disposable GPX file and send unsupported/empty upload probes.
- Not allowed: Delete source data outside the disposable run cleanup.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_02 | Inspected the Upload UI and exercised the backing upload endpoint with unsupported, empty, and valid files. | Drag/pick upload supports GPX/FIT/etc.; availability, accepted formats, progress, success, unsupported-format errors, and empty-file errors are clear. | Upload availability was clear and API success/error messages were clear. However, the UI picker/drop-zone only advertises and accepts `.gpx`, while the endpoint reports broader accepted formats. Native UI progress could not be driven because this browser runtime exposes no file-picker/set-input-files method. | FAIL | `assets/ADM_02-upload-panel.webp`, `assets/ADM_02-upload-results.txt`, `assets/ADM_02-upload-valid.gpx`, `assets/ADM_02-upload-unsupported.xyz` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| ADM-01 | P2 | Admin upload UI only allows `.gpx` while backend and regression scope include multiple track formats. | Open Admin -> Upload and inspect the file input/drop-zone. Compare with `/api/gpx-upload/upload` unsupported-format response. | Upload UI should clearly support the same accepted track formats as the upload endpoint or explain the restriction. | UI says `Click or drag a .gpx file here` and the input has `accept=".gpx"`; endpoint reports `.gpx, .fit, .tcx, .kml, .kmz, .igc, .sbp, .nmea, .geojson, .gdb`. | `assets/ADM_02-upload-panel.webp`, `assets/ADM_02-upload-results.txt` | Users cannot pick or drag supported non-GPX track files from the Admin upload UI. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_02-upload-panel.webp | Screenshot of the Upload panel. |
| assets/ADM_02-upload-results.txt | UI accepted-format details plus API success/error output. |
| assets/ADM_02-upload-valid.gpx | Disposable valid GPX payload uploaded for success coverage. |
| assets/ADM_02-upload-unsupported.xyz | Unsupported-format payload used for error coverage. |

## Timings

| Step | Timing |
|---|---:|
| Upload UI/API checks | <2 min |

## Handoff Notes

- Completed: ADM_02 terminal `FAIL` due UI accepted-format mismatch; API success and error messages were captured.
- Remaining unfinished coverage: ADM_03 through RUN_CLEANUP.
- Blocked or not applicable: Native file-picker/progress automation is a tooling limitation in this browser runtime.
- State left for the next packet: `ADM_02-upload-valid.gpx` was uploaded to `GPX-UPLOAD` and may index as an additional track/freshness change.
