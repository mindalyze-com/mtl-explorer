# Packet: ADM_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_01
- In scope: Open the admin dialog and verify the tab/action list is reachable and usable.
- Out of scope: Exercising each admin tool's behavior.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_04
- Required app/data state: Signed-in app with imported dataset.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Admin and switch to one admin section.
- Not allowed: Run rescans, uploads, installs, or destructive session actions.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_01 | Opened Admin and clicked `Open Upload`. | Admin dialog opens; tab list is reachable and usable. | Admin workspace showed grouped entries for Data, System, and Session. Clicking `Open Upload` opened the upload panel in-place. | PASS | `assets/ADM_01-admin-upload-tab.webp`, `assets/ADM_01-admin-tabs.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_01-admin-upload-tab.webp | Screenshot of the Admin workspace with Upload opened. |
| assets/ADM_01-admin-tabs.txt | Text evidence for reachable admin sections and opened Upload panel. |

## Timings

| Step | Timing |
|---|---:|
| Open Admin and switch to Upload | <1 min |

## Handoff Notes

- Completed: ADM_01 passed.
- Remaining unfinished coverage: ADM_02 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Admin dialog is open on Upload.
