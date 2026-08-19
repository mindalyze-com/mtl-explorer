# Packet: MED_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_04
- In scope: Server-converted HEIC display.
- Out of scope: JPEG display.

## Prerequisites

- Required previous coverage IDs or run packets: MED_03.
- Required app/data state: Controlled DAT_08 media set.
- Required browser context: Any media viewer.

## Allowed Mutations

- Allowed: Audit controlled fixture formats.
- Not allowed: Expand the prescribed six-file set mid-run.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_04 | Audited the frozen media manifest for HEIC/HEIF inputs. | HEIC image is converted server-side and displays. | All six controlled files are JPEG; no HEIC/HEIF input exists, so conversion/display cannot be exercised without changing the frozen data contract. | BLOCKED | [assets/MED_04-heic-fixture-gap.txt](../assets/MED_04-heic-fixture-gap.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_04-heic-fixture-gap.txt](../assets/MED_04-heic-fixture-gap.txt) | Exact fixture-format audit and blocker. |

## Screenshot Evidence

Not applicable; the required input format is absent.

## Timings

| Step | Timing |
|---|---:|
| Manifest audit | Under 1 s |

## Handoff Notes

- Completed: Fixture audit; terminal BLOCKED.
- Remaining unfinished coverage: None for MED_04.
- Blocked or not applicable: No HEIC/HEIF fixture.
- State left for the next packet: Controlled data unchanged.

