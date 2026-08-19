# Packet: SGN_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_08
- In scope: Branding and identity on signed-out surfaces.
- Out of scope: Pixel-level image comparison, which is blocked by ACC_04.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_07.
- Required app/data state: Healthy disposable installation.
- Required browser context: Signed-out login page.

## Allowed Mutations

- Allowed: Open and close the About dialog.
- Not allowed: Change application configuration or data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_08 | Inspected the login identity, opened About, and verified its heading, description, version, license, source, and open-source notice. | Signed-out branding consistently uses the current MTL Explorer identity. | Login logo accessible name and About action use MTL Explorer. The About dialog identifies MTL Explorer, version `dev`, AGPL-3.0-or-later, and the expected source repository. | PASS | [assets/SGN_08-branding.txt](../assets/SGN_08-branding.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_08-branding.txt](../assets/SGN_08-branding.txt) | Semantic login and About identity evidence. |

## Screenshot Evidence

Not available because ACC_04 blocks screenshots; semantic DOM evidence was sufficient for this coverage.

## Timings

| Step | Timing |
|---|---:|
| Open and inspect About dialog | Under 2 s |

## Handoff Notes

- Completed: Signed-out branding and identity checks.
- Remaining unfinished coverage: None for SGN_08.
- Blocked or not applicable: Screenshot capture only; not required for the semantic identity assertion.
- State left for the next packet: About dialog open on the healthy login page.
