# Packet: GPS_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: GPS_05
- In scope: Applicability of disabling a live GPS stream on this run.
- Out of scope: GPS disabled-state messaging failure; covered by GPS_04.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_04.
- Required app/data state: Remote plain-HTTP quick-install target.
- Required browser context: Desktop Chromium.

## Allowed Mutations

- Allowed: None beyond GPS_01/GPS_04 evidence reuse.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Evaluated whether disabling GPS can remove a marker and stop updates after GPS_01/GPS_04 evidence. | Disabling GPS removes an existing locate marker and stops live updates. | No live position stream or locate marker can be created on remote HTTP because Chrome blocks geolocation outside secure contexts; marker removal/update-stop behavior cannot be exercised in this run. | NOT APPLICABLE | [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt); [assets/GPS_01-insecure-origin-gps-panel.webp](../assets/GPS_01-insecure-origin-gps-panel.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt) | Shared secure-origin limitation evidence. |
| [assets/GPS_01-insecure-origin-gps-panel.webp](../assets/GPS_01-insecure-origin-gps-panel.webp) | Shared GPS click screenshot on insecure origin. |

## Screenshot Evidence

**Shared GPS click screenshot on insecure origin.**

![Shared GPS click screenshot on insecure origin.](../assets/GPS_01-insecure-origin-gps-panel.webp)

## Timings

| Step | Timing |
|---|---:|
| Applicability classification | ~1 s |

## Handoff Notes

- Completed: GPS_05 terminal as `NOT APPLICABLE`.
- Remaining unfinished coverage: Continue with SRC_01.
- Blocked or not applicable: Requires localhost or HTTPS with a live geolocation stream.
- State left for the next packet: Server data unchanged.
