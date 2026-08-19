# Packet: SGN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_05
- In scope: UI sign-out and subsequent sign-in.
- Out of scope: Wipe & logout.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_04.
- Required app/data state: Valid account and populated map.
- Required browser context: Signed-in main tab.

## Allowed Mutations

- Allowed: Sign out and sign in again.
- Not allowed: Wipe local application data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_05 | Signed in, used Admin > Session > Sign out, then signed in again. | Return to login; subsequent sign-in works. | Sign out reached `/mtl/login`; re-login returned to `/mtl/` with map and 14 tracks. | PASS | [assets/SGN_05-logout-relogin.txt](../assets/SGN_05-logout-relogin.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_05-logout-relogin.txt](../assets/SGN_05-logout-relogin.txt) | Signed-out and recovered signed-in routes/states. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM and URL evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Sign out and re-login | About 9 s after settled map state |

## Handoff Notes

- Completed: Sign-out and login recovery passed.
- Remaining unfinished coverage: None for SGN_05.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Signed in on populated map.
