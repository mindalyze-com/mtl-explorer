---
released_on: 2026-10-15
teaser: >-
  Improved segment comparison and replay, route planning with saved places,
  and optional health data.
---

# MTL Explorer Release v.0.9.0

Changes since [v.0.8.1](https://github.com/mindalyze-com/mtl-explorer/releases/tag/v.0.8.1),
ordered by user impact.

## Important before updating

An in-place upgrade should work. For this release, we recommend a
**fresh PostgreSQL database** to rebuild derived data consistently.

**Back up before resetting: a reset deletes saved plans and manual edits.**
Preserve anything you need, then
[start with a fresh database](../administration/maintenance/updates.md#start-with-a-fresh-database).
Keep original GPS and media files and other data folders.
See [Updating](#updating) for the installation guide.

## Calories and energy estimates

- **Active and total calories:** Track Overview shows activity-specific estimates
  separately from external or vehicle work, with model assumptions and limits.
- **Body-weight adjustment:** Preview and save a weight for each track. It
  survives reimport and recalculation without changing your default.
- **Coverage explanations:** Partial estimates explain excluded time. Missing or
  unsupported data remains unavailable rather than zero.
- **Reliable recalculation:** Activity changes refresh estimates and charts;
  failures retry.
- **Calculation corrections:** Moving power uses consistent moving-time intervals.
  Cycling and water-sport estimates exclude unsupported work, including altitude
  changes for water sports.

See [calorie estimates](../user/analysis/energy.md).

## Segment comparison and replay

- **Discovery and selection:** Explore trends, individual visits, and the optional
  segment matrix. Select exact attempts, including repeated visits from one track;
  selections survive full-track inspection.
- **More reliable visits:** Movements around a zone's edge stay one visit. Results
  include only tracks that visit every placed zone.
- **More reliable timing:** Times are more precise and include stops; gaps
  and unclear crossings are marked. Incomplete attempts stay out of performance
  averages, and replay requires reliable timing and complete distance.
- **Aligned charts:** Compare speed, elevation, power, energy, slope, time, and
  available heart rate between the same checkpoints. Chart size and detail
  settings are remembered.
- **Reference attempt:** A diamond marked **R** identifies the reference.
  Finish comparisons show how much faster or slower each attempt was; the opening
  chart compares time at equal traveled distance, including stops.
- **Shared inspection and position map:** Move along a chart with the mouse,
  touch, or keyboard to see the matching spot on the map for every attempt.
- **Ghost positions:** See where other attempts were when the reference reached
  a distance, with ahead/behind values, route trails, and an optional lead chart.
- **Replay here:** Start replay at the inspected position with the same attempts,
  colors, and reference. Marker numbers match rankings; hover reveals progress.
- **Targeted retries:** If loading fails, retry only the part that failed.
  Refresh results after a track changes.

See [segment analysis](../user/analysis/segments.md).

## GPS reliability and review

- **Review:** Inspect import failures and GPS problems, compare original
  and processed paths, center the map on each finding, and see what was cleaned
  up during import. Highlighted points link to explanations.
- **Map and statistics choices:** Include or exclude whole sources or individual
  sections from the map, statistics, or both. The findings stay visible.
- **External correction:** Download the original or a GPX version, fix it in
  another tool, and put it back in the GPS folder to import and check it again.
- **Improved processing:** Smoothing handles variable sampling and missing
  measurements; altitude spikes no longer affect later points. Recording gaps
  stay disconnected on maps and in calculations.
- **Clearer motion and events:** Separate moving, stopped, and unknown time.
  Short pauses count toward stopped time; visible breaks start at one minute.
  Select breaks or GPS gaps to locate them on the activity map.
- **Import fixes:** Preserve GeoJSON elevation and separate lines, zero altitude
  in FIT files, and date-line crossings. Reimporting avoids transferring unrelated
  review choices.

See [Review](../user/tracks/source-review.md) and [motion analysis](../user/analysis/motion.md).

## Maps and points of interest

- **Map POIs:** Explore peaks, water, huts, food, transport, and more across
  21 categories. Tap for details and save visibility and opacity preferences.
- **Clearer data status:** See preparation, stale data, errors, and offline status.
  Map lookups tell you when processing isn't finished yet.
- **Rendering fixes:** OSM Dark works without an API key. Maps recover from tile
  failures and restore GPS markers; terrain handles missing ocean tiles, and
  Swiss Topo labels use the correct fonts.
- **Mini-map recovery:** Activity maps still appear when offline map data fails,
  and resize with their panels.

## Route planning

- **Planner hub:** Start drawing, open saved plans, or read the guide while
  preserving your current draft.
- **Mountain-bike routing:** Added alongside touring bike, road bike, hiking,
  and car profiles.
- **Personal POIs:** Save named places with icons and notes, alone or with a
  route. Move or remove them with shared undo/redo.
- **Waypoint editing:** Larger drag targets, tap-to-move, insertion, reverse,
  trimming, and undo/redo work on desktop and phone.
- **Saved plans and draft protection:** Search and reopen plans, save edited
  copies, and confirm before replacing unsaved work. Failed saves retain input.
- **Elevation and routing:** More consistent whole-route ascent and descent,
  resizable elevation charts, clearer failures, and improved BRouter recovery.
- **GPX export:** Exports contain the route; personal POIs remain in the saved plan.

## Google Health beta

**Automatically match heart rate to GPS tracks from independent devices.**
Record GPS on a Garmin and heart rate on a Fitbit, for example. MTL Explorer
matches recorded times after import, without device pairing or manual linking.

The optional, read-only connection also imports
exercise, sleep, steps, daily activity, body measurements, and health observations.
Available data depends on your account and devices.

Setup requires your own Google Cloud project with Google Health API access and
a Google Account linked to an active Google Health profile.
Google may limit new API projects; check the
[setup requirements](../administration/google-health.md) before you start.
The supported personal setup uses **Testing mode**, which
[requires reauthorization after seven days](https://support.google.com/cloud/answer/15549945?hl=en).
Imported history stays available. Setup and sync may still encounter errors or
interruptions.

- **Track and segment heart rate:** Aligned graphs and averages show available
  coverage. Optional cardiac-cost comparisons relate heart rate to estimated work.
- **Health overview:** Explore activity, vitals, sleep, and body charts by date or
  calendar period. **Latest values** shows the most recent measurements,
  independent of the chart period. Choose **Intraday** to see one day in detail:
  all charts zoom together, down to one-second heart rate and individual body
  measurements.
- **Records and routes:** Filter stored records and add available exercise routes
  to the GPS archive.
- **Resumable sync:** Recent data refreshes while older history imports in
  resumable batches. Settings shows progress, gaps, and retries for recent data,
  history, and routes.
- **Connection controls:** Renew expired access without repeating server setup.
  Disconnecting keeps imported history; deleting it requires separate confirmation.
- **Synthetic demo:** When enabled, explore generated health trends and matching
  track heart rate without a Google account. Use your own installation to connect
  real data.

See the [Health guide](../user/health/google-health.md).

## Filters and statistics

- **Filter refinements:** Preview results and category counts before Apply, with
  validation and date shortcuts. Shared criteria stay selected, and geographic
  drawing returns to the filter controls.
- **Map colors:** **No coloring** now stays selected when you switch views, and
  **Reset** returns each view to its default colors.
- **Statistics hub:** Preview the latest activity and browse Overview, Trends,
  and Tracks. Settings and search are preserved when returning.
- **Chart and browser controls:** Reorder and resize trend charts with saved
  preferences; clear track searches easily. Unreliable timing stays unavailable.
- **Consistent media filtering:** Map media and statistics use the matching
  tracks' time range, showing all media or only track-related items. Empty or
  undated selections show no media.

See [media filtering](../architecture/media/filtering.md).

## Photos and video

- **Image previews:** Better support for newer iPhone HEIC files, efficient
  resizing, and retained orientation, aspect ratio, and PNG transparency.
- **Recovery and downloads:** Thumbnails reload automatically when the server is
  busy, and a photo without a preview still opens. **Retry** reloads the photo
  and its filmstrip thumbnail. Download original files, including HEIC.
- **Video compatibility:** Offer compatible playback when audio works but video
  does not, with improved streaming and playback from the beginning.
- **Activity and demo media:** Clearer mini-map counts and retries. Demo photo
  generation resumes without repeating completed work.

## Settings and background processing

- **Release updates:** A notice and Settings badge link to release notes and
  manual update instructions for the app and self-managed components.
  Optional checks tolerate blocked network access; browser updates wait for Reload.
  See [release updates](../administration/maintenance/updates.md).
- **Navigation:** Admin becomes Settings with grouped sections. Mobile keeps
  common tools in the main toolbar and other tools under More.
- **Processing progress:** Clearer counts distinguish completed work from files
  needing review or reaching processing limits. Failed GPS files link to diagnoses;
  acknowledging notices keeps the findings.
- **Import recovery:** Pending GPS and media imports resume after restarts and
  retry temporary failures. Incomplete storage scans preserve imported records.
- **Memory and performance:** Lower memory use, capped import and media processing,
  less repeated exploration work, and improved service recovery.
- **Support details:** Preview and copy component versions without private
  settings or logs.
- **Stored diagnostics:** Filter browser and server events and inspect full error
  details. Browser reports survive reloads and retry temporary connection failures.
  See [logs](../administration/maintenance/logs.md).
- **Optional demo analytics:** Umami is available to public demo operators and
  remains off by default. See [web analytics](../development/website/analytics.md).

## Updating

Read the [database notice above](#important-before-updating) before upgrading.
Review the updated [installation guide](../administration/README.md) for custom
mounts and configuration, then follow [Updates](../administration/maintenance/updates.md).

- **Keep your original GPS files in place.** After the update, MTL Explorer
  re-imports eligible files once. Wait until **Settings → Processing** is done,
  then click **Reload** when the map asks.
- **Docker Compose:** use `docker compose` (with a space). The older
  `docker-compose` command is no longer supported.
- **Replace your Compose file, not only the images.** Memory limits are lower
  than in 0.8.1 and rely on new database, routing, and search settings in the
  same file. Start from the new file and copy over only your own folders, ports,
  and any limits you raised; set `memswap_limit` to the same value as a raised
  limit. Update the copy in your NAS container manager too.
  See [Updates](../administration/maintenance/updates.md) and
  [container memory limits](../development/profiling.md#container-memory-limits).
