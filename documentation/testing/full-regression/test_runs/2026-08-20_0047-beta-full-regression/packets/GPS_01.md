# Packet: GPS_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_01
- In scope: Classify geolocation coverage for the configured remote plain-HTTP quick-install and verify the app's secure-origin guard.
- Out of scope: Live permission/marker/follow behavior that requires localhost or HTTPS.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP target/origin evidence.
- Required app/data state: Normal eight-track map; filters/layers restored.
- Required browser context: Authenticated remote `http://62.238.106.141:18080/mtl/`.

## Allowed Mutations

- Allowed: Activate the visible GPS tool and inspect the resulting user-visible state.
- Not allowed: Substitute a different origin, mock geolocation, or alter browser permissions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Confirmed remote plain HTTP and activated GPS. | Frozen rule classifies live GPS permission/marker rows as expected browser limitation on this origin; app should explain the constraint. | App immediately showed `GPS unavailable` and the exact HTTPS/localhost requirement, created no marker, and left the map usable. Live rows are correctly classified NOT APPLICABLE for this run. | PASS | [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) | Exact origin/configuration, visible guard message, and live-row classification. |

## Screenshot Evidence

Live desktop inspection confirmed the GPS unavailable alert. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| GPS guard response | Under 0.35 s |

## Handoff Notes

- Completed: Origin check, visible GPS activation, clear guard state, and frozen classification.
- Remaining unfinished coverage: None for GPS_01.
- Blocked or not applicable: GPS_02/03/05 live paths require localhost or HTTPS and will be terminal NOT APPLICABLE.
- State left for the next packet: GPS remains disabled; no marker/follow state exists; map stays usable at 8 tracks.
