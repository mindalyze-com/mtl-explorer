# Packet: ADM_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_10
- In scope: Installed Garmin exporter status and install/update feedback.
- Out of scope: Running a real Garmin account export.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_09.
- Required app/data state: Authenticated Admin Maintenance and configured helpers.
- Required browser context: Desktop Admin > Maintenance.

## Allowed Mutations

- Allowed: Run helper install/update actions with the current configured values.
- Not allowed: Trigger the remote Garmin Sync Run action.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_10 | Opened Advanced tools, recorded status, then ran both Install actions with their current configured values. | Installed-exporter status is shown; install/update actions report success or error. | Both helpers were Ready (2/2). Each action reported Done with explicit safe-skip/current-configuration output, updated its active configuration, and left the final status 2/2 ready. | PASS | [assets/ADM_10-garmin-tools.txt](../assets/ADM_10-garmin-tools.txt); [assets/ADM_10-garmin-tools.jpg](../assets/ADM_10-garmin-tools.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_10-garmin-tools.txt](../assets/ADM_10-garmin-tools.txt) | Initial status and exact result summaries for both helper actions. |
| [assets/ADM_10-garmin-tools.jpg](../assets/ADM_10-garmin-tools.jpg) | Maintenance Advanced tools after the fit-export action completed. |

## Screenshot Evidence

- The clipped Maintenance panel preserves 2/2 ready, both configured helper
  rows, Done status, and the fit-export command output.

## Timings

| Step | Timing |
|---|---:|
| Tool status load | Under 1 s |
| gcexport action | Under 1 s |
| fit-export action | Under 1 s |

## Handoff Notes

- Completed: Both Garmin helper status and install/update feedback checks passed.
- Remaining unfinished coverage: None for ADM_10.
- Blocked or not applicable: None.
- State left for the next packet: Admin Maintenance remains open with the
  completed fit-export output; both helpers are ready.
