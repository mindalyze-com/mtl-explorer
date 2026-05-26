# Packet: ERR_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ERR_01
- In scope: Error cases for failed track/map/API load, broken media, planner route trouble applicability, and expired session.
- Out of scope: Fixing already-recorded failures.

## Prerequisites

- Required previous coverage IDs or run packets: NET_02, NET_03, MED_05, PLN_10
- Required app/data state: Regression evidence for each referenced error surface available.
- Required browser context: Main browser plus isolated contexts used by NET_02 and NET_03.

## Allowed Mutations

- Allowed: Aggregate direct error-case evidence from completed packets.
- Not allowed: Stop the target server or corrupt non-disposable data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ERR_01 | Reviewed direct error-case evidence for API load failure, expired session, broken media, and planner route trouble applicability. | Failed track load, failed map config, failed media, failed planner route, and expired session show actionable messages and can recover. | API/map failure showed Retry and expired session redirected to login, but broken media still rendered a blank/broken preview with no actionable recovery message. Planner route-fetch trouble did not occur in this configured run. | FAIL | `assets/ERR_01-error-recovery.txt`, `assets/NET_02-network-recovery.txt`, `assets/NET_03-auth-redirect.txt`, `assets/MED_05-broken-media.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MED-01 | P2 | Broken media renders as a blank/broken image instead of a recoverable error. | Index a valid geotagged JPEG, replace the file with invalid bytes, then open it from the Photos & Media layer. | Preview should show an explicit recoverable error with a clear message and a way to dismiss, retry, or navigate away. | Server returns `200 image/jpeg` with an empty body, and the UI displays a blank/broken image area with no actionable error state. | `assets/MED_05-broken-media.txt`, `assets/MED_05-broken-preview.webp` | Broken or missing media can look like an empty preview sheet, leaving users without useful recovery guidance. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/ERR_01-error-recovery.txt | Aggregate error-case recovery summary. |
| assets/NET_02-network-recovery.txt | Failed API/map/track load recovery evidence. |
| assets/NET_03-auth-redirect.txt | Expired/invalid session redirect evidence. |
| assets/MED_05-broken-media.txt | Broken media failure evidence. |

## Timings

| Step | Timing |
|---|---:|
| Aggregate error recovery review | <2 min |

## Handoff Notes

- Completed: ERR_01 terminal `FAIL`.
- Remaining unfinished coverage: ERR_02 through RUN_CLEANUP.
- Blocked or not applicable: Planner route-fetch trouble did not occur and was not forced in this fixed-target run; the row fails because broken media lacks actionable recovery.
- State left for the next packet: Main browser signed in on desktop viewport.
