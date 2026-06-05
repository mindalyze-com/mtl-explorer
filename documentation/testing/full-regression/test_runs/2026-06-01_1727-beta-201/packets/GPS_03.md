# Packet: GPS_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_03
- In scope: Applicability of live Follow me mode on this run.
- Out of scope: Secure-origin proof; covered by GPS_01.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_02.
- Required app/data state: Remote plain-HTTP quick-install target.
- Required browser context: Desktop Chromium.

## Allowed Mutations

- Allowed: None beyond GPS_01 evidence reuse.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Evaluated whether Follow me centering/drift can be tested after GPS_01 secure-origin proof. | Follow me requires an accepted live geolocation stream and marker. | No live position is available on the remote HTTP origin because Chrome blocks geolocation outside secure contexts; follow/drift behavior cannot be exercised in this run. | NOT APPLICABLE | [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt) | Shared secure-origin limitation evidence. |

## Timings

| Step | Timing |
|---|---:|
| Applicability classification | ~1 s |

## Handoff Notes

- Completed: GPS_03 terminal as `NOT APPLICABLE`.
- Remaining unfinished coverage: Continue with GPS_04.
- Blocked or not applicable: Requires localhost or HTTPS with geolocation permission accepted.
- State left for the next packet: Server data unchanged.
