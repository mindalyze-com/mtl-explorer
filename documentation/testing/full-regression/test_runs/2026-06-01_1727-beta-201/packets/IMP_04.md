# Packet: IMP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_04
- In scope: Confirm five-source import/index status, data freshness change, and background jobs settle.
- Out of scope: Reloading the visible app data cache and map/browser/statistics verification; covered by IMP_05+.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_03.
- Required app/data state: Five GPX files indexed by live watcher.
- Required browser context: Logged-in browser and authenticated API session for status reads.

## Allowed Mutations

- Allowed: Refresh/read admin/status views and authenticated status APIs.
- Not allowed: Add/delete files or trigger another import.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_04 | Checked data freshness, GPS indexer status, jobs status, server request logs, and imported track mapping after the five GPX imports. | All five source files reach completed state, no GPS index failures appear, freshness changes from baseline, and Duplicate Finder/Exploration Score settle. | Freshness changed from baseline `index:0`, `track_geometry:0`, `tracks:0` to token `index:15`, `track_geometry:30`, `tracks:30`; GPS indexer status is `total:5 completed:5 failed:0 progressPercent:100`; Duplicate Finder, Activity Classifier, and Exploration Score are `done:5/5`; each imported file maps to one `COMPLETED_WITH_SUCCESS` track id (`100000`-`100004`). | PASS | [assets/IMP_04-post-import-status-api.txt](../assets/IMP_04-post-import-status-api.txt), [assets/IMP_04-imported-track-mapping.txt](../assets/IMP_04-imported-track-mapping.txt), [assets/IMP_04-post-import-server-requests.txt](../assets/IMP_04-post-import-server-requests.txt), [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MTL-FR-001 | P2 | Admin subroute hard-load returns Spring Whitelabel 404. | In the installed app after opening Admin, reach `/mtl/admin` and reload/navigate that URL directly. | SPA route should reload or redirect back into the MTL Explorer app shell. | Server returned Whitelabel `404` for `/mtl/admin`. | [assets/IMP_04-admin-route-404-dom.txt](../assets/IMP_04-admin-route-404-dom.txt); [assets/IMP_04-post-import-server-requests.txt](../assets/IMP_04-post-import-server-requests.txt) | Admin deep links/reloads and browser recovery can dump users into a server error page. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_04-post-import-status-api.txt](../assets/IMP_04-post-import-status-api.txt) | Authenticated freshness, indexer, and jobs status after import. |
| [assets/IMP_04-imported-track-mapping.txt](../assets/IMP_04-imported-track-mapping.txt) | Imported track ids, source filenames, index statuses, names, point counts, and distances. |
| [assets/IMP_04-post-import-server-requests.txt](../assets/IMP_04-post-import-server-requests.txt) | Cropped server logs for settled jobs, loaded tracks, and the admin route 404. |
| [assets/IMP_04-admin-route-404-dom.txt](../assets/IMP_04-admin-route-404-dom.txt) | DOM evidence for the admin route Whitelabel 404. |
| [assets/DAT-public-data-manifest.json](../assets/DAT-public-data-manifest.json) | Manifest updated with imported GPX track ids and names. |

## Timings

| Step | Timing |
|---|---:|
| Duplicate Finder settled | 2026-06-01T15:39:56Z |
| Activity Classifier settled | 2026-06-01T15:40:17Z |
| Exploration Score settled | 2026-06-01T15:40:31Z |
| Freshness `tracks` revision changed | 2026-06-01T15:40:22.269Z |

## Handoff Notes

- Completed: IMP_04 terminal as `PASS`; one non-blocking P2 issue recorded.
- Remaining unfinished coverage: Continue with `IMP_05` reload/cache refresh and visible map/browser/stats update verification.
- Blocked or not applicable: None.
- State left for the next packet: Server has five imported GPX tracks and settled jobs; current browser contexts show startup splash/0 tracks after reload and need verification in `IMP_05`.
