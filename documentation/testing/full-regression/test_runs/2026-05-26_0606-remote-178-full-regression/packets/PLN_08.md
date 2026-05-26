# Packet: PLN_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_08
- In scope: saved planner-route GPX download and validation.
- Out of scope: browser download event handling; the in-app browser cannot reliably surface downloaded files, so the authenticated endpoint was verified directly.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_07.
- Required app/data state: active route available to save as a disposable plan.
- Required browser context: signed-in desktop browser; API credentials from README.

## Allowed Mutations

- Allowed: save and delete one disposable planned route.
- Not allowed: leave saved test routes behind.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_08 | Saved `Regression PLN08 export route`, downloaded `/api/planner/plans/{id}/gpx`, validated GPX against saved-plan detail, then deleted the plan. | Download plan as GPX returns a valid GPX file matching the planned route. | Download returned HTTP 200 `application/gpx+xml`; GPX root/name were valid; 5 `<trkpt>` entries matched the saved-plan detail coordinate count and first/last coordinates; disposable plan delete returned 204. | PASS | `assets/PLN_08-after-save.webp`, `assets/PLN_08-download-headers.txt`, `assets/PLN_08-download-verification.txt`, `assets/PLN_08-export.gpx` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_08-after-save.webp | Disposable export route saved from the UI. |
| assets/PLN_08-download-headers.txt | Planner GPX response headers. |
| assets/PLN_08-download-verification.txt | GPX validation and cleanup result. |
| assets/PLN_08-export.gpx | Downloaded planner GPX file. |

## Timings

| Step | Timing |
|---|---:|
| Save/export/validate/delete | <5m |

## Handoff Notes

- Completed: PLN_08 terminal PASS.
- Remaining unfinished coverage: continue with PLN_09.
- Blocked or not applicable: none.
- State left for the next packet: disposable PLN_08 saved route deleted; planner still open.
