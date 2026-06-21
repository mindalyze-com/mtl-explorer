# Packet: SGN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_07
- In scope: Verify that startup failure offers an actionable retry instead of freezing on splash/progress UI.
- Out of scope: Generic server health checks after recovery except to restore the shared test state.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_06.
- Required app/data state: Signed-in browser context with imported tracks; quick-install stack running.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Temporarily stop and restart a dependency to simulate startup failure; restart the app service to restore the test target.
- Not allowed: Change imported track data or product configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Stopped only the db service, loaded/reloaded the app, then restarted db and app to recover the target. | Startup dependency failure shows an actionable retry or recoverable error state instead of leaving the user on frozen splash/progress UI. | Browser navigation timed out during the db outage. After db recovery and app restart, fresh tabs still showed startup/progress text, map shell, and `0 Tracks` without a retry control. API and compose checks confirmed the stack was restored afterward. | FIXED | [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt); [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| SGN-07-P2 | P2 | Startup failure can strand users on progress UI without retry. | In the quick-install stack, stop the db service while the app remains running, then open or reload `/mtl/`; restore db/app afterward. | The frontend should show an actionable retry or recoverable startup error once initial loading fails. | The browser load timed out during the outage, and after recovery fresh tabs showed startup text plus `0 Tracks` with no retry control. | [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt) | Users encountering a transient startup dependency outage may see a frozen/progress shell and have no clear self-service recovery action. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt) | Local implementation and verification evidence for FIXED status. |
| [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt) | Fault injection, observed UI/API behavior, and recovery notes. |

## Screenshot Evidence

No screenshot asset was captured for this packet; the relevant failure path produced browser navigation/screenshot timeouts, so the compact text evidence records the observed DOM states and recovery checks.

## Timings

| Step | Timing |
|---|---:|
| Fault injection, recovery, and verification | ~10 min |

## Handoff Notes

- Fix status: FIXED locally: startup failure keeps retry/error state visible; see shared fix evidence. Evidence: [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt).

- Completed: SGN_07.
- Remaining unfinished coverage: SGN_08 onward.
- Blocked or not applicable: none.
- State left for the next packet: The quick-install stack is running again; db and location-search reported healthy; API endpoints returned HTTP 200. The browser may need a fresh tab or re-login before the next UI packet.
