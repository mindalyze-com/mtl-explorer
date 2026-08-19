# Packet: ADM_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_06
- In scope: Vector tile, location search, and routing operational cards across
  ready, unavailable, disabled, and applicable recovery states.
- Out of scope: Data freshness behavior, covered by ADM_07.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_05.
- Required app/data state: Healthy app, location-search, and brouter services;
  existing reversible MAP_13 remote-mode override.
- Required browser context: Desktop Admin > Processing.

## Allowed Mutations

- Allowed: Temporarily stop/start disposable sidecars and temporarily recreate
  app in existing remote map mode, then restore normal compose state.
- Not allowed: Alter data or leave any service degraded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_06 | Inspected healthy cards/APIs, stopped/restored location and routing sidecars, switched app to existing remote tile mode, then restored local hosted-fallback mode. | Operational tasks show ready/downloading/unavailable/disabled states with useful detail. | Healthy cards included source/version/count detail. Stopped sidecars showed clear unavailable reasons and recovered. Remote mode showed vector tiles disabled with `Local tiles off`, then restored to done. Downloading does not apply to this hosted-fallback quick-install topology. | PASS | [assets/ADM_06-operational-tasks.txt](../assets/ADM_06-operational-tasks.txt); [assets/ADM_06-unavailable.jpg](../assets/ADM_06-unavailable.jpg); [assets/ADM_06-disabled.jpg](../assets/ADM_06-disabled.jpg); [assets/ADM_05-jobs-settled.jpg](../assets/ADM_05-jobs-settled.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_06-operational-tasks.txt](../assets/ADM_06-operational-tasks.txt) | Healthy APIs, unavailable/disabled transitions, and full restoration. |
| [assets/ADM_06-unavailable.jpg](../assets/ADM_06-unavailable.jpg) | Location and routing unavailable cards with useful reasons. |
| [assets/ADM_06-disabled.jpg](../assets/ADM_06-disabled.jpg) | Vector map tile disabled card in remote raster mode. |
| [assets/ADM_05-jobs-settled.jpg](../assets/ADM_05-jobs-settled.jpg) | Same-run healthy operational cards before controlled degradation. |

## Screenshot Evidence

- Three same-run states preserve healthy, unavailable, and disabled cards.

## Timings

| Step | Timing |
|---|---:|
| Degraded-state status load | About 3 s |
| Sidecar recovery | About 40 s |
| Each app mode recreate to HTTP 200 | About 33 s |

## Handoff Notes

- Completed: Ready, unavailable, disabled, and recovery paths passed.
- Remaining unfinished coverage: None for ADM_06.
- Blocked or not applicable: Live downloading does not apply to the hosted
  public fallback/no-local-maps topology.
- State left for the next packet: Normal local tile mode restored; required beta
  app, brouter, and healthy location-search services are running; Processing open.
