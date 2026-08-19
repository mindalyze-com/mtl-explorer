# Packet: SGN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_07
- In scope: Startup failure UI and retry affordance.
- Out of scope: Indefinite outage or destructive infrastructure failure.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_06.
- Required app/data state: Healthy disposable installation and cached signed-in client.
- Required browser context: Settled main map tab.

## Allowed Mutations

- Allowed: Briefly stop and restart only the disposable app service.
- Not allowed: Circumvent browser security policy or switch browser surfaces to evade it.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Stopped the app service and attempted to reload the existing client, then restored and health-checked the service. | Startup failure shows a retry action instead of a frozen splash. | The browser policy refused navigation while the origin was unavailable, so product UI could not be observed. Service was restored and remote HTTP 200 verified. | BLOCKED | [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt) | Controlled outage, browser constraint, and restored service evidence. |

## Screenshot Evidence

Not available; the browser rejected unavailable-origin navigation before page inspection, and ACC_04 also blocks screenshots.

## Timings

| Step | Timing |
|---|---:|
| Stop service | About 2.4 s |
| Restore service to verified remote HTTP 200 | About 30 s |

## Handoff Notes

- Completed: Safe failure injection and verified restoration.
- Remaining unfinished coverage: None; terminally blocked for this selected browser surface.
- Blocked or not applicable: Browser unavailable-origin navigation policy prevents observing the retry UI.
- State left for the next packet: Disposable app container healthy; browser tab requires replacement for later unrelated coverage.
