# Packet: ADM_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_09
- In scope: Public and authenticated About/credits content and Back behavior.
- Out of scope: External source-link destinations.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_08.
- Required app/data state: Healthy beta-image installation.
- Required browser context: One session deliberately signed out, then restored.

## Allowed Mutations

- Allowed: Sign out, direct public navigation, sign in, and open/close overlays.
- Not allowed: Change application preferences or service configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_09 | Opened `/about` while signed out, disclosed credits, closed it, signed in, opened About from Admin Server log, disclosed credits, and closed it. | Expected map, library, and data sources appear before/after login; Back returns to the prior Admin section or login/map fallback. | Both contexts showed the same 12-source list. Signed-out Close returned to login; authenticated Close preserved `/admin/logs` and Server log. | PASS | [assets/ADM_09-about-credits.txt](../assets/ADM_09-about-credits.txt); [assets/ADM_09-public-about.jpg](../assets/ADM_09-public-about.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_09-about-credits.txt](../assets/ADM_09-about-credits.txt) | Signed-out/authenticated routes, complete credit names, and Close destinations. |
| [assets/ADM_09-public-about.jpg](../assets/ADM_09-public-about.jpg) | Public About sheet with Credits and data sources expanded. |

## Screenshot Evidence

- The public-route capture preserves installation identity and the expanded
  library/map/data-source credits while no login is required.

## Timings

| Step | Timing |
|---|---:|
| Public About load | Under 1 s |
| Public Close to login | Under 1 s |
| Authenticated embedded open/close | About 1 s |

## Handoff Notes

- Completed: Public/authenticated About content and both Back destinations passed.
- Remaining unfinished coverage: None for ADM_09.
- Blocked or not applicable: None.
- State left for the next packet: Authenticated browser is on Admin Server log;
  the app has 14 tracks and healthy services.
