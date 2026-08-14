# Packet: GPS_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: GPS_01.
- In scope: secure-origin applicability for browser geolocation.
- Out of scope: live location behavior unavailable on this configured origin.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_03.
- Required app/data state: signed-in remote quick-install app.
- Required browser context: `http://91.99.12.14.nip.io:18080/mtl/`.

## Allowed Mutations

- Allowed: open GPS and inspect the app/browser limitation.
- Not allowed: replace the configured target with localhost/HTTPS.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Opened GPS from the configured remote plain-HTTP origin. | Mark live GPS checks not applicable on remote HTTP; use localhost/HTTPS instead. | The app reported GPS unavailable and explicitly required HTTPS or localhost. | NOT APPLICABLE | [origin and message](../assets/GPS_01-insecure-origin.txt) |

## Issues

No issue found; this is the frozen plan's expected browser limitation.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-insecure-origin.txt](../assets/GPS_01-insecure-origin.txt) | Configured origin and exact limitation copy. |

## Screenshot Evidence

The exact user-facing message is captured as compact text evidence.

## Timings

| Step | Timing |
|---|---:|
| GPS limitation response | < 0.6 s |

## Handoff Notes

- Completed: GPS_01 is terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: GPS_02 onward.
- Blocked or not applicable: live geolocation requires localhost or HTTPS.
- State left for the next packet: GPS unavailable notice visible on the map.
