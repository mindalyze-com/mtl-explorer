# Packet: GPS_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_02
- In scope: Permission prompt, acceptance, and locate marker after enabling GPS.
- Out of scope: Non-live disabled-state messaging covered by GPS_04.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01 secure-origin classification.
- Required app/data state: GPS disabled and map usable.
- Required browser context: Remote plain-HTTP origin.

## Allowed Mutations

- Allowed: Activate GPS through the visible tool.
- Not allowed: Mock permission/location or replace the configured origin.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_02 | Used the same visible GPS activation recorded in GPS_01 and checked whether a permission prompt/accepted marker path was reachable. | On localhost/HTTPS, accepting permission creates the locate marker. | The configured remote HTTP origin is intentionally rejected before the browser permission prompt; no accepted-permission or locate-marker state can exist. GPS_01 explicitly requires this live row to be marked expected browser limitation. | NOT APPLICABLE | [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) |

## Issues

No product issue; expected browser security behavior.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) | Exact remote origin and pre-permission guard behavior. |

## Screenshot Evidence

Not applicable; the live prompt/marker path cannot exist on this origin.

## Timings

| Step | Timing |
|---|---:|
| Secure-origin guard | Under 0.35 s |

## Handoff Notes

- Completed: Directly established that permission/accept/marker is unreachable for the configured expected reason.
- Remaining unfinished coverage: None for GPS_02.
- Blocked or not applicable: NOT APPLICABLE - expected browser limitation under frozen GPS_01.
- State left for the next packet: GPS disabled; no marker.
