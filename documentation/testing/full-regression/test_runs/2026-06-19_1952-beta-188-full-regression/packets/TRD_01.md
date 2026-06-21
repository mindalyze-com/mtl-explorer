# Packet: TRD_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_01
- In scope: Open at least one GPX-backed track and one FIT-backed track from user-facing navigation and record IDs/source filenames.
- Out of scope: Full detail tab validation, covered by TRD_02 onward.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_04 and FIT_03.
- Required app/data state: GPX and FIT imports indexed successfully.
- Required browser context: authenticated desktop browser.

## Allowed Mutations

- Allowed: Reuse completed user-facing navigation evidence from this run.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_01 | Reused completed DEL_04 and FIT_03 actions: opened Mosel GPX track #100003 from track browser search and Activity.fit track #100005 from Stats > Tracks. | At least one GPX-backed and one FIT-backed track open from user-facing navigation, with IDs/source filenames recorded. | GPX #100003 / `MoselradwegAusWiki.gpx` opened Track Details; FIT #100005 / `Activity.fit` opened Track Details. | PASS | [assets/TRD_01-opened-gpx-fit.txt](../assets/TRD_01-opened-gpx-fit.txt); [assets/DEL_04-remaining-detail-open.webp](../assets/DEL_04-remaining-detail-open.webp); [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_01-opened-gpx-fit.txt](../assets/TRD_01-opened-gpx-fit.txt) | GPX/FIT track IDs, source filenames, and navigation paths. |
| [assets/DEL_04-remaining-detail-open.webp](../assets/DEL_04-remaining-detail-open.webp) | GPX-backed Mosel detail opened. |
| [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) | FIT-backed Activity detail overview. |

## Screenshot Evidence

![GPX-backed Mosel detail](../assets/DEL_04-remaining-detail-open.webp)

![FIT-backed Activity detail](../assets/FIT_03-overview.webp)

## Timings

| Step | Timing |
|---|---:|
| GPX/FIT detail open evidence | Covered in DEL_04 and FIT_03 |

## Handoff Notes

- Completed: TRD_01.
- Remaining unfinished coverage: TRD_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: App state remains restored to local/Auto map source with 14 API tracks / 13 visible map tracks.
