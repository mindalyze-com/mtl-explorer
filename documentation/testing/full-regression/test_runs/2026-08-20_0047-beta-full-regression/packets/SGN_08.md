# Packet: SGN_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_08
- In scope: Verify `MTL Explorer` branding in About and public-facing copy.
- Out of scope: Precise About build identity, already recorded as MTL-FR-001 in RUN_SETUP.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_07.
- Required app/data state: Recovered healthy application and loaded map.
- Required browser context: Signed-in in-app browser.

## Allowed Mutations

- Allowed: Open the visible About control.
- Not allowed: Change application state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_08 | Open About MTL Explorer and inspect its public-facing labels and copy. | `MTL Explorer` branding appears in About/public copy. | The global control, panel title, region, heading, licensing paragraph, and source link all use MTL Explorer consistently. | PASS | [assets/SGN_08-branding.txt](../assets/SGN_08-branding.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

MTL-FR-001 remains linked to RUN_SETUP for the separate `Version dev` problem.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_08-branding.txt](../assets/SGN_08-branding.txt) | Accessible About/public-facing branding and source-link text. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible About structure and copy are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Open and inspect About | <1 min |

## Handoff Notes

- Completed: MTL Explorer branding verification.
- Remaining unfinished coverage: None for SGN_08.
- Blocked or not applicable: None.
- State left for the next packet: About panel open over the loaded map.
