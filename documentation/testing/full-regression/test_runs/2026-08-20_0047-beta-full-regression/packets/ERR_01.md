# Packet: ERR_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ERR_01
- In scope: Actionable recovery for failed track load, map config, media, Planner route, and expired session.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_07, MED_05, PLN_09, MED_42, NET_03.
- Required app/data state: Healthy restored disposable stack and signed-in browser.
- Required browser context: Main map and representative tool routes.

## Allowed Mutations

- Allowed: Open a nonexistent track and consolidate direct same-run fault injections.
- Not allowed: Change deployment/security configuration solely to isolate map config.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ERR_01 | Opened a nonexistent track and consolidated clean-start, media, BRouter, compatible-video, and expired-session fault evidence; audited map-config isolation. | Every failure is actionable rather than blank/frozen. | Track detail, media, Planner, video, and expired-session flows are actionable. This target's clean-start track failure exposes 0 Tracks without error/Retry (MTL-FR-003, fixed only locally); map-config-only injection is blocked. | FAIL | [assets/ERR_01-recovery-matrix.txt](../assets/ERR_01-recovery-matrix.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Finding status | Release impact |
|---|---|---|---|---|---|---|---|---|
| MTL-FR-003 | P2 | Startup dependency failure exposes an empty 0-track map without error or Retry. | Clean browser sign-in while the disposable database is unavailable. | Keep a clear loading/error curtain with Retry. | Deployed target exposes 0 Tracks with no action; the shared local worktree fix was directly verified. | [assets/SGN_07-startup-failure.txt](../assets/SGN_07-startup-failure.txt); [assets/MTL-FR-003-fix-local.txt](../assets/MTL-FR-003-fix-local.txt) | FIXED | Target image still needs a later build containing the verified fix. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ERR_01-recovery-matrix.txt](../assets/ERR_01-recovery-matrix.txt) | Five-path recovery matrix and map-config constraint. |
| [assets/MED_05-broken-recovery.txt](../assets/MED_05-broken-recovery.txt) | Real media 500 recovery. |
| [assets/PLN_09-brouter-unavailable.txt](../assets/PLN_09-brouter-unavailable.txt) | Real routing-service outage UI. |
| [assets/NET_03-auth-status.txt](../assets/NET_03-auth-status.txt) | Real expired-session redirect. |

## Screenshot Evidence

Accessible failure states are recorded as text; ACC_04 blocks screenshots.

## Timings

| Step | Timing |
|---|---:|
| Nonexistent-track error | About 1.4 s |
| Back to map | About 0.8 s |

## Handoff Notes

- Completed: Direct track failure and same-run recovery matrix.
- Remaining unfinished coverage: None for ERR_01.
- Blocked or not applicable: Map-config-only failure isolation.
- State left for the next packet: Healthy eight-track map; all disposable services running.
