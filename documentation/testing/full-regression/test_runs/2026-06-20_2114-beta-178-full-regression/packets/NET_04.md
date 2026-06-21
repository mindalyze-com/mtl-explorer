# Packet: NET_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` section 19.
- Coverage ID or run packet: NET_04.
- In scope: Determine whether the service-worker update prompt check applies to this configured run.
- Out of scope: Installing MTL Explorer as a web app, publishing a second app build, or forcing an installed-PWA service-worker update.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP, NET_01.
- Required app/data state: Authenticated desktop browser context with loaded map.
- Required browser context: Normal desktop browser tab on the remote plain-HTTP target.

## Allowed Mutations

- Allowed: Read-only browser context inspection.
- Not allowed: Change app build assets, alter service-worker cache state, or mutate server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Loaded MTL Explorer in the configured normal browser-tab context and checked display mode, service-worker support/registrations, and update-prompt visibility. | The service-worker update prompt row applies to installed web-app/PWA mode; normal browser-tab runs on remote HTTP should not be judged against that installed update flow. | The context was a normal browser tab, not standalone; because the target is remote plain HTTP, `serviceWorker` was not supported, no registrations existed, and no update prompt was visible. The app itself loaded with `16 Tracks`. | NOT APPLICABLE | [assets/NET_04-service-worker-applicability.txt](../assets/NET_04-service-worker-applicability.txt); [assets/NET_04-service-worker-applicability.webp](../assets/NET_04-service-worker-applicability.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_04-service-worker-applicability.txt](../assets/NET_04-service-worker-applicability.txt) | Display-mode, service-worker support, registration, prompt, and app-load evidence. |
| [assets/NET_04-service-worker-applicability.webp](../assets/NET_04-service-worker-applicability.webp) | Loaded normal browser-tab app surface for the applicability check. |

## Screenshot Evidence

![Normal browser-tab app surface](../assets/NET_04-service-worker-applicability.webp)

## Timings

| Step | Timing |
|---|---:|
| Service-worker update applicability check | 2026-06-21 06:18 CEST |

## Handoff Notes

- Completed: NET_04 terminalized as `NOT APPLICABLE` for this normal browser-tab, remote plain-HTTP run.
- Remaining unfinished coverage: ERR_01, ERR_02, RUN_CLEANUP.
- Blocked or not applicable: Installed web-app service-worker update behavior requires a secure installed PWA context and a second app build/update.
- State left for the next packet: No mutations made.
