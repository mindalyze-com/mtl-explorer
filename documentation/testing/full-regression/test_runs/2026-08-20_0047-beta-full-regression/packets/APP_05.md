# Packet: APP_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_05
- In scope: Hard refresh in dark mode does not flash light theme first.

## Prerequisites

- Required previous coverage IDs or run packets: APP_04 persisted dark theme and ACC_04.
- Required app/data state: Dark selected and persisted.
- Required browser context: Connected browser without first-paint capture.

## Allowed Mutations

- Allowed: Reuse APP_04 reload evidence and audit temporal-capture capability.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_05 | Hard-reloaded persisted dark Preferences and audited first-paint evidence capability. | No light-theme flash appears before dark. | Settled state was dark, but a transient first frame cannot be observed/captured with the available browser and ACC_04. | BLOCKED | [assets/APP_05-dark-refresh-flash.txt](../assets/APP_05-dark-refresh-flash.txt); [assets/APP_04-theme-persistence.txt](../assets/APP_04-theme-persistence.txt) |

## Issues

No product issue can be established without temporal paint evidence.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_05-dark-refresh-flash.txt](../assets/APP_05-dark-refresh-flash.txt) | Settled-state pass and exact first-paint blocker. |

## Screenshot Evidence

Required first-paint/filmstrip evidence is blocked by ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Settled reload | About 0.8 s |

## Handoff Notes

- Completed: Persisted dark post-load state.
- Remaining unfinished coverage: None; transient flash proof is terminally BLOCKED.
- Blocked or not applicable: Early-paint screenshot/trace capture.
- State left for the next packet: Dark Preferences remains open.
