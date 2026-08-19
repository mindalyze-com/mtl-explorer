# Packet: PLN_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_08
- In scope: Download a saved plan as valid route-matching GPX.
- Out of scope: Other export formats.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_07.
- Required app/data state: Loaded five-point/710 m route.
- Required browser context: Planner Load and server request log.

## Allowed Mutations

- Allowed: Save/export/delete a dedicated plan.
- Not allowed: Preserve the export plan after validation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| PLN_08 | Saved a dedicated plan, activated its GPX export, validated the exact authenticated response, then deleted it. | Browser downloads a valid GPX matching the planned route. | UI triggered HTTP 200. Response was valid 573-byte GPX with correct name and five points matching the saved five-point/710 m route. Browser download storage is unavailable, so final file delivery cannot be inspected. | BLOCKED | [assets/PLN_08-gpx-export.txt](../assets/PLN_08-gpx-export.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_08-gpx-export.txt](../assets/PLN_08-gpx-export.txt) | UI action, server request, parsed GPX response, match, and cleanup. |

## Screenshot Evidence

Unavailable under ACC_04. GPX response validation is direct text evidence.

## Timings

| Step | Timing |
|---|---:|
| Save and export action | About 3 s |
| Authenticated response validation | About 2 s |
| Delete export plan | About 1 s |

## Handoff Notes

- Completed: UI export, HTTP response, GPX parse/match, and saved-record cleanup.
- Remaining unfinished coverage: None for PLN_08; terminal BLOCKED on browser download transport.
- Blocked or not applicable: Browser filesystem download confirmation only.
- State left for the next packet: Planner Load open; no saved routes; loaded route remains in memory.
