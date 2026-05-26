# Packet: SYN_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_05
- In scope: Dismissing a data-freshness banner after a new server-side change.
- Out of scope: Reloading the banner for this packet.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_04
- Required app/data state: Client refreshed to 11 tracks before this packet.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Upload one disposable GPX to create a freshness banner; click Dismiss.
- Not allowed: Force-dismiss by modifying browser storage or app internals.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_05 | Uploaded `SYN_05-dismiss-upload.gpx`, waited for the freshness banner, then clicked Dismiss via role, DOM, and coordinate paths. | Dismissing the banner does not loop or re-show immediately. | Banner appeared, but Dismiss did not hide it; the banner remained visible after all click paths and waits. | FAIL | `assets/SYN_05-dismiss-results.txt`, `assets/SYN_05-dismiss-upload.gpx` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| SYN-01 | P2 | Data freshness banner Dismiss does not hide the banner. | Upload a new GPX so `New data available` appears, then click `Dismiss`. | Banner should disappear and not immediately re-show. | Banner remains visible after Dismiss clicks through multiple interaction paths. | `assets/SYN_05-dismiss-results.txt` | Users cannot temporarily dismiss an out-of-sync warning without applying reload. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/SYN_05-dismiss-results.txt | Setup, click attempts, and observed failure. |
| assets/SYN_05-dismiss-upload.gpx | Disposable GPX used to create the freshness banner. |

## Timings

| Step | Timing |
|---|---:|
| Upload, wait for banner, dismiss attempts | <3 min |

## Handoff Notes

- Completed: SYN_05 terminal `FAIL`.
- Remaining unfinished coverage: SYN_06 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Freshness banner remains visible; client cache still shows 11 tracks while the server has the SYN_05 upload available.
