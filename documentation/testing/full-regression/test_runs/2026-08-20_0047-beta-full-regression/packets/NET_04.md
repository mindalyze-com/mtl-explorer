# Packet: NET_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: NET_04
- In scope: Installed-web-app service-worker update prompt and accepting/reloading it.

## Prerequisites

- Required previous coverage IDs or run packets: NET_03 and NET_01.
- Required app/data state: Two deployable application versions.
- Required browser context: Installed PWA/web-app mode.

## Allowed Mutations

- Allowed: Apply the frozen mode applicability rule.
- Not allowed: Replace the required app image or deploy a second version during this frozen single-image regression.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_04 | Confirmed the active client is the same normal browser tab and that no second app build/update is in scope. | Installed web app offers a new-version prompt and reloads cleanly after acceptance. | The frozen pass restricts NET_01-NET_04 to installed PWA/web-app mode; this run has only a normal tab and one required image, so the update lifecycle does not apply. | NOT APPLICABLE | [assets/NET_01-mode.txt](../assets/NET_01-mode.txt) |

## Issues

No issue; this is an explicit client-mode limitation.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/NET_01-mode.txt](../assets/NET_01-mode.txt) | Normal-tab mode evidence shared by the installed-web-app-only checks. |

## Screenshot Evidence

Not required for the applicability decision.

## Timings

| Step | Timing |
|---|---:|
| Mode/build applicability check | Under 1 min |

## Handoff Notes

- Completed: Installed-web-app update applicability decision.
- Remaining unfinished coverage: None for NET_04.
- Blocked or not applicable: Entire installed-app update lifecycle is NOT APPLICABLE.
- State left for the next packet: Required image still running; fresh signed-in normal tab.
