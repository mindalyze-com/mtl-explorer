# Packet: MED_17

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_17
- In scope: Save, reload/restart persistence, clear, and baseline restoration for a camera correction.
- Out of scope: Unsaved preview defect, covered by MED_16.

## Prerequisites

- Required previous coverage IDs or run packets: MED_16.
- Required app/data state: At least one saveable camera-clock preview item.
- Required browser context: Activity Photos Photo tools.

## Allowed Mutations

- Allowed: Save and clear correction on disposable fixtures.
- Not allowed: Direct database writes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_17 | Used the smallest step-valid preview (+0.25 h) on the prescribed five-minute activity fixture and checked Save eligibility. | At least one camera-clock item can be saved, reloaded/restarted, and cleared. | Preview returns 0 photos and disables Save correction; no correction can be created through the end-user flow. | BLOCKED | [assets/MED_16-camera-preview.txt](../assets/MED_16-camera-preview.txt) |

## Issues

- Upstream product defect FR-010 in MED_16 removes all preview items.
- Fixture/input boundary: the UI accepts 0.25-hour steps while the prescribed synthetic activity spans only five minutes.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_16-camera-preview.txt](../assets/MED_16-camera-preview.txt) | Valid offset boundary and disabled save state. |

## Screenshot Evidence

The accessible preview state directly reports zero photos and a disabled Save correction control.

## Timings

| Step | Timing |
|---|---:|
| Save eligibility check | Under 2 s |

## Handoff Notes

- Completed: All safe in-scope save preconditions were exercised.
- Remaining unfinished coverage: None for MED_17.
- Blocked or not applicable: Save/reload/restart/clear cannot start because no valid preview is saveable.
- State left for the next packet: Baseline restored; database has no correction rows.
