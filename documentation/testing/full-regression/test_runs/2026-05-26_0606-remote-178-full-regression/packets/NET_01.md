# Packet: NET_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_01
- In scope: Determine whether installed-PWA offline reload scope applies.
- Out of scope: Normal browser-tab offline expectations.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_05
- Required app/data state: App available in browser.
- Required browser context: Installed PWA/web-app mode.

## Allowed Mutations

- Allowed: Inspect run/browser context.
- Not allowed: Create a new installed app shell outside the configured run.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_01 | Compared the coverage row's installed-PWA condition with the current normal browser-tab run context. | Installed PWA/web-app mode offline reload is tested only when the app is installed as a web app. | This run used a normal browser tab; the test plan explicitly says that context is not expected to pass and should be marked not applicable. | NOT APPLICABLE | `assets/NET_01-pwa-mode.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/NET_01-pwa-mode.txt | Installed-PWA applicability decision. |

## Timings

| Step | Timing |
|---|---:|
| Applicability check | <1 min |

## Handoff Notes

- Completed: NET_01 terminal `NOT APPLICABLE`.
- Remaining unfinished coverage: NET_02 through RUN_CLEANUP.
- Blocked or not applicable: Installed PWA mode was not part of this normal browser-tab run.
- State left for the next packet: Main browser remains signed in.
