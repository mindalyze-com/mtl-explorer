# Packet: MED_20

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_20
- In scope: Targeted media-correlation queue behavior for activity add, replace, and delete.
- Out of scope: Full-library performance scale.

## Prerequisites

- Required previous coverage IDs or run packets: MED_19.
- Required app/data state: Six correlated photos, original activity 100016, and disposable added activities.
- Required browser context: Activity Photos and Admin Maintenance.

## Allowed Mutations

- Allowed: Add, replace, restore, and recoverably delete fully synthetic disposable GPX sources.
- Not allowed: Direct database writes or permanent loss of the baseline media activity.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_20 | Replaced an indexed GPX at the same path with different same-length content, waited for the watcher, and verified targeted correlation on the fixed server. | A same-size changed GPX is reingested and its affected media are recalculated. | The watcher detected MODIFY, reingested the 1,080-byte replacement, created the 25-minute route, and correlated all six affected media without a full media scan. | FIXED | [details](../assets/MED_20-remediation.txt); [desktop](../assets/MED_20-fixed-desktop.webp); [mobile](../assets/MED_20-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-011 | P1 | Modified watched GPS source is detected but not re-ingested, even after Rescan GPS. | Replace a previously indexed GPX at the same path with changed geometry; wait; use Admin Maintenance Rescan GPS. | GPS track is re-ingested and only old/new-window media correlations recalculate to the new route. | MODIFY is logged, rescan completes with new/changed=0, and the old track/correlation geometry remains. | [assets/MED_20-targeted-activity-recalculation.txt](../assets/MED_20-targeted-activity-recalculation.txt) | Corrected/replaced activity files leave stale track and photo positions. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_20-targeted-activity-recalculation.txt](../assets/MED_20-targeted-activity-recalculation.txt) | Add/replace/delete queue logs, exact coordinates, and restored cleanup state. |
| [assets/MED_20-replacement-media-track.gpx](../assets/MED_20-replacement-media-track.gpx) | Fully synthetic replacement bytes used for the reproducible modify check. |

## Screenshot Evidence

The Admin GUI exposed queued Rescan GPS and later browser Reload returned 14 to 13 tracks; exact server logs and persistence values are stronger evidence for the replacement defect.

## Timings

| Step | Timing |
|---|---:|
| Add ingest/correlation | About 25 s |
| Replace observation plus GUI rescan | Over 110 s |
| Delete ingest/correlation | About 20 s |

## Handoff Notes

- Completed: Add/replace/delete paths exercised; FR-011 recorded; baseline source restored exactly.
- Remaining unfinished coverage: None for MED_20.
- Blocked or not applicable: None.
- State left for the next packet: 13-track map; only original activity 100016 correlates to the six photos; added sources quarantined.

## Remediation Verification

- Finding FR-011 is `FIXED`: default change detection now uses file size and modification time.
- Backend regression coverage verifies same-path, same-size replacement and unchanged-file skipping.
