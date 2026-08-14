# Packet: SGN_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SGN_01.
- In scope: open the application root while signed out.
- Out of scope: credential submission.

## Prerequisites

- Required previous coverage IDs or run packets: FMT_02.
- Required app/data state: credentials-only sign-out completed without removing imported data.
- Required browser context: desktop browser with no active MTL credential.

## Allowed Mutations

- Allowed: navigate to the application root.
- Not allowed: sign in during this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_01 | Navigated to `http://91.99.12.14:18080/mtl/` after credentials-only sign-out. | The signed-out request redirects to the login screen. | The final URL was `/mtl/login`; username, password, and Sign In controls were visible. | PASS | [assets/SGN_01-signed-out-redirect.webp](../assets/SGN_01-signed-out-redirect.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_01-signed-out-redirect.webp](../assets/SGN_01-signed-out-redirect.webp) | Login screen reached from the signed-out app root. |

## Screenshot Evidence

![Signed-out app redirect](../assets/SGN_01-signed-out-redirect.webp)

## Timings

| Step | Timing |
|---|---:|
| Root navigation and redirect | < 1 s |

## Handoff Notes

- Completed: signed-out root redirect.
- Remaining unfinished coverage: SGN_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: login screen open, signed out.
