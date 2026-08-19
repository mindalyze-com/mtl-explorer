# Packet: ERR_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ERR_01
- In scope: Failed track load, map config, media, planner route, and expired-session recovery.
- Out of scope: Broad uncontrolled host/network corruption.

## Prerequisites

- Required previous coverage IDs or run packets: NET_04; direct MED_05, PLN_09, MAP_14, and NET_03 evidence.
- Required app/data state: Warm authenticated 15-track session and restored media/routing fixtures.
- Required browser context: Desktop browser with visible error/action inspection.

## Allowed Mutations

- Allowed: Briefly stop/start only the run's app service and retry a track; reuse direct earlier packet evidence for preserved fixtures.
- Not allowed: Corrupt production-like config, remove a non-disposable service, or leave a fixture missing.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ERR_01 | Simulated a failed track load and recovered with Retry; consolidated direct missing-media, unavailable-route, and invalid-session recoveries; enforced the prior safe-control audit for map config. | Every listed failure shows retry, re-login, dismiss, or other actionable guidance rather than freezing/blanking. | Track failure showed a specific connection message plus Retry/Close and recovered after restart. Missing media, planner route, and invalid session also passed their actionable recovery flows. Failed map config remains unexecutable because no isolated safe failure control exists in this topology/browser. | BLOCKED | [assets/ERR_01-recovery-matrix.txt](../assets/ERR_01-recovery-matrix.txt); [assets/ERR_01-track-load-failure.jpg](../assets/ERR_01-track-load-failure.jpg); [packets/MED_05.md](MED_05.md); [packets/PLN_09.md](PLN_09.md); [packets/MAP_14.md](MAP_14.md); [packets/NET_03.md](NET_03.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ERR_01-recovery-matrix.txt](../assets/ERR_01-recovery-matrix.txt) | Five required branches, exact messages/actions, recovery, constraint, and final state. |
| [assets/ERR_01-track-load-failure.jpg](../assets/ERR_01-track-load-failure.jpg) | Non-blank Track Details shell during the controlled failed load. |
| [packets/MED_05.md](MED_05.md) | Direct missing-media error and Retry recovery. |
| [packets/PLN_09.md](PLN_09.md) | Direct failed-route unavailable state and guidance. |
| [packets/MAP_14.md](MAP_14.md) | Direct safe-control/topology audit for isolated map failure. |
| [packets/NET_03.md](NET_03.md) | Direct 401, login redirect, and sign-in recovery. |

## Screenshot Evidence

![Controlled failed track load](../assets/ERR_01-track-load-failure.jpg)

## Timings

| Step | Timing |
|---|---:|
| Track failure state | About 3 seconds |
| App restart to running | About 27 seconds |
| Track Retry recovery | About 2.5 seconds |

## Handoff Notes

- Completed: Four actionable recovery branches, including a fresh failed-track Retry check.
- Remaining unfinished coverage: None for ERR_01; the unexecutable map-config branch is terminally recorded.
- Blocked or not applicable: Isolated failed-map-config simulation needs a disposable local map server or request interception, neither available.
- State left for the next packet: All services running, authenticated desktop root, 15 tracks, no missing source or active error panel.
