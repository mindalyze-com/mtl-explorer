# Packet: SGN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_07
- In scope: Verify an actionable Retry state when startup cannot load tracks.
- Out of scope: Prolonged outage or destructive infrastructure failure.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_06.
- Required app/data state: Healthy disposable installation; browser cache cleared through the visible wipe action.
- Required browser context: Clean signed-out login page in the in-app browser.

## Allowed Mutations

- Allowed: Briefly pause/unpause only the disposable database service; clear browser-local application data.
- Not allowed: Delete server data or alter Compose configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Wipe browser-local data, authenticate, pause the database during root startup, observe the failure UI, then restore and health-check the service. | Startup failure offers Retry instead of a frozen splash. | The beta target originally hid the splash and exposed an empty 0-track map. In the current-worktree local retest, the startup deadline kept the curtain visible with a clear delayed-loading message and enabled Retry action; the empty map was not exposed. Restoring the isolated database and signing in again returned to the normal zero-track map. | FIXED | [original failure](../assets/SGN_07-startup-failure.txt); [local fix record](../assets/MTL-FR-003-fix-local.txt); [repaired UI](../assets/MTL-FR-003-fix-local.webp) |

## Issues

| ID | Severity | Summary | Finding status | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|---|
| MTL-FR-003 | P2 | Startup dependency failure exposes an empty map without Retry. | FIXED | With empty browser application data, authenticate and pause the disposable database while root startup begins. | Show an actionable startup error with Retry. | The original beta target exposed the empty map. The current local build keeps the curtain visible after the startup deadline, explains that loading is delayed, and exposes an enabled Retry action; a late successful load still clears the warning automatically. | [original failure](../assets/SGN_07-startup-failure.txt); [local fix record](../assets/MTL-FR-003-fix-local.txt); [repaired UI](../assets/MTL-FR-003-fix-local.webp) | Local fix verified; the regression target image still needs a rebuild and deployment. |

## Fix Record

- Root cause: the 12-second fallback hid the startup curtain while track requests could remain pending longer during a dependency outage. A later explicit failure also did not restore the hidden curtain.
- Implementation: timeout now keeps the curtain visible with a delayed-loading message and Retry; explicit failure restores the curtain with the no-server/no-cache message; late track success clears a timeout-only warning.
- Automated checks: focused 3-test spec, full 130-file/740-test Vitest suite, focused Prettier/ESLint, Vue type check, and production build passed.
- Local retest: current-worktree Vite and Spring Boot servers used a fresh isolated PostGIS database and empty synthetic watch directories. Pausing only that database kept the repaired timeout UI visible instead of exposing the map. Expected outage diagnostics were observed; normal sign-in recovered after restore.
- Evidence: [local fix record](../assets/MTL-FR-003-fix-local.txt) and [repaired UI](../assets/MTL-FR-003-fix-local.webp).
- Boundary: no image was built, deployed, or published by this fix task.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt) | Controlled fault, failed UI states, and verified service/UI recovery. |
| [assets/MTL-FR-003-fix-local.txt](../assets/MTL-FR-003-fix-local.txt) | Root cause, implementation, automated checks, isolated local retest, and release boundary. |
| [assets/MTL-FR-003-fix-local.webp](../assets/MTL-FR-003-fix-local.webp) | Repaired current-worktree timeout curtain with Retry. |

## Screenshot Evidence

Original beta-target screenshot capture was blocked in ACC_04. The local repaired state was captured successfully:

![Startup timeout keeps the curtain visible with Retry](../assets/MTL-FR-003-fix-local.webp)

## Timings

| Step | Timing |
|---|---:|
| Sign In to first failure-state observation | 6.338 s |
| Sign In to post-splash failure state | 29.474 s |
| Database health and API recovery | <30 s after unpause |
| Local repaired timeout state | Visible after the configured 12 s startup deadline |

## Handoff Notes

- Completed: Safe startup fault injection, focused implementation, automated checks, and current-worktree local browser retest.
- Remaining unfinished coverage: None for SGN_07.
- Blocked or not applicable: None.
- State left for the next packet: Original regression database remains healthy; isolated local verification state was cleaned up after evidence capture.
