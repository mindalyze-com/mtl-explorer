# Packet: ADM_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_10
- In scope: Garmin/helper export tool status and install/update action reporting.
- Out of scope: Running a Garmin account sync/export with external credentials.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_09
- Required app/data state: Admin workspace available.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Read tool status and run one exposed helper install action.
- Not allowed: Enter Garmin credentials or trigger external account sync.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_10 | Opened Helpers, checked tool status, then clicked the `gcexport` Install action. | Garmin export tools, if present, show installed exporter status; install/update actions report success or error. | Helpers showed `2/2 READY`; API reported both exporter environments present. The `gcexport` install action returned clear output saying the existing venv was already present and active version was updated in DB. | PASS | `assets/ADM_10-helpers.webp`, `assets/ADM_10-helper-install-output.webp`, `assets/ADM_10-garmin-tools.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_10-helpers.webp | Screenshot of Helpers tool status before action. |
| assets/ADM_10-helper-install-output.webp | Screenshot after helper install action output. |
| assets/ADM_10-garmin-tools.txt | Tool status endpoint and install output summary. |

## Timings

| Step | Timing |
|---|---:|
| Tool status and one install action | <2 min |

## Handoff Notes

- Completed: ADM_10 passed.
- Remaining unfinished coverage: ADM_11 through RUN_CLEANUP.
- Blocked or not applicable: External Garmin account sync was out of scope; tool status/install reporting was covered.
- State left for the next packet: Helpers panel open; freshness banner remains visible.
