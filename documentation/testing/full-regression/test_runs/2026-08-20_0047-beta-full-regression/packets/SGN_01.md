# Packet: SGN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_01
- In scope: Open the app while signed out and verify login redirect.
- Out of scope: Valid/invalid credential submission, covered by SGN_02-SGN_03.

## Prerequisites

- Required previous coverage IDs or run packets: MED_06.
- Required app/data state: Server available; browser credentials removed through visible Sign out.
- Required browser context: Signed-out in-app browser.

## Allowed Mutations

- Allowed: Sign out credentials only; navigate to app root.
- Not allowed: Wipe local data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| SGN_01 | Sign out, then open `/mtl/` while signed out. | Redirect to login; protected views stay unavailable. | Browser redirected to `/mtl/login` with MTL Explorer branding and username/password/Sign In controls. | PASS | [assets/SGN_01-redirect.txt](../assets/SGN_01-redirect.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_01-redirect.txt](../assets/SGN_01-redirect.txt) | Signed-out action, redirect URL, and visible login controls. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible login state is recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Sign out and root redirect | <1 min |

## Handoff Notes

- Completed: Signed-out root redirect and protected-content check.
- Remaining unfinished coverage: None for SGN_01.
- Blocked or not applicable: None.
- State left for the next packet: Browser is signed out at `/mtl/login`.
