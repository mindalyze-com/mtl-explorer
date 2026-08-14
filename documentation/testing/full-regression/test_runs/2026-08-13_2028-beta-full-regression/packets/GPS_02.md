# Packet: GPS_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: GPS_02.
- In scope: GPS enable permission and marker applicability.
- Out of scope: secure-origin execution not available in this run.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01.
- Required app/data state: remote plain-HTTP quick install.
- Required browser context: signed-in target origin.

## Allowed Mutations

- Allowed: attempt the GPS enable control.
- Not allowed: infer a prompt/marker that the browser cannot expose.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_02 | Opened GPS to attempt enablement. | Secure-origin permission prompt; accepting shows locate marker. | The remote HTTP origin was rejected before permission/marker, with explicit HTTPS/localhost guidance. | NOT APPLICABLE | [enable attempt](../assets/GPS_02-live-enable.txt) |

## Issues

No issue found; GPS_01 defines this expected limitation for the configured run.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_02-live-enable.txt](../assets/GPS_02-live-enable.txt) | Direct attempt and secure-origin unblock path. |

## Screenshot Evidence

The exact outcome is recorded as compact text evidence.

## Timings

| Step | Timing |
|---|---:|
| Enable attempt | < 0.6 s |

## Handoff Notes

- Completed: GPS_02 is terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: GPS_03 onward.
- Blocked or not applicable: browser permission and live marker require localhost or HTTPS.
- State left for the next packet: no GPS session or marker was created.
