# Packet: SGN_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_01
- In scope: Signed-out first open redirects to login.
- Out of scope: Successful and failed credential entry; covered by SGN_02 and SGN_03.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP.
- Required app/data state: app running.
- Required browser context: clean desktop browser context with no stored session.

## Allowed Mutations

- Allowed: open the app in a clean browser context.
- Not allowed: sign in.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_01 | Opened `http://178.104.209.132:18080/mtl/` in a clean browser context. | You are redirected to the login screen. | PASS: browser landed on `/mtl/login`; the page title was `MTL Explorer`; one text input, one password input, and a `Sign In` button were visible. | PASS | [assets/SGN_01-login-redirect.txt](../assets/SGN_01-login-redirect.txt); [assets/SGN_01-login-redirect.webp](../assets/SGN_01-login-redirect.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_01-login-redirect.txt](../assets/SGN_01-login-redirect.txt) | URL, input, button, and text evidence for signed-out redirect. |
| [assets/SGN_01-login-redirect.webp](../assets/SGN_01-login-redirect.webp) | Login screen screenshot from clean context. |

## Screenshot Evidence

![Signed-out login redirect](../assets/SGN_01-login-redirect.webp)

## Timings

| Step | Timing |
|---|---:|
| Clean-context app open | ~3 seconds |

## Handoff Notes

- Completed: SGN_01 is terminal.
- Remaining unfinished coverage: SGN_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: clean context was closed; existing authenticated desktop state remains available separately.
