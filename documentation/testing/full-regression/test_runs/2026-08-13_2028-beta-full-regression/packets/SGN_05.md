# Packet: SGN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SGN_05.
- In scope: credentials-only sign-out followed by a new valid sign-in.
- Out of scope: wipe-and-logout data removal.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_04.
- Required app/data state: valid account and 12 imported visible records.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: Admin > Session > Sign out and valid login.
- Not allowed: Wipe & logout.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_05 | Used Admin > Session > Sign out, then entered the valid credentials again. | Sign-out returns to login; signing in again works. | Sign-out changed the URL to `/mtl/login` with the form visible. The next valid sign-in returned to `/mtl/` with all 12 tracks. | PASS | [assets/SGN_05-sign-out.webp](../assets/SGN_05-sign-out.webp); [assets/SGN_05-sign-in-again.webp](../assets/SGN_05-sign-in-again.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_05-sign-out.webp](../assets/SGN_05-sign-out.webp) | Login screen immediately after sign-out. |
| [assets/SGN_05-sign-in-again.webp](../assets/SGN_05-sign-in-again.webp) | Restored map and data after signing in again. |

## Screenshot Evidence

![Login after sign-out](../assets/SGN_05-sign-out.webp)

![Map after signing in again](../assets/SGN_05-sign-in-again.webp)

## Timings

| Step | Timing |
|---|---:|
| Sign-out redirect | < 1 s |
| New sign-in to loaded map | 1.2 s |

## Handoff Notes

- Completed: sign-out and repeat sign-in.
- Remaining unfinished coverage: SGN_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: signed in on the 12-track map.
