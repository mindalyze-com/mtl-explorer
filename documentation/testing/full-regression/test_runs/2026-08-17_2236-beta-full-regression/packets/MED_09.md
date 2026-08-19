# Packet: MED_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_09
- In scope: GUI media rescan, terminal MEDIA status, removed/failure counts, freshness change, and freshness Reload.
- Out of scope: Map and database absence validation.

## Prerequisites

- Required previous coverage IDs or run packets: MED_08.
- Required app/data state: Exactly two designated sources absent from the watched tree.
- Required browser context: Signed-in desktop Admin workspace.

## Allowed Mutations

- Allowed: Activate Admin Rescan Media and the in-app freshness Reload.
- Not allowed: Call the rescan API fallback unless the GUI fails.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_09 | Activated Admin Maintenance `Rescan Media`, waited for MEDIA settlement, inspected counts/token, and activated the freshness banner Reload. | Removed increases by two, failure does not increase, media freshness changes, and Reload applies it. | The GUI action worked directly: MEDIA is terminal with 4 completed, 2 removed, 0 pending, and 0 failed. Media advanced r30 to r32, index r117 to r123, and Reload cleared the stale state. | PASS | [assets/MED_09-rescan.txt](../assets/MED_09-rescan.txt); [assets/MED_09-processing.jpg](../assets/MED_09-processing.jpg) |

## Issues

- None for this activation. MED_06 retains FR-001 for its separately observed initial no-op action.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_09-rescan.txt](../assets/MED_09-rescan.txt) | GUI action, exact terminal counts, freshness revisions/token, and Reload result. |
| [assets/MED_09-processing.jpg](../assets/MED_09-processing.jpg) | Durable Admin Processing screenshot showing MEDIA Done, 4 completed, and 2 removed after Reload. |

## Screenshot Evidence

- The saved desktop screenshot shows the terminal MEDIA card with 4 completed, 2 removed, 6 total, and the stale banner absent after Reload.

## Timings

| Step | Timing |
|---|---:|
| Admin rescan to observable terminal state | Under 2 s |
| Freshness Reload and settled Admin view | About 2 s |

## Handoff Notes

- Completed: Required GUI rescan, terminal removal accounting, and freshness action.
- Remaining unfinished coverage: None for MED_09.
- Blocked or not applicable: None.
- State left for the next packet: Browser has applied the four-photo media state; the two source backups remain quarantined.
