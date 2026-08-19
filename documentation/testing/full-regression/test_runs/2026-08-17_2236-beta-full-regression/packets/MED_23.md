# Packet: MED_23

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_23
- In scope: Compact label for saved correction with non-zero seconds and honest unknown provenance label.
- Out of scope: Direct database/API seeding.

## Prerequisites

- Required previous coverage IDs or run packets: MED_22.
- Required app/data state: A saved offset such as 3603 seconds and optionally an item with unavailable provenance.
- Required browser context: Activity Photos timeline.

## Allowed Mutations

- Allowed: End-user preview/save flow on disposable items.
- Not allowed: Direct persistence seeding to manufacture the state under test.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_23 | Audited the live input/save boundary and prescribed provenance fixtures. | A saved 3603-second correction renders a valid compact label; unavailable provenance says Position unknown. | UI permits 900-second steps only, MED_17 cannot save, and all six items have known provenance; neither required state is reachable end to end. | BLOCKED | [assets/MED_23-offset-seconds-prerequisite.txt](../assets/MED_23-offset-seconds-prerequisite.txt) |

## Issues

- Upstream save blocker FR-010 from MED_16/MED_17.
- Required seconds-level and unknown-provenance fixtures are absent.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_23-offset-seconds-prerequisite.txt](../assets/MED_23-offset-seconds-prerequisite.txt) | Live input attributes and fixture audit. |

## Screenshot Evidence

Not required for the exact input attributes and absent states.

## Timings

| Step | Timing |
|---|---:|
| Prerequisite audit | Under 1 s |

## Handoff Notes

- Completed: All safe in-scope prerequisite checks.
- Remaining unfinished coverage: None for MED_23.
- Blocked or not applicable: Saved seconds-level correction and Position unknown fixture unavailable.
- State left for the next packet: Baseline six-photo data intact.
