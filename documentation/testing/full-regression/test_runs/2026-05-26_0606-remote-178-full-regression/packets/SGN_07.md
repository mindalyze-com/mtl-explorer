# Packet: SGN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_07
- In scope: startup failure handling when API requests fail.
- Out of scope: physically stopping the shared server.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: app running.
- Required browser context: desktop browser context with saved auth state and no cached track data.

## Allowed Mutations

- Allowed: simulate API request failures in the browser context.
- Not allowed: stop the shared server or mutate app data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Reopened the app with saved auth state while aborting `/mtl/api/**` requests. | Startup failure offers retry instead of freezing on splash. | The app showed `Unable to load tracks — no server connection and no cached data available. Retry`; no frozen splash remained. | PASS | `assets/SGN_07-storage-startup-api-failure.webp`, `assets/SGN_07-storage-startup-api-failure.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_07-storage-startup-api-failure.webp | Startup failure with retry control. |
| assets/SGN_07-storage-startup-api-failure.txt | Failed API requests, visible text, and retry detection. |
| assets/SGN_07-startup-api-failure.txt | Cached-data fallback probe. |

## Timings

| Step | Timing |
|---|---:|
| Failure observation window | 7s |

## Handoff Notes

- Completed: SGN_07 terminal PASS.
- Remaining unfinished coverage: continue with SGN_08.
- Blocked or not applicable: none.
- State left for the next packet: server remained running; API failures were browser-context-only.
