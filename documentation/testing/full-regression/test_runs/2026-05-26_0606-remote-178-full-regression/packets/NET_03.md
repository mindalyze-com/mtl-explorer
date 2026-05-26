# Packet: NET_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_03
- In scope: 401/403-style auth failure redirects to login.
- Out of scope: Wrong-password login validation, already covered by SGN_03.

## Prerequisites

- Required previous coverage IDs or run packets: NET_02
- Required app/data state: App running on target.
- Required browser context: Isolated browser context.

## Allowed Mutations

- Allowed: Corrupt the JWT in an isolated browser context and reload.
- Not allowed: Corrupt auth state in the main regression browser session.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_03 | Logged in in an isolated context, replaced `mtl.jwt` with an invalid token, and reloaded. | 401/403 from the server redirects to login. | The app redirected to `/mtl/login?reason=expired` and showed the login form. | PASS | `assets/NET_03-auth-redirect.txt`, `assets/NET_03-auth-redirect.spec.ts` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/NET_03-auth-redirect.txt | Invalid-auth redirect evidence. |
| assets/NET_03-auth-redirect.spec.ts | Isolated Playwright check used to corrupt auth token. |

## Timings

| Step | Timing |
|---|---:|
| Invalid-auth reload check | <3 min |

## Handoff Notes

- Completed: NET_03 passed.
- Remaining unfinished coverage: NET_04 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Main browser remains signed in; isolated test context closed.
