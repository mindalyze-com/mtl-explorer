# Packet: GPS_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: GPS_05.
- In scope: live GPS disable applicability.
- Out of scope: secure-origin marker/watch unavailable here.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_04.
- Required app/data state: no live marker or geolocation watch.
- Required browser context: remote plain-HTTP origin.

## Allowed Mutations

- Allowed: determine whether a live disable state exists.
- Not allowed: treat dismissal of the unavailable notice as disabling GPS.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Inspected the post-enable-attempt state for a marker/watch that could be disabled. | Disabling GPS removes marker and stops updates. | No live marker/watch could be created on remote HTTP, so the disable behavior cannot enter an executable state. | NOT APPLICABLE | [disable applicability](../assets/GPS_05-disable.txt) |

## Issues

No issue found; GPS_01 defines this expected limitation for live GPS checks.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_05-disable.txt](../assets/GPS_05-disable.txt) | Missing live prerequisite and exact unblock path. |

## Screenshot Evidence

No additional screenshot is useful because no live marker can exist on this origin.

## Timings

| Step | Timing |
|---|---:|
| Applicability check | Immediate after GPS_04 |

## Handoff Notes

- Completed: GPS_05 is terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: SRC_01 onward.
- Blocked or not applicable: disable/marker-update behavior requires localhost or HTTPS.
- State left for the next packet: GPS unavailable notice can be dismissed; location-search tests can proceed normally.
