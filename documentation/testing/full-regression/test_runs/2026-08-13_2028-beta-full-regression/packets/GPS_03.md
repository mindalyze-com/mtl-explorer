# Packet: GPS_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: GPS_03.
- In scope: follow-mode applicability.
- Out of scope: live follow behavior unavailable on remote HTTP.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_02.
- Required app/data state: no live GPS watch or marker.
- Required browser context: remote plain-HTTP origin.

## Allowed Mutations

- Allowed: inspect whether follow mode can be entered.
- Not allowed: fabricate location updates.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Attempted to reach Follow me after the GPS enable action. | Follow keeps the map centered until user pan causes drift. | No live GPS session exists on this insecure origin, so Follow me and drift states cannot be entered. | NOT APPLICABLE | [follow applicability](../assets/GPS_03-follow.txt) |

## Issues

No issue found; GPS_01 defines this expected limitation for the configured run.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_03-follow.txt](../assets/GPS_03-follow.txt) | Missing secure-origin prerequisite and unblock path. |

## Screenshot Evidence

No extra screenshot is useful because the prerequisite live state cannot exist.

## Timings

| Step | Timing |
|---|---:|
| Applicability check | Immediate after GPS_02 |

## Handoff Notes

- Completed: GPS_03 is terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: GPS_04 onward.
- Blocked or not applicable: live Follow me needs a secure origin and accepted location.
- State left for the next packet: GPS unavailable notice remains the only GPS state.
